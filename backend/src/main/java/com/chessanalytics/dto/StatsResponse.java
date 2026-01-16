package com.chessanalytics.dto;

import com.chessanalytics.model.Platform;

import java.util.List;
import java.util.Map;

/**
 * Aggregated statistics response for analytics.
 */
public record StatsResponse(
    long total,
    long wins,
    long losses,
    long draws,
    Map<String, ColorStats> byColor,
    List<AccountStats> byAccount
) {
    /**
     * Constructor for backwards compatibility (without account breakdown).
     */
    public StatsResponse(long total, long wins, long losses, long draws, Map<String, ColorStats> byColor) {
        this(total, wins, losses, draws, byColor, List.of());
    }

    public record ColorStats(
        long wins,
        long losses,
        long draws
    ) {}

    public record AccountStats(
        Long accountId,
        String username,
        Platform platform,
        String label,
        long total,
        long wins,
        long losses,
        long draws
    ) {}
}
