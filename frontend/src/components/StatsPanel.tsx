import { useEffect, useState } from 'react';
import type { Stats, AccountStats } from '../types';
import { getStats } from '../api/client';
import { PLATFORM_LABELS } from '../types';

interface StatsPanelProps {
  accountIds: number[];
  refreshKey: number;
}

export function StatsPanel({ accountIds, refreshKey }: StatsPanelProps) {
  const [stats, setStats] = useState<Stats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isActive = true;

    const fetchStats = async () => {
      if (accountIds.length === 0) {
        setStats(null);
        setLoading(false);
        return;
      }

      setLoading(true);
      setError(null);

      try {
        const data = await getStats(accountIds);
        if (isActive) {
          setStats(data);
        }
      } catch (err) {
        if (isActive) {
          setError(err instanceof Error ? err.message : 'Failed to load stats');
        }
      } finally {
        if (isActive) {
          setLoading(false);
        }
      }
    };

    fetchStats();

    return () => {
      isActive = false;
    };
  }, [accountIds, refreshKey]);

  if (loading) {
    return (
      <section className="card">
        <h2>Statistics</h2>
        <div className="loading">Loading statistics...</div>
      </section>
    );
  }

  if (error) {
    return (
      <section className="card">
        <h2>Statistics</h2>
        <div className="error-message">{error}</div>
      </section>
    );
  }

  if (!stats || stats.total === 0) {
    return (
      <section className="card">
        <h2>Statistics</h2>
        <div className="empty-state">No games found</div>
      </section>
    );
  }

  const winRate = stats.total > 0 ? ((stats.wins / stats.total) * 100).toFixed(1) : '0';

  const whiteStats = stats.byColor?.WHITE;
  const blackStats = stats.byColor?.BLACK;

  const whiteTotal = whiteStats ? whiteStats.wins + whiteStats.losses + whiteStats.draws : 0;
  const blackTotal = blackStats ? blackStats.wins + blackStats.losses + blackStats.draws : 0;

  const whiteWinRate = whiteTotal > 0 && whiteStats ? ((whiteStats.wins / whiteTotal) * 100).toFixed(1) : '0';
  const blackWinRate = blackTotal > 0 && blackStats ? ((blackStats.wins / blackTotal) * 100).toFixed(1) : '0';

  const showAccountBreakdown = stats.byAccount && stats.byAccount.length > 1;

  return (
    <section className="card">
      <h2>Statistics</h2>

      <div className="stats-grid">
        <div className="stat-item stat-total">
          <div className="stat-value">{stats.total.toLocaleString()}</div>
          <div className="stat-label">Total Games</div>
        </div>

        <div className="stat-item stat-wins">
          <div className="stat-value">{stats.wins.toLocaleString()}</div>
          <div className="stat-label">Wins ({winRate}%)</div>
        </div>

        <div className="stat-item stat-losses">
          <div className="stat-value">{stats.losses.toLocaleString()}</div>
          <div className="stat-label">Losses</div>
        </div>

        <div className="stat-item stat-draws">
          <div className="stat-value">{stats.draws.toLocaleString()}</div>
          <div className="stat-label">Draws</div>
        </div>
      </div>

      {(whiteStats || blackStats) && (
        <div className="stats-by-color">
          <h3>By Color</h3>
          <div className="color-stats">
            {whiteStats && (
              <div className="color-stat white">
                <span className="color-indicator">White</span>
                <span className="color-rate">{whiteWinRate}% wins</span>
                <span className="color-detail">
                  {whiteStats.wins}W / {whiteStats.losses}L / {whiteStats.draws}D
                </span>
              </div>
            )}
            {blackStats && (
              <div className="color-stat black">
                <span className="color-indicator">Black</span>
                <span className="color-rate">{blackWinRate}% wins</span>
                <span className="color-detail">
                  {blackStats.wins}W / {blackStats.losses}L / {blackStats.draws}D
                </span>
              </div>
            )}
          </div>
        </div>
      )}

      {showAccountBreakdown && (
        <div className="stats-by-account">
          <h3>By Account</h3>
          <div className="account-stats">
            {stats.byAccount!.map((account: AccountStats) => {
              const accountWinRate = account.total > 0
                ? ((account.wins / account.total) * 100).toFixed(1)
                : '0';
              return (
                <div key={account.accountId} className="account-stat">
                  <span className="account-stat-name">
                    <span className="account-stat-platform">
                      {PLATFORM_LABELS[account.platform]}
                    </span>
                    <span className="account-stat-username">{account.username}</span>
                    {account.label && (
                      <span className="account-stat-label">({account.label})</span>
                    )}
                  </span>
                  <span className="account-stat-games">
                    {account.total.toLocaleString()} games
                  </span>
                  <span className="account-stat-rate">{accountWinRate}% wins</span>
                  <span className="account-stat-detail">
                    {account.wins}W / {account.losses}L / {account.draws}D
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </section>
  );
}
