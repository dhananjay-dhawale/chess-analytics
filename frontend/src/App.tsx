import { useState, useCallback } from 'react';
import { AccountManager } from './components/AccountManager';
import { UploadForm } from './components/UploadForm';
import { StatsPanel } from './components/StatsPanel';
import { MultiYearCalendar } from './components/MultiYearCalendar';
import './App.css';

function App() {
  // Track selected accounts for combined analytics
  const [selectedAccountIds, setSelectedAccountIds] = useState<number[]>([]);

  // Track active job for progress display
  const [activeJob, setActiveJob] = useState<{ accountId: number; jobId: number } | null>(null);

  // Counter to trigger re-fetches of stats/heatmap
  const [refreshKey, setRefreshKey] = useState(0);

  const handleUploadStarted = useCallback((accountId: number, jobId: number) => {
    setActiveJob({ accountId, jobId });
  }, []);

  const handleJobComplete = useCallback(() => {
    setActiveJob(null);
    // Trigger refresh of stats and heatmap
    setRefreshKey((prev) => prev + 1);
  }, []);

  const handleAccountsChange = useCallback(() => {
    // Refresh analytics when accounts are added/removed
    setRefreshKey((prev) => prev + 1);
  }, []);

  const handleSelectionChange = useCallback((accountIds: number[]) => {
    setSelectedAccountIds(accountIds);
  }, []);

  const isUploading = activeJob !== null;
  const hasAccounts = selectedAccountIds.length > 0;

  return (
    <div className="app">
      <header className="app-header">
        <h1>Chess Analytics</h1>
        <p>Analyze your chess games across multiple accounts</p>
      </header>

      <main className="app-main">
        <AccountManager
          selectedAccountIds={selectedAccountIds}
          onSelectionChange={handleSelectionChange}
          onAccountsChange={handleAccountsChange}
          onImportStarted={handleUploadStarted}
          activeJob={activeJob}
          onJobComplete={handleJobComplete}
        />

        {hasAccounts && (
          <UploadForm
            accountIds={selectedAccountIds}
            onUploadStarted={handleUploadStarted}
            disabled={isUploading}
          />
        )}

        {hasAccounts && (
          <>
            <StatsPanel
              accountIds={selectedAccountIds}
              refreshKey={refreshKey}
            />
            <MultiYearCalendar
              accountIds={selectedAccountIds}
              refreshKey={refreshKey}
            />
          </>
        )}

        {!hasAccounts && !isUploading && (
          <section className="card empty-state-card">
            <p>Add an account above to get started.</p>
            <p className="hint">
              You can add multiple Chess.com and Lichess accounts to view combined analytics.
            </p>
          </section>
        )}
      </main>

      <footer className="app-footer">
        <p>Chess Analytics - Personal Game Analysis Tool</p>
      </footer>
    </div>
  );
}

export default App;
