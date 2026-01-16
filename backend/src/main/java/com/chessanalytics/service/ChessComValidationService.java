package com.chessanalytics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Validates Chess.com usernames using their public API.
 *
 * Chess.com API Rate Limit Compliance:
 * - Uses official public API endpoint: /pub/player/{username}
 * - Single request per validation (no batching)
 * - Includes User-Agent header as recommended
 * - Handles rate limiting with appropriate error responses
 *
 * Reference: https://www.chess.com/news/view/published-data-api
 */
@Service
public class ChessComValidationService {

    private static final Logger log = LoggerFactory.getLogger(ChessComValidationService.class);
    private static final String CHESS_COM_API_BASE = "https://api.chess.com/pub/player/";
    private static final String USER_AGENT = "ChessAnalytics/1.0 (personal project)";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;

    public ChessComValidationService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    }

    /**
     * Validates that a username exists on Chess.com.
     *
     * @param username the Chess.com username to validate
     * @return ValidationResult indicating success, not found, or error
     */
    public ValidationResult validateUsername(String username) {
        if (username == null || username.isBlank()) {
            return ValidationResult.invalid("Username cannot be empty");
        }

        String url = CHESS_COM_API_BASE + username.toLowerCase().trim();
        log.debug("Validating Chess.com username: {}", username);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(TIMEOUT)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return switch (response.statusCode()) {
                case 200 -> {
                    log.debug("Username '{}' validated successfully", username);
                    yield ValidationResult.valid();
                }
                case 404 -> {
                    log.debug("Username '{}' not found on Chess.com", username);
                    yield ValidationResult.notFound(
                        "Username '" + username + "' not found on Chess.com"
                    );
                }
                case 429 -> {
                    log.warn("Rate limited by Chess.com API");
                    yield ValidationResult.rateLimited(
                        "Chess.com is temporarily unavailable. Please try again in a moment."
                    );
                }
                default -> {
                    log.warn("Unexpected response from Chess.com API: {}", response.statusCode());
                    yield ValidationResult.error(
                        "Unable to verify username. Please try again."
                    );
                }
            };
        } catch (Exception e) {
            log.error("Error validating Chess.com username: {}", e.getMessage());
            return ValidationResult.error(
                "Unable to connect to Chess.com. Please try again."
            );
        }
    }

    /**
     * Result of username validation.
     */
    public record ValidationResult(
        boolean isValid,
        String errorType,
        String errorMessage
    ) {
        public static ValidationResult valid() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult notFound(String message) {
            return new ValidationResult(false, "NOT_FOUND", message);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, "INVALID", message);
        }

        public static ValidationResult rateLimited(String message) {
            return new ValidationResult(false, "RATE_LIMITED", message);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, "ERROR", message);
        }

        public boolean isNotFound() {
            return "NOT_FOUND".equals(errorType);
        }

        public boolean isRateLimited() {
            return "RATE_LIMITED".equals(errorType);
        }
    }
}
