package com.chessanalytics.dto;

import com.chessanalytics.model.Account;
import com.chessanalytics.model.Platform;
import java.time.LocalDateTime;

public record AccountResponse(
    Long id,
    Platform platform,
    String platformDisplayName,
    String username,
    String label,
    LocalDateTime createdAt,
    Long gameCount
) {
    public static AccountResponse from(Account account, Long gameCount) {
        return new AccountResponse(
            account.getId(),
            account.getPlatform(),
            account.getPlatform().getDisplayName(),
            account.getUsername(),
            account.getLabel(),
            account.getCreatedAt(),
            gameCount
        );
    }
}
