import { useState, useRef, useEffect } from 'react';
import type { Account } from '../types';
import { uploadPgn, getAccounts } from '../api/client';
import { PLATFORM_LABELS } from '../types';

interface UploadFormProps {
  accountIds: number[];
  onUploadStarted: (accountId: number, jobId: number) => void;
  disabled: boolean;
}

export function UploadForm({ accountIds, onUploadStarted, disabled }: UploadFormProps) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Load accounts when accountIds change
  useEffect(() => {
    const loadAccounts = async () => {
      try {
        const allAccounts = await getAccounts();
        const filtered = allAccounts.filter(a => accountIds.includes(a.id));
        setAccounts(filtered);

        // Auto-select first account if none selected
        if (filtered.length > 0 && !selectedAccountId) {
          setSelectedAccountId(filtered[0].id);
        } else if (selectedAccountId && !filtered.find(a => a.id === selectedAccountId)) {
          // Reset if selected account is no longer in list
          setSelectedAccountId(filtered.length > 0 ? filtered[0].id : null);
        }
      } catch (err) {
        console.error('Failed to load accounts', err);
      }
    };

    loadAccounts();
  }, [accountIds, selectedAccountId]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0] ?? null;
    setFile(selectedFile);
    setError(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!selectedAccountId) {
      setError('Please select an account');
      return;
    }

    if (!file) {
      setError('Please select a PGN file');
      return;
    }

    setIsUploading(true);

    try {
      const job = await uploadPgn(selectedAccountId, file);
      onUploadStarted(selectedAccountId, job.id);
      // Reset file selection after successful upload
      setFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setIsUploading(false);
    }
  };

  const isDisabled = disabled || isUploading;

  const getAccountLabel = (account: Account) => {
    const platformLabel = PLATFORM_LABELS[account.platform];
    if (account.label) {
      return `${platformLabel}: ${account.username} (${account.label})`;
    }
    return `${platformLabel}: ${account.username}`;
  };

  if (accounts.length === 0) {
    return null;
  }

  return (
    <section className="card">
      <h2>Upload PGN</h2>
      <form onSubmit={handleSubmit} className="upload-form">
        <div className="form-row">
          <div className="form-group">
            <label htmlFor="account">Account</label>
            <select
              id="account"
              value={selectedAccountId ?? ''}
              onChange={(e) => setSelectedAccountId(Number(e.target.value))}
              disabled={isDisabled}
            >
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {getAccountLabel(account)}
                </option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label htmlFor="file">PGN File</label>
            <div className="file-input-wrapper">
              <button
                type="button"
                className="file-button"
                onClick={() => fileInputRef.current?.click()}
                disabled={isDisabled}
              >
                Choose File
              </button>
              <span className="file-name">
                {file ? file.name : 'No file selected'}
              </span>
              <input
                ref={fileInputRef}
                id="file"
                type="file"
                accept=".pgn"
                onChange={handleFileChange}
                disabled={isDisabled}
                style={{ display: 'none' }}
              />
            </div>
          </div>
        </div>

        {error && <div className="error-message">{error}</div>}

        <button
          type="submit"
          className="submit-button"
          disabled={isDisabled || !file || !selectedAccountId}
        >
          {isUploading ? 'Uploading...' : 'Upload & Analyze'}
        </button>
      </form>
    </section>
  );
}
