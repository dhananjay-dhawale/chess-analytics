package com.chessanalytics.model;

/**
 * Status of a PGN upload/processing job.
 */
public enum JobStatus {
    PENDING,      // Job created, not yet started
    PROCESSING,   // Currently parsing games
    COMPLETED,    // Successfully finished
    FAILED        // Error occurred during processing
}
