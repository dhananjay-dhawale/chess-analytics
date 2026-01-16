package com.chessanalytics.controller;

import com.chessanalytics.dto.UploadJobResponse;
import com.chessanalytics.model.Account;
import com.chessanalytics.service.AccountService;
import com.chessanalytics.service.IngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/accounts/{accountId}")
@CrossOrigin(origins = "*")
public class GameController {

    private final AccountService accountService;
    private final IngestionService ingestionService;

    public GameController(AccountService accountService, IngestionService ingestionService) {
        this.accountService = accountService;
        this.ingestionService = ingestionService;
    }

    /**
     * Upload a PGN file for an account.
     * Returns immediately with a job ID for progress polling.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadPgn(
            @PathVariable Long accountId,
            @RequestParam("file") MultipartFile file) {

        // Validate account exists
        Account account = accountService.getAccountEntity(accountId)
            .orElse(null);

        if (account == null) {
            return ResponseEntity.notFound().build();
        }

        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                .body("File is empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pgn")) {
            return ResponseEntity.badRequest()
                .body("File must be a .pgn file");
        }

        try {
            UploadJobResponse job = ingestionService.uploadPgn(account, file);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to save file: " + e.getMessage());
        }
    }

    /**
     * Get the status of an upload job.
     * Frontend polls this endpoint to track progress.
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<UploadJobResponse> getJobStatus(
            @PathVariable Long accountId,
            @PathVariable Long jobId) {

        return ingestionService.getJobStatus(jobId)
            .filter(job -> job.accountId().equals(accountId))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
