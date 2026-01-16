import { useEffect, useState, useRef } from 'react';
import type { UploadJob } from '../types';
import { getJobStatus } from '../api/client';

interface JobProgressProps {
  accountId: number;
  jobId: number;
  onComplete: () => void;
}

const POLL_INTERVAL_MS = 1000;

export function JobProgress({ accountId, jobId, onComplete }: JobProgressProps) {
  const [job, setJob] = useState<UploadJob | null>(null);
  const [error, setError] = useState<string | null>(null);
  const pollingRef = useRef<number | null>(null);

  useEffect(() => {
    let isActive = true;

    const poll = async () => {
      try {
        const status = await getJobStatus(accountId, jobId);

        if (!isActive) return;

        setJob(status);
        setError(null);

        if (status.status === 'COMPLETED') {
          onComplete();
        } else if (status.status === 'FAILED') {
          setError(status.errorMessage || 'Processing failed');
        } else {
          // Continue polling for PENDING and PROCESSING
          pollingRef.current = window.setTimeout(poll, POLL_INTERVAL_MS);
        }
      } catch (err) {
        if (!isActive) return;
        setError(err instanceof Error ? err.message : 'Failed to get status');
      }
    };

    poll();

    return () => {
      isActive = false;
      if (pollingRef.current) {
        clearTimeout(pollingRef.current);
      }
    };
  }, [accountId, jobId, onComplete]);

  // Determine if we're in discovery phase (fetching archives) or processing phase
  const isDiscoveryPhase = job && (
    job.status === 'PENDING' ||
    (job.status === 'PROCESSING' && (job.totalGames === null || job.totalGames === 0))
  );

  const getStatusLabel = (): string => {
    if (!job) return 'Starting...';

    if (job.status === 'COMPLETED') return 'Complete!';
    if (job.status === 'FAILED') return 'Failed';

    if (isDiscoveryPhase) {
      // Show archive progress if available (Chess.com)
      if (job.totalArchives && job.totalArchives > 0) {
        const archivesProcessed = job.archivesProcessed ?? 0;
        return `Discovering games... (${archivesProcessed}/${job.totalArchives} archives)`;
      }
      return 'Discovering games...';
    }

    return 'Processing games...';
  };

  if (error && !job) {
    return (
      <div className="job-progress">
        <div className="error-message">{error}</div>
      </div>
    );
  }

  if (!job) {
    return (
      <div className="job-progress">
        <div className="status-text">Starting...</div>
      </div>
    );
  }

  const progress = job.progressPercent ?? 0;
  const processed = job.processedGames ?? 0;
  const total = job.totalGames ?? 0;
  const duplicates = job.duplicateGames ?? 0;

  const statusClass = job.status === 'COMPLETED' ? 'completed' : job.status === 'FAILED' ? 'failed' : '';

  // Calculate archive progress for the circular spinner
  const archiveProgress = job.totalArchives && job.totalArchives > 0
    ? Math.round(((job.archivesProcessed ?? 0) / job.totalArchives) * 100)
    : 0;

  return (
    <div className="job-progress">
      <div className="status-text">{getStatusLabel()}</div>

      {isDiscoveryPhase ? (
        // Circular spinner during discovery phase
        <div className="circular-progress-container">
          <svg className="circular-progress" viewBox="0 0 36 36">
            <path
              className="circular-progress-bg"
              d="M18 2.0845
                a 15.9155 15.9155 0 0 1 0 31.831
                a 15.9155 15.9155 0 0 1 0 -31.831"
            />
            <path
              className="circular-progress-bar"
              strokeDasharray={`${archiveProgress}, 100`}
              d="M18 2.0845
                a 15.9155 15.9155 0 0 1 0 31.831
                a 15.9155 15.9155 0 0 1 0 -31.831"
            />
          </svg>
          <div className="circular-progress-spinner" />
        </div>
      ) : (
        // Linear progress bar during processing phase
        <div className="progress-bar-container">
          <div
            className={'progress-bar ' + statusClass}
            style={{ width: progress + '%' }}
          />
        </div>
      )}

      <div className="progress-details">
        {!isDiscoveryPhase && total > 0 && (
          <span>
            {processed.toLocaleString()} / {total.toLocaleString()} games
            {duplicates > 0 && ' (' + duplicates.toLocaleString() + ' duplicates skipped)'}
          </span>
        )}
        {isDiscoveryPhase && job.totalArchives && job.totalArchives > 0 && (
          <span>
            Fetching archive {(job.archivesProcessed ?? 0) + 1} of {job.totalArchives}
          </span>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}
    </div>
  );
}
