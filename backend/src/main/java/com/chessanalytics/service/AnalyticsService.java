package com.chessanalytics.service;

import com.chessanalytics.dto.*;
import com.chessanalytics.model.Color;
import com.chessanalytics.model.GameResult;
import com.chessanalytics.model.Platform;
import com.chessanalytics.model.TimeControlCategory;
import com.chessanalytics.repository.AccountRepository;
import com.chessanalytics.repository.GameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides analytics data for the frontend.
 * Calendar heatmaps, win/loss stats, etc.
 */
@Service
public class AnalyticsService {

    private final GameRepository gameRepository;
    private final AccountRepository accountRepository;

    public AnalyticsService(GameRepository gameRepository, AccountRepository accountRepository) {
        this.gameRepository = gameRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Returns game counts per day for calendar heatmap visualization.
     */
    @Transactional(readOnly = true)
    public List<CalendarDataPoint> getCalendarData(
            LocalDate from,
            LocalDate to,
            Long accountId,
            TimeControlCategory timeControlCategory,
            Color color) {

        LocalDateTime startDate = from.atStartOfDay();
        LocalDateTime endDate = to.plusDays(1).atStartOfDay();

        List<Object[]> results = gameRepository.countGamesByDay(
            startDate, endDate, accountId, timeControlCategory, color
        );

        return results.stream()
            .map(row -> {
                // Handle different date formats from different databases
                Object dateObj = row[0];
                LocalDate date;
                if (dateObj instanceof LocalDate ld) {
                    date = ld;
                } else if (dateObj instanceof java.sql.Date sqlDate) {
                    date = sqlDate.toLocalDate();
                } else {
                    date = LocalDate.parse(dateObj.toString());
                }
                int count = ((Number) row[1]).intValue();
                return new CalendarDataPoint(date, count);
            })
            .toList();
    }

    /**
     * Returns aggregated win/loss/draw statistics.
     */
    @Transactional(readOnly = true)
    public StatsResponse getStats(
            Long accountId,
            TimeControlCategory timeControlCategory,
            Color color) {

        // Get overall counts by result
        List<Object[]> resultCounts = gameRepository.countByResult(
            accountId, timeControlCategory, color
        );

        long wins = 0, losses = 0, draws = 0;
        for (Object[] row : resultCounts) {
            GameResult result = (GameResult) row[0];
            long count = ((Number) row[1]).longValue();
            switch (result) {
                case WIN -> wins = count;
                case LOSS -> losses = count;
                case DRAW -> draws = count;
            }
        }

        // Get counts by color and result
        Map<String, StatsResponse.ColorStats> byColor = new HashMap<>();

        if (color == null) {
            // Only fetch color breakdown if not filtering by color
            List<Object[]> colorCounts = gameRepository.countByColorAndResult(
                accountId, timeControlCategory
            );

            Map<Color, long[]> colorMap = new HashMap<>();
            colorMap.put(Color.WHITE, new long[3]); // [wins, losses, draws]
            colorMap.put(Color.BLACK, new long[3]);

            for (Object[] row : colorCounts) {
                Color c = (Color) row[0];
                GameResult r = (GameResult) row[1];
                long count = ((Number) row[2]).longValue();

                int idx = switch (r) {
                    case WIN -> 0;
                    case LOSS -> 1;
                    case DRAW -> 2;
                };
                colorMap.get(c)[idx] = count;
            }

            for (Color c : Color.values()) {
                long[] counts = colorMap.get(c);
                byColor.put(c.name(), new StatsResponse.ColorStats(
                    counts[0], counts[1], counts[2]
                ));
            }
        }

        return new StatsResponse(
            wins + losses + draws,
            wins,
            losses,
            draws,
            byColor
        );
    }

    /**
     * Returns multi-year calendar data with rich tooltip information.
     * Groups data by year, with per-day and per-account breakdowns.
     *
     * @param accountIds list of account IDs to include (null = all accounts)
     * @param timeControlCategory optional time control filter
     * @param color optional color filter
     * @return CalendarResponse with years sorted descending (most recent first)
     */
    @Transactional(readOnly = true)
    public CalendarResponse getMultiYearCalendarData(
            List<Long> accountIds,
            TimeControlCategory timeControlCategory,
            Color color) {

        // Get raw data grouped by date and account
        List<Object[]> rawData = gameRepository.getDailyStatsByAccount(
            accountIds, timeControlCategory, color
        );

        if (rawData.isEmpty()) {
            return new CalendarResponse(
                List.of(),
                new CalendarResponse.CalendarSummary(0, 0, 0, 0, 0, "", 0)
            );
        }

        // Parse raw data into structured objects
        // Group by date first, then aggregate
        Map<LocalDate, List<DayAccountData>> dataByDate = new LinkedHashMap<>();
        Set<Long> uniqueAccountIds = new HashSet<>();

        for (Object[] row : rawData) {
            LocalDate date = parseDate(row[0]);
            Long accountId = ((Number) row[1]).longValue();
            String username = (String) row[2];
            Platform platform = (Platform) row[3];
            String label = (String) row[4];
            int count = ((Number) row[5]).intValue();
            int wins = ((Number) row[6]).intValue();
            int losses = ((Number) row[7]).intValue();
            int draws = ((Number) row[8]).intValue();

            uniqueAccountIds.add(accountId);

            DayAccountData dayData = new DayAccountData(
                date, accountId, username, platform, label, count, wins, losses, draws
            );

            dataByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(dayData);
        }

        // Group by year
        Map<Integer, List<CalendarDayResponse>> yearDays = new TreeMap<>(Comparator.reverseOrder());
        int totalGames = 0, totalWins = 0, totalLosses = 0, totalDraws = 0;
        Set<LocalDate> allActiveDays = new HashSet<>();

        for (Map.Entry<LocalDate, List<DayAccountData>> entry : dataByDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<DayAccountData> accountsData = entry.getValue();
            int year = date.getYear();

            // Aggregate totals for this day
            int dayCount = 0, dayWins = 0, dayLosses = 0, dayDraws = 0;
            List<CalendarDayAccountBreakdown> byAccount = new ArrayList<>();

            for (DayAccountData data : accountsData) {
                dayCount += data.count;
                dayWins += data.wins;
                dayLosses += data.losses;
                dayDraws += data.draws;

                byAccount.add(new CalendarDayAccountBreakdown(
                    data.accountId, data.username, data.platform, data.label,
                    data.count, data.wins, data.losses, data.draws
                ));
            }

            CalendarDayResponse dayResponse = new CalendarDayResponse(
                date, dayCount, dayWins, dayLosses, dayDraws, byAccount
            );

            yearDays.computeIfAbsent(year, k -> new ArrayList<>()).add(dayResponse);

            totalGames += dayCount;
            totalWins += dayWins;
            totalLosses += dayLosses;
            totalDraws += dayDraws;
            allActiveDays.add(date);
        }

        // Build year responses
        List<CalendarYearResponse> years = new ArrayList<>();
        for (Map.Entry<Integer, List<CalendarDayResponse>> entry : yearDays.entrySet()) {
            int year = entry.getKey();
            List<CalendarDayResponse> days = entry.getValue();

            // Sort days within year ascending
            days.sort(Comparator.comparing(CalendarDayResponse::date));

            int yearGames = days.stream().mapToInt(CalendarDayResponse::count).sum();
            int yearWins = days.stream().mapToInt(CalendarDayResponse::wins).sum();
            int yearLosses = days.stream().mapToInt(CalendarDayResponse::losses).sum();
            int yearDraws = days.stream().mapToInt(CalendarDayResponse::draws).sum();

            years.add(new CalendarYearResponse(
                year, yearGames, days.size(), yearWins, yearLosses, yearDraws, days
            ));
        }

        // Build year range string
        int minYear = years.isEmpty() ? 0 : years.get(years.size() - 1).year();
        int maxYear = years.isEmpty() ? 0 : years.get(0).year();
        String yearRange = minYear == maxYear
            ? String.valueOf(minYear)
            : minYear + "-" + maxYear;

        CalendarResponse.CalendarSummary summary = new CalendarResponse.CalendarSummary(
            totalGames, totalWins, totalLosses, totalDraws,
            allActiveDays.size(), yearRange, uniqueAccountIds.size()
        );

        return new CalendarResponse(years, summary);
    }

    /**
     * Returns enhanced stats with per-account breakdown.
     */
    @Transactional(readOnly = true)
    public StatsResponse getStatsMultiAccount(
            List<Long> accountIds,
            TimeControlCategory timeControlCategory,
            Color color) {

        // Get overall counts by result
        List<Object[]> resultCounts = gameRepository.countByResultMultiAccount(
            accountIds, timeControlCategory, color
        );

        long wins = 0, losses = 0, draws = 0;
        for (Object[] row : resultCounts) {
            GameResult result = (GameResult) row[0];
            long count = ((Number) row[1]).longValue();
            switch (result) {
                case WIN -> wins = count;
                case LOSS -> losses = count;
                case DRAW -> draws = count;
            }
        }

        // Get counts by color and result
        Map<String, StatsResponse.ColorStats> byColor = new HashMap<>();

        if (color == null) {
            List<Object[]> colorCounts = gameRepository.countByColorAndResultMultiAccount(
                accountIds, timeControlCategory
            );

            Map<Color, long[]> colorMap = new HashMap<>();
            colorMap.put(Color.WHITE, new long[3]);
            colorMap.put(Color.BLACK, new long[3]);

            for (Object[] row : colorCounts) {
                Color c = (Color) row[0];
                GameResult r = (GameResult) row[1];
                long count = ((Number) row[2]).longValue();

                int idx = switch (r) {
                    case WIN -> 0;
                    case LOSS -> 1;
                    case DRAW -> 2;
                };
                colorMap.get(c)[idx] = count;
            }

            for (Color c : Color.values()) {
                long[] counts = colorMap.get(c);
                byColor.put(c.name(), new StatsResponse.ColorStats(
                    counts[0], counts[1], counts[2]
                ));
            }
        }

        // Get per-account breakdown
        List<Object[]> accountStats = gameRepository.getStatsByAccount(
            accountIds, timeControlCategory, color
        );

        List<StatsResponse.AccountStats> byAccount = new ArrayList<>();
        for (Object[] row : accountStats) {
            Long accountId = ((Number) row[0]).longValue();
            String username = (String) row[1];
            Platform platform = (Platform) row[2];
            String label = (String) row[3];
            long total = ((Number) row[4]).longValue();
            long accWins = ((Number) row[5]).longValue();
            long accLosses = ((Number) row[6]).longValue();
            long accDraws = ((Number) row[7]).longValue();

            byAccount.add(new StatsResponse.AccountStats(
                accountId, username, platform, label, total, accWins, accLosses, accDraws
            ));
        }

        return new StatsResponse(
            wins + losses + draws,
            wins,
            losses,
            draws,
            byColor,
            byAccount
        );
    }

    // Helper method to parse dates from different database formats
    private LocalDate parseDate(Object dateObj) {
        if (dateObj instanceof LocalDate ld) {
            return ld;
        } else if (dateObj instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        } else {
            return LocalDate.parse(dateObj.toString());
        }
    }

    // Internal data class for processing
    private record DayAccountData(
        LocalDate date,
        Long accountId,
        String username,
        Platform platform,
        String label,
        int count,
        int wins,
        int losses,
        int draws
    ) {}
}
