import { useEffect, useState, useCallback, useRef } from 'react';
import CalendarHeatmapLib from 'react-calendar-heatmap';
import 'react-calendar-heatmap/dist/styles.css';
import html2canvas from 'html2canvas';
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
  const [isCapturing, setIsCapturing] = useState(false);
  const [showShareMenu, setShowShareMenu] = useState(false);
  const calendarRef = useRef<HTMLDivElement>(null);

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

  const captureScreenshot = useCallback(async (): Promise<Blob | null> => {
    if (!calendarRef.current) return null;

    setIsCapturing(true);
    setTooltip(null); // Hide tooltip during capture

    try {
      const canvas = await html2canvas(calendarRef.current, {
        backgroundColor: '#0d1117', // Match app background
        scale: 2, // Higher resolution for social media
        logging: false,
        useCORS: true,
      });

      return new Promise((resolve) => {
        canvas.toBlob((blob) => {
          resolve(blob);
        }, 'image/png', 1.0);
      });
    } catch (err) {
      console.error('Failed to capture screenshot:', err);
      return null;
    } finally {
      setIsCapturing(false);
    }
  }, []);

  const handleDownload = useCallback(async () => {
    const blob = await captureScreenshot();
    if (!blob) {
      alert('Failed to create image. Please try again.');
      return;
    }

    // For mobile, try using share API first if available
    if (navigator.share && navigator.canShare) {
      try {
        const file = new File([blob], `chess-activity-${new Date().toISOString().split('T')[0]}.png`, { type: 'image/png' });
        if (navigator.canShare({ files: [file] })) {
          await navigator.share({
            files: [file],
            title: 'My Chess Activity',
            text: `${data?.summary.totalGames.toLocaleString()} games played!`,
          });
          setShowShareMenu(false);
          return;
        }
      } catch (err) {
        // User cancelled or share failed, fall through to download
        if ((err as Error).name !== 'AbortError') {
          console.log('Share API failed, falling back to download');
        }
      }
    }

    // Fallback: direct download
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `chess-activity-${new Date().toISOString().split('T')[0]}.png`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    setShowShareMenu(false);
  }, [captureScreenshot, data]);

  const handleCopyToClipboard = useCallback(async () => {
    const blob = await captureScreenshot();
    if (!blob) {
      alert('Failed to create image. Please try again.');
      return;
    }

    try {
      // Check if clipboard API is available and supports images
      if (navigator.clipboard && typeof ClipboardItem !== 'undefined') {
        await navigator.clipboard.write([
          new ClipboardItem({ 'image/png': blob })
        ]);
        alert('Image copied to clipboard!');
      } else {
        // Mobile fallback: use share or download
        alert('Clipboard not supported on this device. Use Download instead.');
      }
    } catch (err) {
      console.error('Clipboard write failed:', err);
      alert('Could not copy to clipboard. Use Download instead.');
    }
    setShowShareMenu(false);
  }, [captureScreenshot]);

  const handleShareTwitter = useCallback(async () => {
    // Download the image first, then open Twitter
    await handleDownload();
    const text = encodeURIComponent(
      `My chess activity: ${data?.summary.totalGames.toLocaleString()} games played! #chess #chesscom #lichess`
    );
    window.open(`https://twitter.com/intent/tweet?text=${text}`, '_blank');
  }, [data, handleDownload]);

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
      <div className="calendar-header">
        <h2>Activity</h2>
        <div className="share-container">
          <button
            className="share-button"
            onClick={() => setShowShareMenu(!showShareMenu)}
            disabled={isCapturing}
            title="Share your activity"
          >
            {isCapturing ? '...' : '📤'}
          </button>
          {showShareMenu && (
            <div className="share-menu">
              <button onClick={handleDownload}>
                💾 Download PNG
              </button>
              <button onClick={handleCopyToClipboard}>
                📋 Copy to Clipboard
              </button>
              <button onClick={handleShareTwitter}>
                🐦 Share on Twitter
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Capturable content */}
      <div ref={calendarRef} className="calendar-capture-area">
        <div className="calendar-branding">
          <span className="brand-title">Chess Analytics</span>
          <span className="brand-stats">
            {data.summary.totalGames.toLocaleString()} games across{' '}
            {data.summary.activeDays} days
          </span>
        </div>

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

        <div className="calendar-watermark">
          chessanalytics.app
        </div>
      </div>

      {tooltip && <CalendarTooltip tooltip={tooltip} />}

      {/* Click outside to close share menu */}
      {showShareMenu && (
        <div className="share-menu-backdrop" onClick={() => setShowShareMenu(false)} />
      )}
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
