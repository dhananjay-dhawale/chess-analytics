package com.chessanalytics.controller;

import com.chessanalytics.dto.UploadJobResponse;
import com.chessanalytics.model.Account;
import com.chessanalytics.model.Platform;
import com.chessanalytics.model.UploadJob;
import com.chessanalytics.repository.AccountRepository;
import com.chessanalytics.service.ChessComApiService;
import com.chessanalytics.service.LichessApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for platform API integrations.
 * Provides endpoints to import games from Chess.com and Lichess.
 */
@RestController
@RequestMapping("/api/accounts/{accountId}/import")
@CrossOrigin(origins = "*")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    private final AccountRepository accountRepository;
    private final ChessComApiService chessComApiService;
    private final LichessApiService lichessApiService;

    public ImportController(
            AccountRepository accountRepository,
            ChessComApiService chessComApiService,
            LichessApiService lichessApiService) {
        this.accountRepository = accountRepository;
        this.chessComApiService = chessComApiService;
        this.lichessApiService = lichessApiService;
    }

    /**
     * Starts an import job to fetch games from Chess.com API.
     *
     * POST /api/accounts/{accountId}/import/chesscom
     *
     * Requirements:
     * - Account must exist
     * - Account must be Chess.com platform
     * - No other import currently running for this account
     *
     * @return 202 Accepted with job details for polling
     */
    @PostMapping("/chesscom")
    public ResponseEntity<?> startChessComImport(@PathVariable Long accountId) {
        log.info("Starting Chess.com import for account ID: {}", accountId);

        // Validate account exists
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null) {
            return ResponseEntity.notFound().build();
        }

        // Validate platform
        if (account.getPlatform() != Platform.CHESS_COM) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "WRONG_PLATFORM",
                "message", "This account is not a Chess.com account"
            ));
        }

        // Check for existing active import
        if (chessComApiService.hasActiveImport(accountId)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "IMPORT_IN_PROGRESS",
                "message", "An import is already in progress for this account"
            ));
        }

        // Start import
        UploadJob job = chessComApiService.startImport(account);
        log.info("Created import job {} for account {}", job.getId(), account.getUsername());

        return ResponseEntity.accepted().body(UploadJobResponse.from(job));
    }

    /**
     * Starts an import job to fetch games from Lichess API.
     *
     * POST /api/accounts/{accountId}/import/lichess
     *
     * Requirements:
     * - Account must exist
     * - Account must be Lichess platform
     * - No other import currently running for this account
     *
     * Rate Limiting:
     * - Lichess uses streaming API (single request)
     * - On 429: waits 60 seconds before retry (per Lichess guidelines)
     *
     * @return 202 Accepted with job details for polling
     */
    @PostMapping("/lichess")
    public ResponseEntity<?> startLichessImport(@PathVariable Long accountId) {
        log.info("Starting Lichess import for account ID: {}", accountId);

        // Validate account exists
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null) {
            return ResponseEntity.notFound().build();
        }

        // Validate platform
        if (account.getPlatform() != Platform.LICHESS) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "WRONG_PLATFORM",
                "message", "This account is not a Lichess account"
            ));
        }

        // Check for existing active import
        if (lichessApiService.hasActiveImport(accountId)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "IMPORT_IN_PROGRESS",
                "message", "An import is already in progress for this account"
            ));
        }

        // Start import
        UploadJob job = lichessApiService.startImport(account);
        log.info("Created import job {} for account {}", job.getId(), account.getUsername());

        return ResponseEntity.accepted().body(UploadJobResponse.from(job));
    }
}
