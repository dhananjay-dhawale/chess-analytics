package com.chessanalytics.dto;

/**
 * Error response specific to account operations with detailed error information.
 */
public record AccountErrorResponse(
    String error,
    String message,
    String platform,
    Long existingAccountId
) {
    public static AccountErrorResponse invalidUsername(String message, String platform) {
        return new AccountErrorResponse("INVALID_USERNAME", message, platform, null);
    }

    public static AccountErrorResponse duplicateAccount(String message, String platform, Long existingAccountId) {
        return new AccountErrorResponse("DUPLICATE_ACCOUNT", message, platform, existingAccountId);
    }

    public static AccountErrorResponse externalApiError(String message) {
        return new AccountErrorResponse("EXTERNAL_API_ERROR", message, null, null);
    }
}
