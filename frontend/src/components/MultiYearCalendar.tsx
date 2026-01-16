import { useEffect, useState, useCallback } from 'react';
import CalendarHeatmapLib from 'react-calendar-heatmap';
import 'react-calendar-heatmap/dist/styles.css';
import type { CalendarResponse, CalendarDayResponse, CalendarDayAccountBreakdown } from '../types';
import { getMultiYearCalendarData } from '../api/client';

interface MultiYearCalendarProps {
  accountIds: number[];
  refreshKey: number;
}

interface TooltipData {
  day: CalendarDayResponse;
  x: number;
  y: number;
}

export function MultiYearCalendar({ accountIds, refreshKey }: MultiYearCalendarProps) {
  const [data, setData] = useState<CalendarResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tooltip, setTooltip] = useState<TooltipData | null>(null);

  useEffect(() => {
    let isActive = true;

    const fetchData = async () => {
      if (accountIds.length === 0) {
        setData(null);
        setLoading(false);
        return;
      }

      setLoading(true);
      setError(null);

      try {
        const calendarData = await getMultiYearCalendarData(accountIds);
        if (isActive) {
          setData(calendarData);
        }
      } catch (err) {
        if (isActive) {
          setError(err instanceof Error ? err.message : 'Failed to load calendar data');
        }
      } finally {
        if (isActive) {
          setLoading(false);
        }
      }
    };

    fetchData();

    return () => {
      isActive = false;
    };
  }, [accountIds, refreshKey]);

  const getClassForValue = useCallback((value: any): string => {
    if (!value || value.count === 0) {
      return 'color-empty';
    }
    if (value.count < 5) {
      return 'color-scale-1';
    }
    if (value.count < 10) {
      return 'color-scale-2';
    }
    if (value.count < 20) {
      return 'color-scale-3';
    }
    return 'color-scale-4';
  }, []);

  const handleMouseOver = useCallback((event: React.MouseEvent, day: CalendarDayResponse) => {
    const rect = (event.target as HTMLElement).getBoundingClientRect();
    setTooltip({
      day,
      x: rect.left + rect.width / 2,
      y: rect.top - 10,
    });
  }, []);

  const handleMouseLeave = useCallback(() => {
    setTooltip(null);
  }, []);

  if (loading) {
    return (
      <section className="card multi-year-calendar">
        <h2>Activity</h2>
        <div className="loading">Loading calendar...</div>
      </section>
    );
  }

  if (error) {
    return (
      <section className="card multi-year-calendar">
        <h2>Activity</h2>
        <div className="error-message">{error}</div>
      </section>
    );
  }

  if (!data || data.years.length === 0) {
    return (
      <section className="card multi-year-calendar">
        <h2>Activity</h2>
        <div className="empty-state">No game data to display</div>
      </section>
    );
  }

  return (
    <section className="card multi-year-calendar">
      <h2>Activity</h2>

      <div className="calendar-summary">
        <span>
          {data.summary.totalGames.toLocaleString()} games across{' '}
          {data.summary.activeDays} days ({data.summary.yearRange})
        </span>
        {data.summary.accountCount > 1 && (
          <span className="account-count">
            {data.summary.accountCount} accounts combined
          </span>
        )}
      </div>

      <div className="years-container">
        {data.years.map((yearData) => (
          <YearHeatmap
            key={yearData.year}
            yearData={yearData}
            getClassForValue={getClassForValue}
            onDayHover={handleMouseOver}
            onDayLeave={handleMouseLeave}
          />
        ))}
      </div>

      <div className="heatmap-legend">
        <span>Less</span>
        <div className="legend-scale">
          <div className="legend-item color-empty" />
          <div className="legend-item color-scale-1" />
          <div className="legend-item color-scale-2" />
          <div className="legend-item color-scale-3" />
          <div className="legend-item color-scale-4" />
        </div>
        <span>More</span>
      </div>

      {tooltip && <CalendarTooltip tooltip={tooltip} />}
    </section>
  );
}

interface YearHeatmapProps {
  yearData: CalendarResponse['years'][0];
  getClassForValue: (value: any) => string;
  onDayHover: (event: React.MouseEvent, day: CalendarDayResponse) => void;
  onDayLeave: () => void;
}

function YearHeatmap({ yearData, getClassForValue, onDayHover, onDayLeave }: YearHeatmapProps) {
  const startDate = new Date(yearData.year, 0, 1);
  const endDate = new Date(yearData.year, 11, 31);

  // Convert days to format expected by the library (include all day data for tooltip)
  const values = yearData.days.map((day) => ({
    ...day,
  }));

  const winRate = yearData.totalGames > 0
    ? ((yearData.wins / yearData.totalGames) * 100).toFixed(1)
    : '0';

  return (
    <div className="year-heatmap">
      <div className="year-header">
        <span className="year-label">{yearData.year}</span>
        <span className="year-stats">
          {yearData.totalGames.toLocaleString()} games
          <span className="year-stats-detail">
            ({yearData.wins}W / {yearData.losses}L / {yearData.draws}D - {winRate}%)
          </span>
        </span>
      </div>
      <div className="heatmap-container">
        <CalendarHeatmapLib
          startDate={startDate}
          endDate={endDate}
          values={values}
          classForValue={getClassForValue}
          showWeekdayLabels={true}
          gutterSize={2}
          onMouseOver={(event, value) => {
            if (value && value.count > 0) {
              onDayHover(event as any, value as CalendarDayResponse);
            }
          }}
          onMouseLeave={onDayLeave}
        />
      </div>
    </div>
  );
}

interface CalendarTooltipProps {
  tooltip: TooltipData;
}

function CalendarTooltip({ tooltip }: CalendarTooltipProps) {
  const { day, x, y } = tooltip;

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-US', {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  };

  return (
    <div
      className="calendar-tooltip"
      style={{
        position: 'fixed',
        left: x,
        top: y,
        transform: 'translate(-50%, -100%)',
      }}
    >
      <div className="tooltip-header">
        {formatDate(day.date)}
      </div>
      <div className="tooltip-summary">
        <strong>{day.count} games</strong>: {day.wins}W / {day.losses}L / {day.draws}D
      </div>
      {day.byAccount && day.byAccount.length > 0 && (
        <div className="tooltip-accounts">
          {day.byAccount.map((account: CalendarDayAccountBreakdown) => (
            <div key={account.accountId} className="tooltip-account-row">
              <span className="tooltip-account-name">
                {account.username}
                {account.label && <span className="tooltip-account-label"> ({account.label})</span>}
              </span>
              <span className="tooltip-account-stats">
                {account.count}: {account.wins}W/{account.losses}L/{account.draws}D
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
