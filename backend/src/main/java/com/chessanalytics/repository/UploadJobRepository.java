package com.chessanalytics.repository;

import com.chessanalytics.model.UploadJob;
import com.chessanalytics.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UploadJobRepository extends JpaRepository<UploadJob, Long> {

    /**
     * Find all jobs for a specific account.
     */
    List<UploadJob> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    /**
     * Find jobs by status.
     */
    List<UploadJob> findByStatus(JobStatus status);

    /**
     * Delete all upload jobs for an account.
     */
    void deleteByAccountId(Long accountId);

    /**
     * Check if account has any jobs with the given statuses.
     * Used to prevent concurrent imports.
     */
    boolean existsByAccountIdAndStatusIn(Long accountId, List<JobStatus> statuses);
}
