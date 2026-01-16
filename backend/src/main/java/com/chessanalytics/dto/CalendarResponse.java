package com.chessanalytics.dto;

import java.util.List;

/**
 * Complete calendar response with all years and summary.
 * Years are ordered descending (most recent first).
 */
public record CalendarResponse(
    List<CalendarYearResponse> years,
    CalendarSummary summary
) {
    /**
     * Overall summary across all years and accounts.
     */
    public record CalendarSummary(
        int totalGames,
        int totalWins,
        int totalLosses,
        int totalDraws,
        int activeDays,
        String yearRange,
        int accountCount
    ) {}
}
