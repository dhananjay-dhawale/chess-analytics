import { useState, useEffect, useCallback } from 'react';
import type { Account } from '../types';
import { getAccounts, deleteAccount, startChessComImport, startLichessImport } from '../api/client';
import { AddAccountModal } from './AddAccountModal';
import { PLATFORM_LABELS } from '../types';

interface AccountManagerProps {
  selectedAccountIds: number[];
  onSelectionChange: (accountIds: number[]) => void;
  onAccountsChange: () => void;
  onImportStarted?: (accountId: number, jobId: number) => void;
  importDisabled?: boolean;
}

export function AccountManager({
  selectedAccountIds,
  onSelectionChange,
  onAccountsChange,
  onImportStarted,
  importDisabled = false
}: AccountManagerProps) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAddModal, setShowAddModal] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [importingId, setImportingId] = useState<number | null>(null);
  const [showImportConfirm, setShowImportConfirm] = useState<Account | null>(null);

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

  const handleToggleAccount = (accountId: number) => {
    if (selectedAccountIds.includes(accountId)) {
      // Don't allow deselecting all
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
    // Keep at least one selected
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

      // Remove from selection if selected
      if (selectedAccountIds.includes(accountId)) {
        const newSelection = selectedAccountIds.filter(id => id !== accountId);
        // Ensure at least one remains selected if possible
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

      // Call the appropriate import API based on platform
      const job = account.platform === 'CHESS_COM'
        ? await startChessComImport(account.id)
        : await startLichessImport(account.id);

      onImportStarted(account.id, job.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to start import');
    } finally {
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
                className={`account-item ${selectedAccountIds.includes(account.id) ? 'selected' : ''}`}
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
                  <span className="account-games">
                    {account.gameCount.toLocaleString()} games
                  </span>
                </label>
                <div className="account-actions">
                  {onImportStarted && (
                    <button
                      className="sync-account-button"
                      onClick={() => handleImportClick(account)}
                      disabled={importDisabled || importingId === account.id || deletingId === account.id}
                      title={getSyncTooltip(account)}
                    >
                      {importingId === account.id ? '...' : '\u{1F504}'}
                    </button>
                  )}
                  <button
                    className="delete-account-button"
                    onClick={() => handleDeleteAccount(account.id)}
                    disabled={deletingId === account.id}
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
