package com.chessanalytics.service;

import com.chessanalytics.dto.AccountRequest;
import com.chessanalytics.dto.AccountResponse;
import com.chessanalytics.dto.UpdateAccountRequest;
import com.chessanalytics.model.Account;
import com.chessanalytics.model.Platform;
import com.chessanalytics.repository.AccountRepository;
import com.chessanalytics.repository.GameRepository;
import com.chessanalytics.repository.UploadJobRepository;
import com.chessanalytics.service.ChessComValidationService.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final GameRepository gameRepository;
    private final UploadJobRepository uploadJobRepository;
    private final ChessComValidationService chessComValidationService;

    public AccountService(
            AccountRepository accountRepository,
            GameRepository gameRepository,
            UploadJobRepository uploadJobRepository,
            ChessComValidationService chessComValidationService) {
        this.accountRepository = accountRepository;
        this.gameRepository = gameRepository;
        this.uploadJobRepository = uploadJobRepository;
        this.chessComValidationService = chessComValidationService;
    }

    /**
     * Creates a new account with username validation for supported platforms.
     *
     * @throws AccountValidationException if username validation fails
     * @throws DuplicateAccountException if account already exists
     */
    @Transactional
    public AccountResponse createAccount(AccountRequest request) {
        // Check for duplicate first
        Optional<Account> existing = accountRepository.findByPlatformAndUsername(
            request.platform(), request.username()
        );
        if (existing.isPresent()) {
            throw new DuplicateAccountException(
                "Account '" + request.username() + "' on " + request.platform().getDisplayName() + " already exists",
                request.platform(),
                existing.get().getId()
            );
        }

        // Validate username for Chess.com
        if (request.platform() == Platform.CHESS_COM) {
            ValidationResult validation = chessComValidationService.validateUsername(request.username());
            if (!validation.isValid()) {
                if (validation.isRateLimited()) {
                    throw new ExternalApiException(validation.errorMessage());
                }
                throw new AccountValidationException(
                    validation.errorMessage(),
                    request.platform()
                );
            }
        }

        Account account = new Account(request.platform(), request.username(), request.label());
        account = accountRepository.save(account);

        log.info("Created account: {} on {} (label: {})",
            account.getUsername(), account.getPlatform(), account.getLabel());
        return AccountResponse.from(account, 0L);
    }

    /**
     * Updates an account's label.
     */
    @Transactional
    public Optional<AccountResponse> updateAccount(Long id, UpdateAccountRequest request) {
        return accountRepository.findById(id)
            .map(account -> {
                account.setLabel(request.label());
                account = accountRepository.save(account);
                log.info("Updated account {} label to: {}", id, request.label());
                return AccountResponse.from(account, gameRepository.countByAccountId(id));
            });
    }

    // Custom exceptions for account operations

    public static class AccountValidationException extends RuntimeException {
        private final Platform platform;

        public AccountValidationException(String message, Platform platform) {
            super(message);
            this.platform = platform;
        }

        public Platform getPlatform() {
            return platform;
        }
    }

    public static class DuplicateAccountException extends RuntimeException {
        private final Platform platform;
        private final Long existingAccountId;

        public DuplicateAccountException(String message, Platform platform, Long existingAccountId) {
            super(message);
            this.platform = platform;
            this.existingAccountId = existingAccountId;
        }

        public Platform getPlatform() {
            return platform;
        }

        public Long getExistingAccountId() {
            return existingAccountId;
        }
    }

    public static class ExternalApiException extends RuntimeException {
        public ExternalApiException(String message) {
            super(message);
        }
    }

    /**
     * Returns all accounts with their game counts.
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
            .map(account -> AccountResponse.from(
                account,
                gameRepository.countByAccountId(account.getId())
            ))
            .toList();
    }

    /**
     * Returns a specific account by ID.
     */
    @Transactional(readOnly = true)
    public Optional<AccountResponse> getAccount(Long id) {
        return accountRepository.findById(id)
            .map(account -> AccountResponse.from(
                account,
                gameRepository.countByAccountId(account.getId())
            ));
    }

    /**
     * Returns the raw Account entity (for internal use).
     */
    @Transactional(readOnly = true)
    public Optional<Account> getAccountEntity(Long id) {
        return accountRepository.findById(id);
    }

    /**
     * Deletes an account and all associated data (upload jobs and games).
     */
    @Transactional
    public boolean deleteAccount(Long id) {
        if (!accountRepository.existsById(id)) {
            return false;
        }
        // Delete all upload jobs first to avoid foreign key constraint violation
        uploadJobRepository.deleteByAccountId(id);
        log.info("Deleted upload jobs for account ID: {}", id);

        // Delete all games
        long gameCount = gameRepository.countByAccountId(id);
        gameRepository.deleteByAccountId(id);
        log.info("Deleted {} games for account ID: {}", gameCount, id);

        accountRepository.deleteById(id);
        log.info("Deleted account ID: {}", id);
        return true;
    }
}
