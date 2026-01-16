import type {
  Account,
  UploadJob,
  Stats,
  CalendarDay,
  CalendarResponse,
  CreateAccountRequest,
  UpdateAccountRequest,
  AccountErrorResponse
} from '../types';

const API_BASE = import.meta.env.VITE_API_BASE ?? '/api';


/**
 * Custom error class for account-related errors with detailed info.
 */
export class AccountError extends Error {
  constructor(
    message: string,
    public errorType: AccountErrorResponse['error'],
    public platform: string | null,
    public existingAccountId: number | null
  ) {
    super(message);
    this.name = 'AccountError';
  }
}

/**
 * Generic fetch wrapper with error handling.
 */
async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    // Try to parse as AccountErrorResponse
    try {
      const errorJson = JSON.parse(errorText) as AccountErrorResponse;
      if (errorJson.error) {
        throw new AccountError(
          errorJson.message,
          errorJson.error,
          errorJson.platform,
          errorJson.existingAccountId
        );
      }
    } catch (e) {
      if (e instanceof AccountError) throw e;
    }
    throw new Error(errorText || `HTTP ${response.status}`);
  }

  return response.json();
}

// ============================================================================
// Account Management
// ============================================================================

/**
 * Get all accounts.
 */
export async function getAccounts(): Promise<Account[]> {
  return fetchJson<Account[]>(`${API_BASE}/accounts`);
}

/**
 * Create a new account with validation.
 * For Chess.com accounts, validates username against their API.
 */
export async function createAccount(request: CreateAccountRequest): Promise<Account> {
  return fetchJson<Account>(`${API_BASE}/accounts`, {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

/**
 * Update an account's label.
 */
export async function updateAccount(accountId: number, request: UpdateAccountRequest): Promise<Account> {
  return fetchJson<Account>(`${API_BASE}/accounts/${accountId}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  });
}

/**
 * Delete an account.
 */
export async function deleteAccount(accountId: number): Promise<void> {
  const response = await fetch(`${API_BASE}/accounts/${accountId}`, {
    method: 'DELETE',
  });

  if (!response.ok) {
    throw new Error('Failed to delete account');
  }
}

// ============================================================================
// Upload / Jobs
// ============================================================================

/**
 * Uploads a PGN file for a specific account.
 */
export async function uploadPgn(accountId: number, file: File): Promise<UploadJob> {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch(`${API_BASE}/accounts/${accountId}/upload`, {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(errorText || 'Upload failed');
  }

  return response.json();
}

/**
 * Gets the current status of an upload job.
 */
export async function getJobStatus(accountId: number, jobId: number): Promise<UploadJob> {
  return fetchJson<UploadJob>(`${API_BASE}/accounts/${accountId}/jobs/${jobId}`);
}

/**
 * Starts a Chess.com API import for an account.
 * Returns the created job for progress tracking.
 */
export async function startChessComImport(accountId: number): Promise<UploadJob> {
  const response = await fetch(`${API_BASE}/accounts/${accountId}/import/chesscom`, {
    method: 'POST',
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || 'Failed to start import');
  }

  return response.json();
}

/**
 * Starts a Lichess API import for an account.
 * Returns the created job for progress tracking.
 */
export async function startLichessImport(accountId: number): Promise<UploadJob> {
  const response = await fetch(`${API_BASE}/accounts/${accountId}/import/lichess`, {
    method: 'POST',
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || 'Failed to start import');
  }

  return response.json();
}

// ============================================================================
// Analytics
// ============================================================================

/**
 * Gets statistics for selected accounts (or all if null/empty).
 */
export async function getStats(accountIds?: number[]): Promise<Stats> {
  const params = new URLSearchParams();
  if (accountIds && accountIds.length > 0) {
    params.set('accountIds', accountIds.join(','));
  }
  const query = params.toString();
  return fetchJson<Stats>(`${API_BASE}/analytics/stats/multi-account${query ? '?' + query : ''}`);
}

/**
 * Gets multi-year calendar data with rich tooltip information.
 * Returns all years with game data automatically.
 */
export async function getMultiYearCalendarData(accountIds?: number[]): Promise<CalendarResponse> {
  const params = new URLSearchParams();
  if (accountIds && accountIds.length > 0) {
    params.set('accountIds', accountIds.join(','));
  }
  const query = params.toString();
  return fetchJson<CalendarResponse>(`${API_BASE}/analytics/calendar/multi-year${query ? '?' + query : ''}`);
}

/**
 * Gets calendar heatmap data for an account (legacy endpoint).
 */
export async function getCalendarData(accountId: number, year: number): Promise<CalendarDay[]> {
  const from = `${year}-01-01`;
  const to = `${year}-12-31`;

  const response = await fetchJson<{ data: CalendarDay[] }>(
    `${API_BASE}/analytics/calendar?accountId=${accountId}&from=${from}&to=${to}`
  );

  return response.data;
}
