package com.chessanalytics.model;

/**
 * Normalized time control categories.
 * Based on Chess.com/Lichess standards.
 */
public enum TimeControlCategory {
    ULTRABULLET,  // < 30 seconds
    BULLET,       // < 3 minutes
    BLITZ,        // 3-10 minutes
    RAPID,        // 10-30 minutes
    CLASSICAL,    // > 30 minutes
    CORRESPONDENCE,
    UNKNOWN
}
