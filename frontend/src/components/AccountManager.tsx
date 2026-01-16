import { useState, useEffect, useCallback, useRef } from 'react';
import type { Account, UploadJob } from '../types';
import { getAccounts, deleteAccount, startChessComImport, startLichessImport, getJobStatus } from '../api/client';
import { AddAccountModal } from './AddAccountModal';
import { PLATFORM_LABELS } from '../types';

interface AccountManagerProps {
  selectedAccountIds: number[];
  onSelectionChange: (accountIds: number[]) => void;
  onAccountsChange: () => void;
  onImportStarted?: (accountId: number, jobId: number) => void;
  activeJob?: { accountId: number; jobId: number } | null;
  onJobComplete?: () => void;
}

const POLL_INTERVAL_MS = 1000;

export function AccountManager({
  selectedAccountIds,
  onSelectionChange,
  onAccountsChange,
  onImportStarted,
  activeJob,
  onJobComplete
}: AccountManagerProps) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAddModal, setShowAddModal] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [importingId, setImportingId] = useState<number | null>(null);
  const [showImportConfirm, setShowImportConfirm] = useState<Account | null>(null);
  const [jobStatus, setJobStatus] = useState<UploadJob | null>(null);
  const pollingRef = useRef<number | null>(null);

  const fetchAccounts = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getAccounts();
      setAccounts(data);

      // If no selection yet and we have accounts, select all
      if (selectedAccountIds.length === 0 && data.length > 0) {
        onSelectionChange(data.map(a => a.id));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load accounts');
    } finally {
      setLoading(false);
    }
  }, [selectedAccountIds.length, onSelectionChange]);

  useEffect(() => {
    fetchAccounts();
  }, [fetchAccounts]);

  // Poll job status when there's an active job
  useEffect(() => {
    if (!activeJob) {
      setJobStatus(null);
      return;
    }

    let isActive = true;

    const poll = async () => {
      try {
        const status = await getJobStatus(activeJob.accountId, activeJob.jobId);
        if (!isActive) return;

        setJobStatus(status);

        if (status.status === 'COMPLETED') {
          onJobComplete?.();
          fetchAccounts(); // Refresh to get updated game count
        } else if (status.status === 'FAILED') {
          // Keep showing the error
        } else {
          pollingRef.current = window.setTimeout(poll, POLL_INTERVAL_MS);
        }
      } catch (err) {
        if (!isActive) return;
        console.error('Failed to poll job status:', err);
      }
    };

    poll();

    return () => {
      isActive = false;
      if (pollingRef.current) {
        clearTimeout(pollingRef.current);
      }
    };
  }, [activeJob, onJobComplete, fetchAccounts]);

  const handleToggleAccount = (accountId: number) => {
    if (selectedAccountIds.includes(accountId)) {
      if (selectedAccountIds.length > 1) {
        onSelectionChange(selectedAccountIds.filter(id => id !== accountId));
      }
    } else {
      onSelectionChange([...selectedAccountIds, accountId]);
    }
  };

  const handleSelectAll = () => {
    onSelectionChange(accounts.map(a => a.id));
  };

  const handleClearSelection = () => {
    if (accounts.length > 0) {
      onSelectionChange([accounts[0].id]);
    }
  };

  const handleDeleteAccount = async (accountId: number) => {
    if (!confirm('Delete this account and all its games?')) {
      return;
    }

    try {
      setDeletingId(accountId);
      await deleteAccount(accountId);

      if (selectedAccountIds.includes(accountId)) {
        const newSelection = selectedAccountIds.filter(id => id !== accountId);
        if (newSelection.length === 0) {
          const remaining = accounts.filter(a => a.id !== accountId);
          if (remaining.length > 0) {
            onSelectionChange([remaining[0].id]);
          } else {
            onSelectionChange([]);
          }
        } else {
          onSelectionChange(newSelection);
        }
      }

      await fetchAccounts();
      onAccountsChange();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete account');
    } finally {
      setDeletingId(null);
    }
  };

  const handleAccountAdded = () => {
    fetchAccounts();
    onAccountsChange();
    setShowAddModal(false);
  };

  const handleImportClick = (account: Account) => {
    setShowImportConfirm(account);
  };

  const handleConfirmImport = async () => {
    if (!showImportConfirm || !onImportStarted) return;

    const account = showImportConfirm;
    setShowImportConfirm(null);

    try {
      setImportingId(account.id);
      setError(null);

      const job = account.platform === 'CHESS_COM'
        ? await startChessComImport(account.id)
        : await startLichessImport(account.id);

      setImportingId(null);
      onImportStarted(account.id, job.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to start import');
      setImportingId(null);
    }
  };

  const totalGames = accounts
    .filter(a => selectedAccountIds.includes(a.id))
    .reduce((sum, a) => sum + a.gameCount, 0);

  const formatLastSync = (lastSyncAt: string | null): string => {
    if (!lastSyncAt) return 'Never synced';
    const date = new Date(lastSyncAt);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    if (diffDays === 0) {
      const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
      if (diffHours === 0) {
        const diffMins = Math.floor(diffMs / (1000 * 60));
        return diffMins <= 1 ? 'Just now' : `${diffMins} minutes ago`;
      }
      return diffHours === 1 ? '1 hour ago' : `${diffHours} hours ago`;
    }
    if (diffDays === 1) return 'Yesterday';
    if (diffDays < 7) return `${diffDays} days ago`;
    return date.toLocaleDateString();
  };

  const getSyncTooltip = (account: Account): string => {
    const syncStatus = formatLastSync(account.lastSyncAt);
    return `Sync games from ${PLATFORM_LABELS[account.platform]}\nLast sync: ${syncStatus}`;
  };

  const getProgressText = (): string => {
    if (!jobStatus) return 'Starting...';
    if (jobStatus.status === 'COMPLETED') return 'Done!';
    if (jobStatus.status === 'FAILED') return 'Failed';

    const isDiscovery = jobStatus.status === 'PENDING' ||
      (jobStatus.status === 'PROCESSING' && (!jobStatus.totalGames || jobStatus.totalGames === 0));

    if (isDiscovery) {
      if (jobStatus.totalArchives && jobStatus.totalArchives > 0) {
        return `${jobStatus.archivesProcessed ?? 0}/${jobStatus.totalArchives} archives`;
      }
      return 'Discovering...';
    }

    const processed = jobStatus.processedGames ?? 0;
    const total = jobStatus.totalGames ?? 0;
    const newGames = processed - (jobStatus.duplicateGames ?? 0);

    if (total > 0) {
      return `${newGames} new games`;
    }
    return 'Processing...';
  };

  const isAccountSyncing = (accountId: number) => activeJob?.accountId === accountId;
  const importDisabled = activeJob !== null;

  if (loading && accounts.length === 0) {
    return (
      <section className="card account-manager">
        <div className="account-manager-header">
          <h2>Accounts</h2>
        </div>
        <div className="loading">Loading accounts...</div>
      </section>
    );
  }

  return (
    <section className="card account-manager">
      <div className="account-manager-header">
        <h2>Accounts</h2>
        <button
          className="add-account-button"
          onClick={() => setShowAddModal(true)}
        >
          + Add Account
        </button>
      </div>

      {error && <div className="error-message">{error}</div>}

      {accounts.length === 0 ? (
        <div className="empty-state">
          <p>No accounts yet. Add an account to get started.</p>
        </div>
      ) : (
        <>
          <div className="account-list">
            {accounts.map(account => (
              <div
                key={account.id}
                className={`account-item ${selectedAccountIds.includes(account.id) ? 'selected' : ''} ${isAccountSyncing(account.id) ? 'syncing' : ''}`}
              >
                <label className="account-checkbox">
                  <input
                    type="checkbox"
                    checked={selectedAccountIds.includes(account.id)}
                    onChange={() => handleToggleAccount(account.id)}
                    disabled={deletingId === account.id}
                  />
                  <span className="account-info">
                    <span className="account-platform">{PLATFORM_LABELS[account.platform]}</span>
                    <span className="account-username">{account.username}</span>
                    {account.label && (
                      <span className="account-label">({account.label})</span>
                    )}
                  </span>
                </label>

                {isAccountSyncing(account.id) && jobStatus ? (
                  <span className={`account-sync-status ${jobStatus.status === 'COMPLETED' ? 'complete' : ''} ${jobStatus.status === 'FAILED' ? 'failed' : ''}`}>
                    {jobStatus.status !== 'COMPLETED' && jobStatus.status !== 'FAILED' && (
                      <span className="sync-spinner-small" />
                    )}
                    {getProgressText()}
                  </span>
                ) : (
                  <span className="account-games">
                    {account.gameCount.toLocaleString()} games
                  </span>
                )}

                <div className="account-actions">
                  {onImportStarted && (
                    <button
                      className="sync-account-button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleImportClick(account);
                      }}
                      disabled={importDisabled || importingId === account.id || deletingId === account.id}
                      title={getSyncTooltip(account)}
                    >
                      {importingId === account.id ? (
                        <span className="sync-spinner" />
                      ) : (
                        <svg className="sync-icon" viewBox="0 0 16 16" width="16" height="16">
                          <path fill="currentColor" d="M13.65 2.35a8 8 0 1 0 1.77 8.71.75.75 0 0 0-1.42-.49 6.5 6.5 0 1 1-1.46-7.11L11 5h3.25a.75.75 0 0 0 .75-.75V1l-1.35 1.35z"/>
                          <path fill="currentColor" d="M2.35 13.65a8 8 0 0 0 12.08-1.71.75.75 0 1 0-1.42-.49A6.5 6.5 0 0 1 3.5 8H5L3.65 6.65 2 8v.25c0 2.07.84 3.95 2.35 5.4z"/>
                        </svg>
                      )}
                    </button>
                  )}
                  <button
                    className="delete-account-button"
                    onClick={(e) => {
                      e.stopPropagation();
                      handleDeleteAccount(account.id);
                    }}
                    disabled={deletingId === account.id || isAccountSyncing(account.id)}
                    title="Delete account"
                  >
                    {deletingId === account.id ? '...' : '×'}
                  </button>
                </div>
              </div>
            ))}
          </div>

          <div className="account-selection-summary">
            <span>
              Selected: {selectedAccountIds.length} account{selectedAccountIds.length !== 1 ? 's' : ''} ({totalGames.toLocaleString()} games)
            </span>
            <div className="selection-buttons">
              <button
                className="text-button"
                onClick={handleSelectAll}
                disabled={selectedAccountIds.length === accounts.length}
              >
                Select All
              </button>
              <button
                className="text-button"
                onClick={handleClearSelection}
                disabled={selectedAccountIds.length <= 1}
              >
                Clear
              </button>
            </div>
          </div>
        </>
      )}

      {showAddModal && (
        <AddAccountModal
          onClose={() => setShowAddModal(false)}
          onAccountAdded={handleAccountAdded}
        />
      )}

      {showImportConfirm && (
        <div className="modal-overlay" onClick={() => setShowImportConfirm(null)}>
          <div className="modal import-confirm-modal" onClick={e => e.stopPropagation()}>
            <h3>Sync from {PLATFORM_LABELS[showImportConfirm.platform]}</h3>
            <p className="import-account-name">
              {showImportConfirm.username}
              {showImportConfirm.label && ` (${showImportConfirm.label})`}
            </p>
            <div className="import-info">
              <p className="last-sync-info">
                Last synced: <strong>{formatLastSync(showImportConfirm.lastSyncAt)}</strong>
              </p>
              {showImportConfirm.lastSyncAt ? (
                <p>This will fetch only new games since your last sync.</p>
              ) : (
                <p>This will fetch your complete game history from {PLATFORM_LABELS[showImportConfirm.platform]}.</p>
              )}
            </div>
            <div className="import-warning">
              <ul>
                {!showImportConfirm.lastSyncAt && (
                  <li>First sync may take several minutes for large accounts</li>
                )}
                <li>Rate-limited to respect {PLATFORM_LABELS[showImportConfirm.platform]} API</li>
                <li>Duplicate games will be skipped automatically</li>
              </ul>
            </div>
            <div className="modal-actions">
              <button
                className="button secondary"
                onClick={() => setShowImportConfirm(null)}
              >
                Cancel
              </button>
              <button
                className="button primary"
                onClick={handleConfirmImport}
              >
                {showImportConfirm.lastSyncAt ? 'Sync New Games' : 'Start Full Sync'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
