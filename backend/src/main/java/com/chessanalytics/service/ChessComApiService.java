package com.chessanalytics.service;

import com.chessanalytics.model.Account;
import com.chessanalytics.model.JobStatus;
import com.chessanalytics.model.Platform;
import com.chessanalytics.model.UploadJob;
import com.chessanalytics.parser.ParsedGame;
import com.chessanalytics.parser.PgnParser;
import com.chessanalytics.repository.UploadJobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chessanalytics.repository.AccountRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.transaction.annotation.Propagation;

/**
 * Fetches games from Chess.com public API and imports them.
 *
 * Chess.com API Rate Limit Compliance:
 * - Sequential requests only (no parallelism)
 * - 500ms delay between requests (conservative baseline)
 * - Exponential backoff on 429: 2s → 4s → 8s (max 3 retries)
 * - Proper User-Agent header as recommended by Chess.com
 *
 * API Endpoints Used:
 * - GET /pub/player/{username}/games/archives - List of monthly archive URLs
 * - GET /pub/player/{username}/games/{YYYY}/{MM} - Games for a specific month
 *
 * Reference: https://www.chess.com/news/view/published-data-api
 */
@Service
public class ChessComApiService {

    private static final Logger log = LoggerFactory.getLogger(ChessComApiService.class);

    // API Configuration
    private static final String CHESS_COM_API_BASE = "https://api.chess.com/pub/player/";
    private static final String USER_AGENT = "ChessAnalytics/1.0 (personal project; contact: github.com/chess-analytics)";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    // Rate Limiting Configuration
    // Conservative delay between requests to respect Chess.com API
    private static final long REQUEST_DELAY_MS = 500;
    // Exponential backoff on 429: starts at 2s, doubles each retry
    private static final long INITIAL_BACKOFF_MS = 2000;
    private static final long MAX_BACKOFF_MS = 60000;
    private static final int MAX_RETRIES = 3;
    private static final int PROGRESS_UPDATE_INTERVAL = 100;

    // Pattern to extract year/month from archive URL
    private static final Pattern ARCHIVE_URL_PATTERN = Pattern.compile(".*/games/(\\d{4})/(\\d{2})$");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PgnParser pgnParser;
    private final IngestionService ingestionService;
    private final UploadJobRepository uploadJobRepository;
    private final AccountRepository accountRepository;

    // Self-injection for @Async to work (Spring proxy requirement)
    @Autowired
    @Lazy
    private ChessComApiService self;

    public ChessComApiService(
            PgnParser pgnParser,
            IngestionService ingestionService,
            UploadJobRepository uploadJobRepository,
            AccountRepository accountRepository) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
        this.objectMapper = new ObjectMapper();
        this.pgnParser = pgnParser;
        this.ingestionService = ingestionService;
        this.uploadJobRepository = uploadJobRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Starts an async import job for a Chess.com account.
     * Fetches all available game archives and imports games.
     *
     * @param account Must be a Chess.com account
     * @return The created job for progress tracking
     * @throws IllegalArgumentException if account is not Chess.com
     */
    @Transactional
    public UploadJob startImport(Account account) {
        if (account.getPlatform() != Platform.CHESS_COM) {
            throw new IllegalArgumentException("Account must be Chess.com platform");
        }

        // Create job record
        UploadJob job = new UploadJob(account, "Chess.com API Import");
        job.setStatus(JobStatus.PENDING);
        job = uploadJobRepository.save(job);

        // Start async processing (use self-injection so @Async works through Spring proxy)
        self.importGamesAsync(job.getId(), account);

        return job;
    }

    /**
     * Checks if an import is already running for this account.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveImport(Long accountId) {
        return uploadJobRepository.existsByAccountIdAndStatusIn(
            accountId,
            List.of(JobStatus.PENDING, JobStatus.PROCESSING)
        );
    }

    /**
     * Async method that fetches games from Chess.com API.
     * Processes archives sequentially with rate limiting.
     * Supports incremental sync by filtering archives based on lastSyncAt.
     */
    @Async
    public void importGamesAsync(Long jobId, Account account) {
        log.info("Starting Chess.com API import for job {} (account: {})", jobId, account.getUsername());
        LocalDateTime syncStartTime = LocalDateTime.now();

        try {
            // Step 1: Fetch archive list
            List<String> allArchives = fetchArchiveList(account.getUsername());
            if (allArchives.isEmpty()) {
                log.info("No game archives found for user: {}", account.getUsername());
                self.markJobCompleted(jobId, account.getId(), syncStartTime);
                return;
            }

            // Step 2: Filter archives based on lastSyncAt for incremental sync
            LocalDateTime lastSyncAt = account.getLastSyncAt();
            List<String> archives = filterArchivesByDate(allArchives, lastSyncAt);

            if (archives.isEmpty()) {
                log.info("No new archives to process since last sync: {}", lastSyncAt);
                self.markJobCompleted(jobId, account.getId(), syncStartTime);
                return;
            }

            log.info("Job {}: Found {} archives to process (filtered from {} total, lastSync: {})",
                jobId, archives.size(), allArchives.size(), lastSyncAt);

            // Step 3: Initialize job with archive count for discovery phase
            self.updateJobStatusWithArchives(jobId, JobStatus.PROCESSING, archives.size());

            // Step 4: Process each archive sequentially
            int totalGamesFound = 0;
            int archivesProcessed = 0;
            AtomicInteger processedCount = new AtomicInteger(0);
            AtomicInteger duplicateCount = new AtomicInteger(0);
            AtomicInteger pendingUpdates = new AtomicInteger(0);

            for (String archiveUrl : archives) {
                try {
                    // Rate limit: wait between requests
                    if (archivesProcessed > 0) {
                        Thread.sleep(REQUEST_DELAY_MS);
                    }

                    List<String> gamePgns = fetchMonthlyGames(archiveUrl);
                    log.debug("Job {}: Archive {} returned {} games", jobId, archiveUrl, gamePgns.size());

                    // Update total count as we discover games
                    totalGamesFound += gamePgns.size();
                    self.updateJobTotal(jobId, totalGamesFound);

                    // Process each game
                    for (String pgn : gamePgns) {
                        ParsedGame parsed = pgnParser.parseGameFromPgn(pgn, account.getUsername());
                        if (parsed != null && parsed.isValid()) {
                            boolean isDuplicate = ingestionService.saveGameOnly(account, parsed);
                            processedCount.incrementAndGet();
                            if (isDuplicate) {
                                duplicateCount.incrementAndGet();
                            }

                            // Batch progress updates to reduce DB contention
                            if (pendingUpdates.incrementAndGet() >= PROGRESS_UPDATE_INTERVAL) {
                                self.updateJobProgress(jobId, processedCount.get(), duplicateCount.get());
                                pendingUpdates.set(0);
                            }
                        }
                    }

                    archivesProcessed++;
                    self.updateArchiveProgress(jobId, archivesProcessed);
                    log.info("Job {}: Processed archive {}/{} ({} games so far)",
                        jobId, archivesProcessed, archives.size(), totalGamesFound);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Import interrupted", e);
                } catch (Exception e) {
                    // Log error but continue with next archive
                    log.warn("Job {}: Failed to process archive {}: {}",
                        jobId, archiveUrl, e.getMessage());
                    archivesProcessed++;
                    self.updateArchiveProgress(jobId, archivesProcessed);
                }
            }

            // Final progress update
            self.updateJobProgress(jobId, processedCount.get(), duplicateCount.get());

            // Mark completed and update lastSyncAt
            self.markJobCompleted(jobId, account.getId(), syncStartTime);
            log.info("Job {} completed: {} archives processed, {} total games",
                jobId, archivesProcessed, totalGamesFound);

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            self.markJobFailed(jobId, e.getMessage());
        }
    }

    /**
     * Filters archives to only include those from months >= lastSyncAt.
     * Archive URLs are in format: .../games/YYYY/MM
     */
    private List<String> filterArchivesByDate(List<String> archives, LocalDateTime lastSyncAt) {
        if (lastSyncAt == null) {
            return archives; // No previous sync, process all
        }

        YearMonth lastSyncMonth = YearMonth.from(lastSyncAt);
        List<String> filtered = new ArrayList<>();

        for (String archiveUrl : archives) {
            Matcher matcher = ARCHIVE_URL_PATTERN.matcher(archiveUrl);
            if (matcher.find()) {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                YearMonth archiveMonth = YearMonth.of(year, month);

                // Include if archive month is >= last sync month
                if (!archiveMonth.isBefore(lastSyncMonth)) {
                    filtered.add(archiveUrl);
                }
            } else {
                // Can't parse, include to be safe
                filtered.add(archiveUrl);
            }
        }

        return filtered;
    }

    /**
     * Fetches the list of monthly archive URLs for a user.
     * Example: ["https://api.chess.com/pub/player/username/games/2024/01", ...]
     */
    List<String> fetchArchiveList(String username) throws Exception {
        String url = CHESS_COM_API_BASE + username.toLowerCase() + "/games/archives";
        log.debug("Fetching archive list: {}", url);

        String response = fetchWithRetry(url);
        JsonNode root = objectMapper.readTree(response);
        JsonNode archivesNode = root.get("archives");

        List<String> archives = new ArrayList<>();
        if (archivesNode != null && archivesNode.isArray()) {
            for (JsonNode archive : archivesNode) {
                archives.add(archive.asText());
            }
        }

        return archives;
    }

    /**
     * Fetches games for a specific monthly archive.
     * Returns list of PGN strings.
     */
    List<String> fetchMonthlyGames(String archiveUrl) throws Exception {
        log.debug("Fetching monthly games: {}", archiveUrl);

        String response = fetchWithRetry(archiveUrl);
        JsonNode root = objectMapper.readTree(response);
        JsonNode gamesNode = root.get("games");

        List<String> pgns = new ArrayList<>();
        if (gamesNode != null && gamesNode.isArray()) {
            for (JsonNode game : gamesNode) {
                JsonNode pgnNode = game.get("pgn");
                if (pgnNode != null && !pgnNode.isNull()) {
                    pgns.add(pgnNode.asText());
                }
            }
        }

        return pgns;
    }

    /**
     * Fetches a URL with retry and exponential backoff on 429.
     * Implements Chess.com API rate limit compliance.
     */
    private String fetchWithRetry(String url) throws Exception {
        int attempt = 0;
        long backoffMs = INITIAL_BACKOFF_MS;

        while (attempt < MAX_RETRIES) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                switch (response.statusCode()) {
                    case 200:
                        return response.body();

                    case 429:
                        // Rate limited - apply exponential backoff
                        attempt++;
                        if (attempt >= MAX_RETRIES) {
                            throw new RuntimeException("Rate limited by Chess.com after " + MAX_RETRIES + " retries");
                        }
                        log.warn("Rate limited (429). Waiting {}ms before retry {}/{}",
                            backoffMs, attempt, MAX_RETRIES);
                        Thread.sleep(backoffMs);
                        backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
                        break;

                    case 404:
                        throw new RuntimeException("Resource not found: " + url);

                    default:
                        throw new RuntimeException("Chess.com API error: HTTP " + response.statusCode());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request interrupted", e);
            }
        }

        throw new RuntimeException("Failed to fetch after " + MAX_RETRIES + " attempts");
    }

    // Job update helper methods

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobStatusWithArchives(Long jobId, JobStatus status, int totalArchives) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            job.setTotalArchives(totalArchives);
            job.setArchivesProcessed(0);
            uploadJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobTotal(Long jobId, int totalGames) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.setTotalGames(totalGames);
            uploadJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobProgress(Long jobId, int processed, int duplicates) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.setProcessedGames(processed);
            job.setDuplicateGames(duplicates);
            uploadJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateArchiveProgress(Long jobId, int archivesProcessed) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.setArchivesProcessed(archivesProcessed);
            uploadJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobCompleted(Long jobId, Long accountId, LocalDateTime syncTime) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.markCompleted();
            uploadJobRepository.save(job);
        });
        // Update account's lastSyncAt
        accountRepository.findById(accountId).ifPresent(account -> {
            account.setLastSyncAt(syncTime);
            accountRepository.save(account);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobFailed(Long jobId, String errorMessage) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.markFailed(errorMessage);
            uploadJobRepository.save(job);
        });
    }
}
