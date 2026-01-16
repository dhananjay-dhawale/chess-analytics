import { useEffect, useState } from 'react';
import CalendarHeatmapLib from 'react-calendar-heatmap';
import 'react-calendar-heatmap/dist/styles.css';
import type { CalendarDay } from '../types';
import { getCalendarData } from '../api/client';

interface CalendarHeatmapProps {
  accountId: number;
  refreshKey: number;
}

export function CalendarHeatmap({ accountId, refreshKey }: CalendarHeatmapProps) {
  const [data, setData] = useState<CalendarDay[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const currentYear = new Date().getFullYear();
  const startDate = new Date(currentYear, 0, 1);
  const endDate = new Date(currentYear, 11, 31);

  useEffect(() => {
    let isActive = true;

    const fetchData = async () => {
      setLoading(true);
      setError(null);

      try {
        const calendarData = await getCalendarData(accountId, currentYear);
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
  }, [accountId, refreshKey, currentYear]);

  const getClassForValue = (value: any): string => {
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
  };

  const getTooltipDataAttrs = (value: any): Record<string, string> => {
    if (!value || !value.date) {
      return {};
    }
    return {
      'data-tooltip': value.count + ' games on ' + value.date,
    };
  };

  const totalGames = data.reduce((sum, day) => sum + day.count, 0);
  const activeDays = data.filter((day) => day.count > 0).length;

  if (loading) {
    return (
      <section className="card">
        <h2>Activity ({currentYear})</h2>
        <div className="loading">Loading calendar...</div>
      </section>
    );
  }

  if (error) {
    return (
      <section className="card">
        <h2>Activity ({currentYear})</h2>
        <div className="error-message">{error}</div>
      </section>
    );
  }

  return (
    <section className="card">
      <h2>Activity ({currentYear})</h2>
      
      <div className="heatmap-summary">
        <span>{totalGames.toLocaleString()} games across {activeDays} days</span>
      </div>

      <div className="heatmap-container">
        <CalendarHeatmapLib
          startDate={startDate}
          endDate={endDate}
          values={data}
          classForValue={getClassForValue}
          tooltipDataAttrs={getTooltipDataAttrs}
          showWeekdayLabels={true}
          gutterSize={2}
        />
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
    </section>
  );
}
