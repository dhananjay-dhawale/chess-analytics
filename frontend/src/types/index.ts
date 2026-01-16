/** Supported chess platforms */
export type Platform = 'CHESS_COM' | 'LICHESS' | 'OTHER';

/** Display names for platforms */
export const PLATFORM_LABELS: Record<Platform, string> = {
  CHESS_COM: 'Chess.com',
  LICHESS: 'Lichess',
  OTHER: 'Other'
};

/** Job processing status */
export type JobStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

/** Account entity */
export interface Account {
  id: number;
  platform: Platform;
  platformDisplayName: string;
  username: string;
  label: string | null;
  gameCount: number;
  createdAt: string;
  lastSyncAt: string | null;
}

/** Request to create an account */
export interface CreateAccountRequest {
  platform: Platform;
  username: string;
  label?: string;
}

/** Request to update an account */
export interface UpdateAccountRequest {
  label?: string;
}

/** Account validation error response */
export interface AccountErrorResponse {
  error: 'INVALID_USERNAME' | 'DUPLICATE_ACCOUNT' | 'EXTERNAL_API_ERROR';
  message: string;
  platform: string | null;
  existingAccountId: number | null;
}

/** Upload job status response */
export interface UploadJob {
  id: number;
  accountId: number;
  fileName: string | null;
  status: JobStatus;
  totalGames: number | null;
  processedGames: number | null;
  duplicateGames: number | null;
  archivesProcessed: number | null;
  totalArchives: number | null;
  errorMessage: string | null;
  createdAt: string;
  completedAt: string | null;
  progressPercent: number | null;
}

/** Single day in calendar heatmap (legacy) */
export interface CalendarDay {
  date: string;
  count: number;
}

/** Per-account breakdown for a day */
export interface CalendarDayAccountBreakdown {
  accountId: number;
  username: string;
  platform: Platform;
  label: string | null;
  count: number;
  wins: number;
  losses: number;
  draws: number;
}

/** Rich day data with W/L/D and account breakdown */
export interface CalendarDayResponse {
  date: string;
  count: number;
  wins: number;
  losses: number;
  draws: number;
  byAccount: CalendarDayAccountBreakdown[];
}

/** Year data with all days and summary */
export interface CalendarYearResponse {
  year: number;
  totalGames: number;
  activeDays: number;
  wins: number;
  losses: number;
  draws: number;
  days: CalendarDayResponse[];
}

/** Multi-year calendar response */
export interface CalendarResponse {
  years: CalendarYearResponse[];
  summary: {
    totalGames: number;
    totalWins: number;
    totalLosses: number;
    totalDraws: number;
    activeDays: number;
    yearRange: string;
    accountCount: number;
  };
}

/** Color-specific stats */
export interface ColorStats {
  wins: number;
  losses: number;
  draws: number;
}

/** Per-account stats */
export interface AccountStats {
  accountId: number;
  username: string;
  platform: Platform;
  label: string | null;
  total: number;
  wins: number;
  losses: number;
  draws: number;
}

/** Overall statistics response */
export interface Stats {
  total: number;
  wins: number;
  losses: number;
  draws: number;
  byColor: {
    WHITE?: ColorStats;
    BLACK?: ColorStats;
  };
  byAccount?: AccountStats[];
}

/** Result of upload operation */
export interface UploadResult {
  accountId: number;
  jobId: number;
}
