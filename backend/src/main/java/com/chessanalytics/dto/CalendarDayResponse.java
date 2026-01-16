package com.chessanalytics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Single day's data for calendar heatmap with rich tooltip information.
 * Includes total counts and per-account breakdown.
 */
public record CalendarDayResponse(
    LocalDate date,
    int count,
    int wins,
    int losses,
    int draws,
    List<CalendarDayAccountBreakdown> byAccount
) {}
