package com.chessanalytics.service;

import com.chessanalytics.model.Account;
import com.chessanalytics.model.JobStatus;
import com.chessanalytics.model.Platform;
import com.chessanalytics.model.UploadJob;
import com.chessanalytics.parser.ParsedGame;
import com.chessanalytics.parser.PgnParser;
import com.chessanalytics.repository.AccountRepository;
import com.chessanalytics.repository.UploadJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fetches games from Lichess public API and imports them.
 */
@Service
public class LichessApiService {

    private static final Logger log = LoggerFactory.getLogger(LichessApiService.class);

    private static final String LICHESS_API_BASE = "https://lichess.org/api/games/user/";
    private static final String USER_AGENT = "ChessAnalytics/1.0 (personal project)";
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);
    private static final long BACKOFF_ON_429_MS = 60000;
    private static final int MAX_RETRIES = 3;
    private static final int PROGRESS_UPDATE_INTERVAL = 100;

    private final HttpClient httpClient;
    private final PgnParser pgnParser;
    private final IngestionService ingestionService;
    private final UploadJobRepository uploadJobRepository;
    private final AccountRepository accountRepository;

    @Autowired
    @Lazy
    private LichessApiService self;

    public LichessApiService(
            PgnParser pgnParser,
            IngestionService ingestionService,
            UploadJobRepository uploadJobRepository,
            AccountRepository accountRepository) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.pgnParser = pgnParser;
        this.ingestionService = ingestionService;
        this.uploadJobRepository = uploadJobRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public UploadJob startImport(Account account) {
        if (account.getPlatform() != Platform.LICHESS) {
            throw new IllegalArgumentException("Account must be Lichess platform");
        }

        UploadJob job = new UploadJob(account, "Lichess API Import");
        job.setStatus(JobStatus.PENDING);
        job = uploadJobRepository.save(job);

        self.importGamesAsync(job.getId(), account);

        return job;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveImport(Long accountId) {
        return uploadJobRepository.existsByAccountIdAndStatusIn(
            accountId,
            List.of(JobStatus.PENDING, JobStatus.PROCESSING)
        );
    }

    @Async
    public void importGamesAsync(Long jobId, Account account) {
        log.info("Starting Lichess API import for job {} (account: {})", jobId, account.getUsername());
        LocalDateTime syncStartTime = LocalDateTime.now();

        try {
            self.updateJobStatus(jobId, JobStatus.PROCESSING);

            StringBuilder urlBuilder = new StringBuilder(LICHESS_API_BASE)
                .append(account.getUsername().toLowerCase())
                .append("?moves=true")
                .append("&tags=true")
                .append("&clocks=false")
                .append("&evals=false")
                .append("&opening=true");

            LocalDateTime lastSyncAt = account.getLastSyncAt();
            if (lastSyncAt != null) {
                long sinceTimestamp = lastSyncAt.toInstant(ZoneOffset.UTC).toEpochMilli();
                urlBuilder.append("&since=").append(sinceTimestamp);
                log.info("Job {}: Incremental sync since {}", jobId, lastSyncAt);
            }

            String url = urlBuilder.toString();
            log.info("Job {}: Fetching games from {}", jobId, url);

            int gamesProcessed = fetchAndProcessGames(url, account, jobId);

            self.markJobCompleted(jobId, account.getId(), syncStartTime);
            log.info("Job {} completed: {} games processed", jobId, gamesProcessed);

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            self.markJobFailed(jobId, e.getMessage());
        }
    }

    private int fetchAndProcessGames(String url, Account account, Long jobId) throws Exception {
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/x-chess-pgn")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

                HttpResponse<java.io.InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
                );

                int statusCode = response.statusCode();

                if (statusCode == 200) {
                    return processStreamingResponse(response.body(), account, jobId);
                } else if (statusCode == 429) {
                    attempt++;
                    if (attempt >= MAX_RETRIES) {
                        throw new RuntimeException("Rate limited by Lichess after " + MAX_RETRIES + " retries");
                    }
                    log.warn("Rate limited (429). Waiting {}ms before retry {}/{}",
                        BACKOFF_ON_429_MS, attempt, MAX_RETRIES);
                    Thread.sleep(BACKOFF_ON_429_MS);
                } else if (statusCode == 404) {
                    throw new RuntimeException("User not found on Lichess: " + account.getUsername());
                } else {
                    throw new RuntimeException("Lichess API error: HTTP " + statusCode);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request interrupted", e);
            }
        }

        throw new RuntimeException("Failed to fetch after " + MAX_RETRIES + " attempts");
    }

    private int processStreamingResponse(java.io.InputStream inputStream, Account account, Long jobId)
            throws Exception {
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        AtomicInteger pendingUpdates = new AtomicInteger(0);

        StringBuilder currentPgn = new StringBuilder();
        boolean inMoves = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (inMoves && currentPgn.length() > 0) {
                        processGame(currentPgn.toString(), account, jobId, processedCount, duplicateCount, pendingUpdates);
                        currentPgn.setLength(0);
                        inMoves = false;
                    }
                    continue;
                }

                currentPgn.append(line).append("\n");

                if (line.startsWith("[")) {
                    if (inMoves) {
                        String pgnString = currentPgn.toString();
                        int lastNewline = pgnString.lastIndexOf('\n', pgnString.length() - 2);
                        if (lastNewline > 0) {
                            processGame(pgnString.substring(0, lastNewline), account, jobId, processedCount, duplicateCount, pendingUpdates);
                        }
                        currentPgn.setLength(0);
                        currentPgn.append(line).append("\n");
                        inMoves = false;
                    }
                } else {
                    inMoves = true;
                }
            }

            if (currentPgn.length() > 0) {
                processGame(currentPgn.toString(), account, jobId, processedCount, duplicateCount, pendingUpdates);
            }
        }

        // Final progress update
        self.updateJobProgress(jobId, processedCount.get(), duplicateCount.get());
        return processedCount.get();
    }

    private void processGame(String pgnString, Account account, Long jobId,
                            AtomicInteger processedCount, AtomicInteger duplicateCount,
                            AtomicInteger pendingUpdates) {
        try {
            ParsedGame parsed = pgnParser.parseGameFromPgn(pgnString, account.getUsername());
            if (parsed != null && parsed.isValid()) {
                boolean isDuplicate = ingestionService.saveGameOnly(account, parsed);
                processedCount.incrementAndGet();
                if (isDuplicate) {
                    duplicateCount.incrementAndGet();
                }

                if (pendingUpdates.incrementAndGet() >= PROGRESS_UPDATE_INTERVAL) {
                    self.updateJobProgress(jobId, processedCount.get(), duplicateCount.get());
                    pendingUpdates.set(0);
                    log.info("Job {}: Processed {} games so far", jobId, processedCount.get());
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse game: {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobStatus(Long jobId, JobStatus status) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            uploadJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobProgress(Long jobId, int processed, int duplicates) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.setTotalGames(processed); // For streaming, total = processed
            job.setProcessedGames(processed);
            job.setDuplicateGames(duplicates);
            uploadJobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markJobCompleted(Long jobId, Long accountId, LocalDateTime syncTime) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.markCompleted();
            uploadJobRepository.save(job);
        });
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
