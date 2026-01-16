package com.chessanalytics.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tracks the status of an asynchronous PGN upload/processing job.
 * Allows the frontend to poll for progress updates.
 */
@Entity
@Table(name = "upload_job")
public class UploadJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "total_games")
    private Integer totalGames;

    @Column(name = "processed_games")
    private Integer processedGames;

    @Column(name = "duplicate_games")
    private Integer duplicateGames;

    @Column(name = "archives_processed")
    private Integer archivesProcessed;

    @Column(name = "total_archives")
    private Integer totalArchives;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public UploadJob() {
        this.status = JobStatus.PENDING;
        this.processedGames = 0;
        this.duplicateGames = 0;
        this.createdAt = LocalDateTime.now();
    }

    public UploadJob(Account account, String fileName) {
        this();
        this.account = account;
        this.fileName = fileName;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public Integer getTotalGames() {
        return totalGames;
    }

    public void setTotalGames(Integer totalGames) {
        this.totalGames = totalGames;
    }

    public Integer getProcessedGames() {
        return processedGames;
    }

    public void setProcessedGames(Integer processedGames) {
        this.processedGames = processedGames;
    }

    public Integer getDuplicateGames() {
        return duplicateGames;
    }

    public void setDuplicateGames(Integer duplicateGames) {
        this.duplicateGames = duplicateGames;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    /**
     * Marks the job as completed successfully.
     */
    public void markCompleted() {
        this.status = JobStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Marks the job as failed with an error message.
     */
    public void markFailed(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Increments the processed game count.
     */
    public void incrementProcessed() {
        this.processedGames = (this.processedGames == null ? 0 : this.processedGames) + 1;
    }

    /**
     * Increments the duplicate game count.
     */
    public void incrementDuplicate() {
        this.duplicateGames = (this.duplicateGames == null ? 0 : this.duplicateGames) + 1;
    }

    public Integer getArchivesProcessed() {
        return archivesProcessed;
    }

    public void setArchivesProcessed(Integer archivesProcessed) {
        this.archivesProcessed = archivesProcessed;
    }

    public Integer getTotalArchives() {
        return totalArchives;
    }

    public void setTotalArchives(Integer totalArchives) {
        this.totalArchives = totalArchives;
    }
}
