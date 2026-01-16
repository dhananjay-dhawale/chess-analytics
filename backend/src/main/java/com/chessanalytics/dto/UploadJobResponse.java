package com.chessanalytics.dto;

import com.chessanalytics.model.JobStatus;
import com.chessanalytics.model.UploadJob;
import java.time.LocalDateTime;

public record UploadJobResponse(
    Long id,
    Long accountId,
    String fileName,
    JobStatus status,
    Integer totalGames,
    Integer processedGames,
    Integer duplicateGames,
    Integer archivesProcessed,
    Integer totalArchives,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime completedAt,
    Integer progressPercent
) {
    public static UploadJobResponse from(UploadJob job) {
        Integer progress = null;
        if (job.getTotalGames() != null && job.getTotalGames() > 0) {
            progress = (int) ((job.getProcessedGames() * 100.0) / job.getTotalGames());
        }

        return new UploadJobResponse(
            job.getId(),
            job.getAccount().getId(),
            job.getFileName(),
            job.getStatus(),
            job.getTotalGames(),
            job.getProcessedGames(),
            job.getDuplicateGames(),
            job.getArchivesProcessed(),
            job.getTotalArchives(),
            job.getErrorMessage(),
            job.getCreatedAt(),
            job.getCompletedAt(),
            progress
        );
    }
}
