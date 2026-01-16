package com.chessanalytics.dto;

import java.time.LocalDate;

/**
 * Single data point for calendar heatmap visualization.
 */
public record CalendarDataPoint(
    LocalDate date,
    int count
) {}
