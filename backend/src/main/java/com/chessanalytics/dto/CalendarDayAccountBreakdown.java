package com.chessanalytics.dto;

import com.chessanalytics.model.Platform;

/**
 * Per-account breakdown for a single day's games.
 * Used in calendar tooltip to show contribution from each account.
 */
public record CalendarDayAccountBreakdown(
    Long accountId,
    String username,
    Platform platform,
    String label,
    int count,
    int wins,
    int losses,
    int draws
) {}
