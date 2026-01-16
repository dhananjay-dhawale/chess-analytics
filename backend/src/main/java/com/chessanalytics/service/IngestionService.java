package com.chessanalytics.service;

import com.chessanalytics.dto.UploadJobResponse;
import com.chessanalytics.model.*;
import com.chessanalytics.parser.ParsedGame;
import com.chessanalytics.parser.PgnParser;
import com.chessanalytics.repository.GameRepository;
import com.chessanalytics.repository.UploadJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles PGN file uploads and asynchronous game parsing.
 * Provides job tracking for frontend progress polling.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final int PROGRESS_UPDATE_INTERVAL = 50; // Update DB every N games

    private final PgnParser pgnParser;
    private final GameRepository gameRepository;
    private final UploadJobRepository uploadJobRepository;
    private final Path uploadDir;

    // Self-injection for @Async and @Transactional to work through Spring proxy
    @Autowired
    @Lazy
    private IngestionService self;

    public IngestionService(
            PgnParser pgnParser,
            GameRepository gameRepository,
            UploadJobRepository uploadJobRepository,
            @Value("${app.upload.dir}") String uploadDirPath) {
        this.pgnParser = pgnParser;
        this.gameRepository = gameRepository;
        this.uploadJobRepository = uploadJobRepository;
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath();

        // Ensure upload directory exists
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            log.error("Failed to create upload directory: {}", uploadDirPath, e);
        }
    }

    /**
     * Initiates a PGN file upload and starts async processing.
     *
     * @return The created job for progress tracking
     */
    @Transactional
    public UploadJobResponse uploadPgn(Account account, MultipartFile file) throws IOException {
        // Save file with unique name
        String originalFilename = file.getOriginalFilename();
        String storedFilename = UUID.randomUUID() + "_" +
            (originalFilename != null ? originalFilename : "upload.pgn");
        Path filePath = uploadDir.resolve(storedFilename);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Saved PGN file: {} ({} bytes)", storedFilename, file.getSize());

        // Create job record
        UploadJob job = new UploadJob(account, originalFilename);
        job.setStatus(JobStatus.PENDING);
        job = uploadJobRepository.save(job);

        // Start async processing (use self-injection so @Async works through Spring proxy)
        self.processFileAsync(job.getId(), account, filePath);

        return UploadJobResponse.from(job);
    }

    /**
     * Async method that processes the PGN file in the background.
     * Updates job status as it progresses.
     */
    @Async
    public void processFileAsync(Long jobId, Account account, Path filePath) {
        log.info("Starting async processing for job {} (account: {})", jobId, account.getUsername());

        // Track progress in memory, batch updates to DB
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        AtomicInteger pendingUpdates = new AtomicInteger(0);

        try {
            // Count total games first for progress tracking
            int totalGames = pgnParser.countGames(filePath);
            self.updateJobTotal(jobId, totalGames);
            log.info("Job {}: Found {} games to process", jobId, totalGames);

            // Parse and save games
            pgnParser.parse(filePath, account.getUsername(), parsedGame -> {
                try {
                    boolean isDuplicate = self.saveGameOnly(account, parsedGame);

                    processedCount.incrementAndGet();
                    if (isDuplicate) {
                        duplicateCount.incrementAndGet();
                    }

                    // Batch progress updates to reduce DB contention
                    if (pendingUpdates.incrementAndGet() >= PROGRESS_UPDATE_INTERVAL) {
                        int processed = processedCount.get();
                        int duplicates = duplicateCount.get();
                        self.updateJobProgress(jobId, processed, duplicates);
                        pendingUpdates.set(0);
                    }
                } catch (Exception e) {
                    log.error("Error saving game in job {}: {}", jobId, e.getMessage());
                    processedCount.incrementAndGet();
                    pendingUpdates.incrementAndGet();
                }
            });

            // Final progress update and mark completed
            self.updateJobProgress(jobId, processedCount.get(), duplicateCount.get());
            self.markJobCompleted(jobId);
            log.info("Job {} completed successfully: {} processed, {} duplicates",
                jobId, processedCount.get(), duplicateCount.get());

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            self.markJobFailed(jobId, e.getMessage());
        }
    }

    /**
     * Saves a parsed game without updating job progress.
     * Returns true if the game was a duplicate.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveGameOnly(Account account, ParsedGame parsed) {
        // Check for duplicate first (before creating entity)
        if (gameRepository.existsByAccountIdAndPgnHash(account.getId(), parsed.getPgnHash())) {
            return true; // duplicate
        }

        Game game = new Game();
        game.setAccount(account);
        game.setPlayedAt(parsed.getPlayedAt());
        game.setResult(parsed.getResult());
        game.setColor(parsed.getColor());
        game.setTimeControlRaw(parsed.getTimeControlRaw());
        game.setTimeControlCategory(parsed.getTimeControlCategory());
        game.setEcoCode(parsed.getEcoCode());
        game.setOpeningName(parsed.getOpeningName());
        game.setOpponent(parsed.getOpponent());
        game.setPgnHash(parsed.getPgnHash());

        gameRepository.save(game);
        return false; // not a duplicate
    }

    /**
     * Returns the current status of an upload job.
     */
    @Transactional(readOnly = true)
    public Optional<UploadJobResponse> getJobStatus(Long jobId) {
        return uploadJobRepository.findById(jobId)
            .map(UploadJobResponse::from);
    }

    // Job update methods

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobTotal(Long jobId, int totalGames) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.setTotalGames(totalGames);
            job.setStatus(JobStatus.PROCESSING);
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
    public void markJobCompleted(Long jobId) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.markCompleted();
            uploadJobRepository.save(job);
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
