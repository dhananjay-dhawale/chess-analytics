package com.chessanalytics.service;

import com.chessanalytics.dto.UploadJobResponse;
import com.chessanalytics.model.*;
import com.chessanalytics.parser.ParsedGame;
import com.chessanalytics.parser.PgnParser;
import com.chessanalytics.repository.GameRepository;
import com.chessanalytics.repository.UploadJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles PGN file uploads and asynchronous game parsing.
 * Provides job tracking for frontend progress polling.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final PgnParser pgnParser;
    private final GameRepository gameRepository;
    private final UploadJobRepository uploadJobRepository;
    private final Path uploadDir;

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

        // Start async processing
        processFileAsync(job.getId(), account, filePath);

        return UploadJobResponse.from(job);
    }

    /**
     * Async method that processes the PGN file in the background.
     * Updates job status as it progresses.
     */
    @Async
    public void processFileAsync(Long jobId, Account account, Path filePath) {
        log.info("Starting async processing for job {} (account: {})", jobId, account.getUsername());

        try {
            // Count total games first for progress tracking
            int totalGames = pgnParser.countGames(filePath);
            updateJobTotal(jobId, totalGames);
            log.info("Job {}: Found {} games to process", jobId, totalGames);

            // Parse and save games
            pgnParser.parse(filePath, account.getUsername(), parsedGame -> {
                try {
                    saveGame(account, parsedGame, jobId);
                } catch (Exception e) {
                    log.error("Error saving game in job {}: {}", jobId, e.getMessage());
                }
            });

            // Mark completed
            markJobCompleted(jobId);
            log.info("Job {} completed successfully", jobId);

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            markJobFailed(jobId, e.getMessage());
        }
    }

    /**
     * Saves a parsed game and updates job progress.
     */
    @Transactional
    public void saveGame(Account account, ParsedGame parsed, Long jobId) {
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

        // Check for duplicate
        if (gameRepository.existsByAccountIdAndPgnHash(account.getId(), parsed.getPgnHash())) {
            incrementDuplicate(jobId);
        } else {
            gameRepository.save(game);
            incrementProcessed(jobId);
        }
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

    @Transactional
    public void updateJobTotal(Long jobId, int totalGames) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.setTotalGames(totalGames);
            job.setStatus(JobStatus.PROCESSING);
            uploadJobRepository.save(job);
        });
    }

    @Transactional
    public void incrementProcessed(Long jobId) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.incrementProcessed();
            uploadJobRepository.save(job);
        });
    }

    @Transactional
    public void incrementDuplicate(Long jobId) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.incrementProcessed(); // Still counts toward progress
            job.incrementDuplicate();
            uploadJobRepository.save(job);
        });
    }

    @Transactional
    public void markJobCompleted(Long jobId) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.markCompleted();
            uploadJobRepository.save(job);
        });
    }

    @Transactional
    public void markJobFailed(Long jobId, String errorMessage) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.markFailed(errorMessage);
            uploadJobRepository.save(job);
        });
    }
}
