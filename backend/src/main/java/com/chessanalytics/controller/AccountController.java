package com.chessanalytics.controller;

import com.chessanalytics.dto.AccountErrorResponse;
import com.chessanalytics.dto.AccountRequest;
import com.chessanalytics.dto.AccountResponse;
import com.chessanalytics.dto.UpdateAccountRequest;
import com.chessanalytics.service.AccountService;
import com.chessanalytics.service.AccountService.AccountValidationException;
import com.chessanalytics.service.AccountService.DuplicateAccountException;
import com.chessanalytics.service.AccountService.ExternalApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<?> createAccount(@Valid @RequestBody AccountRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long id) {
        return accountService.getAccount(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAccountRequest request) {
        return accountService.updateAccount(id, request)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        if (accountService.deleteAccount(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // Exception handlers for account-specific errors

    @ExceptionHandler(AccountValidationException.class)
    public ResponseEntity<AccountErrorResponse> handleValidationException(AccountValidationException e) {
        return ResponseEntity.badRequest().body(
            AccountErrorResponse.invalidUsername(e.getMessage(), e.getPlatform().name())
        );
    }

    @ExceptionHandler(DuplicateAccountException.class)
    public ResponseEntity<AccountErrorResponse> handleDuplicateException(DuplicateAccountException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            AccountErrorResponse.duplicateAccount(
                e.getMessage(),
                e.getPlatform().name(),
                e.getExistingAccountId()
            )
        );
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<AccountErrorResponse> handleExternalApiException(ExternalApiException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            AccountErrorResponse.externalApiError(e.getMessage())
        );
    }
}
