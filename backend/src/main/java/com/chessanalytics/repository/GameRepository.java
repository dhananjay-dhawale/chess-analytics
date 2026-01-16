package com.chessanalytics.repository;

import com.chessanalytics.model.Game;
import com.chessanalytics.model.Color;
import com.chessanalytics.model.TimeControlCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    /**
     * Check if a game with this hash already exists for the account.
     */
    boolean existsByAccountIdAndPgnHash(Long accountId, String pgnHash);

    /**
     * Find games for an account with pagination.
     */
    Page<Game> findByAccountIdOrderByPlayedAtDesc(Long accountId, Pageable pageable);

    /**
     * Count games per day for calendar heatmap.
     * Returns date string and count pairs.
     */
    @Query("""
        SELECT CAST(g.playedAt AS DATE) as gameDate, COUNT(g) as gameCount
        FROM Game g
        WHERE g.playedAt >= :startDate AND g.playedAt < :endDate
        AND (:accountId IS NULL OR g.account.id = :accountId)
        AND (:timeControlCategory IS NULL OR g.timeControlCategory = :timeControlCategory)
        AND (:color IS NULL OR g.color = :color)
        GROUP BY CAST(g.playedAt AS DATE)
        ORDER BY gameDate
        """)
    List<Object[]> countGamesByDay(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("accountId") Long accountId,
        @Param("timeControlCategory") TimeControlCategory timeControlCategory,
        @Param("color") Color color
    );

    /**
     * Get total game counts grouped by result.
     */
    @Query("""
        SELECT g.result, COUNT(g)
        FROM Game g
        WHERE (:accountId IS NULL OR g.account.id = :accountId)
        AND (:timeControlCategory IS NULL OR g.timeControlCategory = :timeControlCategory)
        AND (:color IS NULL OR g.color = :color)
        GROUP BY g.result
        """)
    List<Object[]> countByResult(
        @Param("accountId") Long accountId,
        @Param("timeControlCategory") TimeControlCategory timeControlCategory,
        @Param("color") Color color
    );

    /**
     * Get game counts grouped by result and color.
     */
    @Query("""
        SELECT g.color, g.result, COUNT(g)
        FROM Game g
        WHERE (:accountId IS NULL OR g.account.id = :accountId)
        AND (:timeControlCategory IS NULL OR g.timeControlCategory = :timeControlCategory)
        GROUP BY g.color, g.result
        """)
    List<Object[]> countByColorAndResult(
        @Param("accountId") Long accountId,
        @Param("timeControlCategory") TimeControlCategory timeControlCategory
    );

    /**
     * Count total games for an account.
     */
    long countByAccountId(Long accountId);

    /**
     * Delete all games for an account.
     */
    void deleteByAccountId(Long accountId);

    /**
     * Get daily aggregates with W/L/D breakdown per account for multi-year calendar.
     * Returns: date, accountId, username, platform, label, count, wins, losses, draws
     */
    @Query("""
        SELECT CAST(g.playedAt AS DATE) as gameDate,
               g.account.id as accountId,
               g.account.username as username,
               g.account.platform as platform,
               g.account.label as label,
               COUNT(g) as gameCount,
               SUM(CASE WHEN g.result = com.chessanalytics.model.GameResult.WIN THEN 1 ELSE 0 END) as wins,
               SUM(CASE WHEN g.result = com.chessanalytics.model.GameResult.LOSS THEN 1 ELSE 0 END) as losses,
               SUM(CASE WHEN g.result = com.chessanalytics.model.GameResult.DRAW THEN 1 ELSE 0 END) as draws
        FROM Game g
        WHERE (:accountIds IS NULL OR g.account.id IN :accountIds)
        AND (:timeControlCategory IS NULL OR g.timeControlCategory = :timeControlCategory)
        AND (:color IS NULL OR g.color = :color)
        GROUP BY CAST(g.playedAt AS DATE), g.account.id, g.account.username, g.account.platform, g.account.label
        ORDER BY gameDate DESC
        """)
    List<Object[]> getDailyStatsByAccount(
        @Param("accountIds") Collection<Long> accountIds,
        @Param("timeControlCategory") TimeControlCategory timeControlCategory,
        @Param("color") Color color
    );

    /**
     * Get total game counts grouped by result for multiple accounts.
     */
    @Query("""
        SELECT g.result, COUNT(g)
        FROM Game g
        WHERE (:accountIds IS NULL OR g.account.id IN :accountIds)
        AND (:timeControlCategory IS NULL OR g.timeControlCategory = :timeControlCategory)
        AND (:color IS NULL OR g.color = :color)
        GROUP BY g.result
        """)
    List<Object[]> countByResultMultiAccount(
        @Param("accountIds") Collection<Long> accountIds,
        @Param("timeControlCategory") TimeControlCategory timeControlCategory,
        @Param("color") Color color
    );

    /**
     * Get game counts grouped by result and color for multiple accounts.
     */
    @Query("""
        SELECT g.color, g.result, COUNT(g)
        FROM Game g
        WHERE (:accountIds IS NULL OR g.account.id IN :accountIds)
        AND (:timeControlCategory IS NULL OR g.timeControlCategory = :timeControlCategory)
        GROUP BY g.color, g.result
        """)
    List<Object[]> countByColorAndResultMultiAccount(
        @Param("accountIds") Collection<Long> accountIds,
        @Param("timeControlCategory") TimeControlCategory timeControlCategory
    );

    /**
     * Get game counts grouped by account for stats breakdown.
     */
    @Query("""
        SELECT g.account.id as accountId,
               g.account.username as username,
               g.account.platform as platform,
               g.account.label as label,
               COUNT(g) as total,
               SUM(CASE WHEN g.result = com.chessanalytics.model.GameResult.WIN THEN 1 ELSE 0 END) as wins,
               SUM(CASE WHEN g.result = com.chessanalytics.model.GameResult.LOSS THEN 1 ELSE 0 END) as losses,
               SUM(CASE WHEN g.result = com.chessanalytics.model.GameResult.DRAW THEN 1 ELSE 0 END) as draws
        FROM Game g
        WHERE (:accountIds IS NULL OR g.account.id IN :accountIds)
        AND (:timeControlCategory IS NULL OR g.timeControlCategory = :timeControlCategory)
        AND (:color IS NULL OR g.color = :color)
        GROUP BY g.account.id, g.account.username, g.account.platform, g.account.label
        """)
    List<Object[]> getStatsByAccount(
        @Param("accountIds") Collection<Long> accountIds,
        @Param("timeControlCategory") TimeControlCategory timeControlCategory,
        @Param("color") Color color
    );
}
