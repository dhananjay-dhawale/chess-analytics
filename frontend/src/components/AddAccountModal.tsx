import { useState } from 'react';
import type { Platform } from '../types';
import { createAccount, AccountError } from '../api/client';
import { PLATFORM_LABELS } from '../types';

interface AddAccountModalProps {
  onClose: () => void;
  onAccountAdded: () => void;
}

export function AddAccountModal({ onClose, onAccountAdded }: AddAccountModalProps) {
  const [platform, setPlatform] = useState<Platform>('CHESS_COM');
  const [username, setUsername] = useState('');
  const [label, setLabel] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [validationState, setValidationState] = useState<'idle' | 'validating' | 'valid' | 'invalid'>('idle');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!username.trim()) {
      setError('Username is required');
      return;
    }

    try {
      setIsSubmitting(true);
      setError(null);
      setValidationState('validating');

      await createAccount({
        platform,
        username: username.trim(),
        label: label.trim() || undefined,
      });

      setValidationState('valid');
      onAccountAdded();
    } catch (err) {
      setValidationState('invalid');

      if (err instanceof AccountError) {
        switch (err.errorType) {
          case 'INVALID_USERNAME':
            setError(err.message);
            break;
          case 'DUPLICATE_ACCOUNT':
            setError(err.message);
            break;
          case 'EXTERNAL_API_ERROR':
            setError(err.message);
            break;
          default:
            setError('An unexpected error occurred');
        }
      } else {
        setError(err instanceof Error ? err.message : 'Failed to create account');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  return (
    <div className="modal-backdrop" onClick={handleBackdropClick}>
      <div className="modal">
        <div className="modal-header">
          <h2>Add Account</h2>
          <button className="modal-close" onClick={onClose}>×</button>
        </div>

        <form onSubmit={handleSubmit} className="modal-form">
          <div className="form-group">
            <label htmlFor="platform">Platform</label>
            <select
              id="platform"
              value={platform}
              onChange={(e) => setPlatform(e.target.value as Platform)}
              disabled={isSubmitting}
            >
              {Object.entries(PLATFORM_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label htmlFor="username">Username</label>
            <div className="input-with-status">
              <input
                id="username"
                type="text"
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value);
                  setValidationState('idle');
                  setError(null);
                }}
                placeholder="Enter your username"
                disabled={isSubmitting}
                autoFocus
              />
              {validationState === 'validating' && (
                <span className="input-status validating">Checking...</span>
              )}
              {validationState === 'valid' && (
                <span className="input-status valid">Valid</span>
              )}
              {validationState === 'invalid' && (
                <span className="input-status invalid">Invalid</span>
              )}
            </div>
            {platform === 'CHESS_COM' && (
              <span className="form-hint">
                Username will be validated against Chess.com
              </span>
            )}
          </div>

          <div className="form-group">
            <label htmlFor="label">Label (optional)</label>
            <input
              id="label"
              type="text"
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              placeholder="e.g., Main Account, Alt, etc."
              disabled={isSubmitting}
            />
            <span className="form-hint">
              A friendly name to identify this account
            </span>
          </div>

          {error && (
            <div className="error-message">{error}</div>
          )}

          <div className="modal-actions">
            <button
              type="button"
              className="cancel-button"
              onClick={onClose}
              disabled={isSubmitting}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="submit-button"
              disabled={isSubmitting || !username.trim()}
            >
              {isSubmitting ? 'Adding...' : 'Add Account'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
