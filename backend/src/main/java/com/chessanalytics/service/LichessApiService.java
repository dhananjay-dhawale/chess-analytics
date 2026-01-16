package com.chessanalytics.service;

import com.chessanalytics.model.Account;
import com.chessanalytics.model.JobStatus;
import com.chessanalytics.model.Platform;
import com.chessanalytics.model.UploadJob;
import com.chessanalytics.parser.ParsedGame;
import com.chessanalytics.parser.PgnParser;
import com.chessanalytics.repository.UploadJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Fetches games from Lichess public API and imports them.
 *
 * Lichess API Rate Limit Compliance:
 * - Sequential requests only (one at a time)
 * - On 429: wait a full minute before resuming
 * - Uses streaming endpoint for efficient memory usage
 * - No authentication required for public game exports
 *
 * API Endpoint Used:
 * - GET /api/games/user/{username} - Streams all games in PGN format
 *
 * Reference: https://lichess.org/page/api-tips
 *            https://lichess.org/api#tag/Games/operation/apiGamesUser
 */
@Service
public class LichessApiService {

    private static final Logger log = LoggerFactory.getLogger(LichessApiService.class);

    // API Configuration
    private static final String LICHESS_API_BASE = "https://lichess.org/api/games/user/";
    private static final String USER_AGENT = "ChessAnalytics/1.0 (personal project; contact: github.com/chess-analytics)";
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10); // Long timeout for streaming

    // Rate Limiting Configuration
    // Lichess recommends waiting a full minute on 429
    private static final long BACKOFF_ON_429_MS = 60000;
    private static final int MAX_RETRIES = 3;

    private final HttpClient httpClient;
    private final PgnParser pgnParser;
    private final IngestionService ingestionService;
    private final UploadJobRepository uploadJobRepository;

    public LichessApiService(
            PgnParser pgnParser,
            IngestionService ingestionService,
            UploadJobRepository uploadJobRepository) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.pgnParser = pgnParser;
        this.ingestionService = ingestionService;
        this.uploadJobRepository = uploadJobRepository;
    }

    /**
     * Starts an async import job for a Lichess account.
     * Fetches all games via streaming API.
     *
     * @param account Must be a Lichess account
     * @return The created job for progress tracking
     * @throws IllegalArgumentException if account is not Lichess
     */
    @Transactional
    public UploadJob startImport(Account account) {
        if (account.getPlatform() != Platform.LICHESS) {
            throw new IllegalArgumentException("Account must be Lichess platform");
        }

        // Create job record
        UploadJob job = new UploadJob(account, "Lichess API Import");
        job.setStatus(JobStatus.PENDING);
        job = uploadJobRepository.save(job);

        // Start async processing
        importGamesAsync(job.getId(), account);

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
     * Async method that fetches games from Lichess API.
     * Uses streaming to process games one at a time for memory efficiency.
     */
    @Async
    public void importGamesAsync(Long jobId, Account account) {
        log.info("Starting Lichess API import for job {} (account: {})", jobId, account.getUsername());

        try {
            // Update job to processing
            updateJobStatus(jobId, JobStatus.PROCESSING);

            // Build URL with parameters for PGN format
            // The endpoint streams PGN games separated by blank lines
            String url = LICHESS_API_BASE + account.getUsername().toLowerCase()
                + "?moves=true"
                + "&tags=true"
                + "&clocks=false"  // Skip clock data for faster processing
                + "&evals=false"   // Skip evaluations for faster processing
                + "&opening=true"; // Include opening names

            log.info("Job {}: Fetching games from {}", jobId, url);

            int gamesProcessed = fetchAndProcessGames(url, account, jobId);

            // Mark completed
            markJobCompleted(jobId);
            log.info("Job {} completed: {} games processed", jobId, gamesProcessed);

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            markJobFailed(jobId, e.getMessage());
        }
    }

    /**
     * Fetches games from Lichess and processes them as a stream.
     * Lichess streams PGN games separated by empty lines.
     */
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
                    // Rate limited - wait a full minute as recommended by Lichess
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

    /**
     * Processes streaming PGN response from Lichess.
     * Games are separated by empty lines in the PGN format.
     */
    private int processStreamingResponse(java.io.InputStream inputStream, Account account, Long jobId)
            throws Exception {
        int gamesProcessed = 0;
        StringBuilder currentPgn = new StringBuilder();
        boolean inMoves = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    // Empty line could mean end of headers or end of game
                    if (inMoves && currentPgn.length() > 0) {
                        // End of a complete game - process it
                        String pgnString = currentPgn.toString();
                        try {
                            ParsedGame parsed = pgnParser.parseGameFromPgn(pgnString, account.getUsername());
                            if (parsed != null && parsed.isValid()) {
                                ingestionService.saveGame(account, parsed, jobId);
                                gamesProcessed++;

                                // Update total count periodically
                                if (gamesProcessed % 100 == 0) {
                                    updateJobTotal(jobId, gamesProcessed);
                                    log.info("Job {}: Processed {} games so far", jobId, gamesProcessed);
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Failed to parse game: {}", e.getMessage());
                        }
                        currentPgn.setLength(0);
                        inMoves = false;
                    }
                    continue;
                }

                currentPgn.append(line).append("\n");

                if (line.startsWith("[")) {
                    // Header line - if we were in moves, this is a new game
                    if (inMoves) {
                        // Process previous game first
                        String pgnString = currentPgn.toString();
                        // Remove the last line we just added (it's part of the new game)
                        int lastNewline = pgnString.lastIndexOf('\n', pgnString.length() - 2);
                        if (lastNewline > 0) {
                            String prevGame = pgnString.substring(0, lastNewline);
                            try {
                                ParsedGame parsed = pgnParser.parseGameFromPgn(prevGame, account.getUsername());
                                if (parsed != null && parsed.isValid()) {
                                    ingestionService.saveGame(account, parsed, jobId);
                                    gamesProcessed++;

                                    if (gamesProcessed % 100 == 0) {
                                        updateJobTotal(jobId, gamesProcessed);
                                        log.info("Job {}: Processed {} games so far", jobId, gamesProcessed);
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("Failed to parse game: {}", e.getMessage());
                            }
                        }
                        // Keep only the new header
                        currentPgn.setLength(0);
                        currentPgn.append(line).append("\n");
                        inMoves = false;
                    }
                } else {
                    // Move line
                    inMoves = true;
                }
            }

            // Process the last game if any content remains
            if (currentPgn.length() > 0) {
                String pgnString = currentPgn.toString();
                try {
                    ParsedGame parsed = pgnParser.parseGameFromPgn(pgnString, account.getUsername());
                    if (parsed != null && parsed.isValid()) {
                        ingestionService.saveGame(account, parsed, jobId);
                        gamesProcessed++;
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse last game: {}", e.getMessage());
                }
            }
        }

        // Final update of total
        updateJobTotal(jobId, gamesProcessed);
        return gamesProcessed;
    }

    // Job update helper methods

    @Transactional
    void updateJobStatus(Long jobId, JobStatus status) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            uploadJobRepository.save(job);
        });
    }

    @Transactional
    void updateJobTotal(Long jobId, int totalGames) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.setTotalGames(totalGames);
            uploadJobRepository.save(job);
        });
    }

    @Transactional
    void markJobCompleted(Long jobId) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.markCompleted();
            uploadJobRepository.save(job);
        });
    }

    @Transactional
    void markJobFailed(Long jobId, String errorMessage) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.markFailed(errorMessage);
            uploadJobRepository.save(job);
        });
    }
}
