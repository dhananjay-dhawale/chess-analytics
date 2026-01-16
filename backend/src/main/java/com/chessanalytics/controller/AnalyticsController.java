package com.chessanalytics.controller;

import com.chessanalytics.dto.CalendarDataPoint;
import com.chessanalytics.dto.CalendarResponse;
import com.chessanalytics.dto.StatsResponse;
import com.chessanalytics.model.Color;
import com.chessanalytics.model.TimeControlCategory;
import com.chessanalytics.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Returns games per day for calendar heatmap visualization (legacy endpoint).
     *
     * Query params:
     * - from: start date (required)
     * - to: end date (required)
     * - accountId: filter by account (optional)
     * - timeControl: filter by category (optional)
     * - color: filter by player color (optional)
     */
    @GetMapping("/calendar")
    public ResponseEntity<Map<String, Object>> getCalendarData(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) TimeControlCategory timeControl,
            @RequestParam(required = false) Color color) {

        List<CalendarDataPoint> data = analyticsService.getCalendarData(
            from, to, accountId, timeControl, color
        );

        return ResponseEntity.ok(Map.of("data", data));
    }

    /**
     * Returns multi-year calendar data with rich tooltip information.
     * Automatically includes all years with game data.
     *
     * Query params:
     * - accountIds: comma-separated list of account IDs (optional, null = all accounts)
     * - timeControl: filter by category (optional)
     * - color: filter by player color (optional)
     */
    @GetMapping("/calendar/multi-year")
    public ResponseEntity<CalendarResponse> getMultiYearCalendarData(
            @RequestParam(required = false) List<Long> accountIds,
            @RequestParam(required = false) TimeControlCategory timeControl,
            @RequestParam(required = false) Color color) {

        CalendarResponse response = analyticsService.getMultiYearCalendarData(
            accountIds, timeControl, color
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Returns aggregated win/loss/draw statistics (legacy endpoint).
     *
     * Query params:
     * - accountId: filter by account (optional)
     * - timeControl: filter by category (optional)
     * - color: filter by player color (optional)
     */
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) TimeControlCategory timeControl,
            @RequestParam(required = false) Color color) {

        StatsResponse stats = analyticsService.getStats(accountId, timeControl, color);
        return ResponseEntity.ok(stats);
    }

    /**
     * Returns aggregated statistics with per-account breakdown.
     *
     * Query params:
     * - accountIds: comma-separated list of account IDs (optional, null = all accounts)
     * - timeControl: filter by category (optional)
     * - color: filter by player color (optional)
     */
    @GetMapping("/stats/multi-account")
    public ResponseEntity<StatsResponse> getStatsMultiAccount(
            @RequestParam(required = false) List<Long> accountIds,
            @RequestParam(required = false) TimeControlCategory timeControl,
            @RequestParam(required = false) Color color) {

        StatsResponse stats = analyticsService.getStatsMultiAccount(
            accountIds, timeControl, color
        );

        return ResponseEntity.ok(stats);
    }
}
