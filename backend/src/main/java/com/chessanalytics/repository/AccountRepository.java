package com.chessanalytics.repository;

import com.chessanalytics.model.Account;
import com.chessanalytics.model.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Find an account by platform and username combination.
     */
    Optional<Account> findByPlatformAndUsername(Platform platform, String username);

    /**
     * Check if an account exists for the given platform and username.
     */
    boolean existsByPlatformAndUsername(Platform platform, String username);
}
