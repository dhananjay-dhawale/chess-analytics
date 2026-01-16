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

  const getStatusLabel = (status: string): string => {
    switch (status) {
      case 'PENDING':
        return 'Preparing...';
      case 'PROCESSING':
        return 'Processing games...';
      case 'COMPLETED':
        return 'Complete!';
      case 'FAILED':
        return 'Failed';
      default:
        return status;
    }
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

  return (
    <div className="job-progress">
      <div className="status-text">{getStatusLabel(job.status)}</div>
      
      <div className="progress-bar-container">
        <div 
          className={'progress-bar ' + statusClass}
          style={{ width: progress + '%' }}
        />
      </div>
      
      <div className="progress-details">
        {total > 0 && (
          <span>
            {processed.toLocaleString()} / {total.toLocaleString()} games
            {duplicates > 0 && ' (' + duplicates.toLocaleString() + ' duplicates skipped)'}
          </span>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}
    </div>
  );
}
