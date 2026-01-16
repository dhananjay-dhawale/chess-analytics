package com.chessanalytics.dto;

import java.util.List;

/**
 * Calendar data grouped by year for stacked heatmap display.
 * Each year contains all days with games and summary statistics.
 */
public record CalendarYearResponse(
    int year,
    int totalGames,
    int activeDays,
    int wins,
    int losses,
    int draws,
    List<CalendarDayResponse> days
) {}
