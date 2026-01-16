package com.chessanalytics.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Represents a single chess game parsed from a PGN file.
 * Stores both raw and normalized data for flexible querying.
 */
@Entity
@Table(
    name = "game",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"account_id", "pgn_hash"},
        name = "uk_game_account_hash"
    ),
    indexes = {
        @Index(name = "idx_game_played_at", columnList = "played_at"),
        @Index(name = "idx_game_account_id", columnList = "account_id"),
        @Index(name = "idx_game_time_control_category", columnList = "time_control_category")
    }
)
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @NotNull
    @Column(name = "played_at", nullable = false)
    private LocalDateTime playedAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GameResult result;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Color color;

    @Column(name = "time_control_raw", length = 50)
    private String timeControlRaw;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_control_category", length = 20)
    private TimeControlCategory timeControlCategory;

    @Column(name = "eco_code", length = 10)
    private String ecoCode;

    @Column(name = "opening_name", length = 255)
    private String openingName;

    @Column(length = 100)
    private String opponent;

    /**
     * SHA-256 hash of core game data for duplicate detection.
     * Computed from: date + white + black + result + first 20 moves
     */
    @Column(name = "pgn_hash", nullable = false, length = 64)
    private String pgnHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Game() {
        this.createdAt = LocalDateTime.now();
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

    public LocalDateTime getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(LocalDateTime playedAt) {
        this.playedAt = playedAt;
    }

    public GameResult getResult() {
        return result;
    }

    public void setResult(GameResult result) {
        this.result = result;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public String getTimeControlRaw() {
        return timeControlRaw;
    }

    public void setTimeControlRaw(String timeControlRaw) {
        this.timeControlRaw = timeControlRaw;
    }

    public TimeControlCategory getTimeControlCategory() {
        return timeControlCategory;
    }

    public void setTimeControlCategory(TimeControlCategory timeControlCategory) {
        this.timeControlCategory = timeControlCategory;
    }

    public String getEcoCode() {
        return ecoCode;
    }

    public void setEcoCode(String ecoCode) {
        this.ecoCode = ecoCode;
    }

    public String getOpeningName() {
        return openingName;
    }

    public void setOpeningName(String openingName) {
        this.openingName = openingName;
    }

    public String getOpponent() {
        return opponent;
    }

    public void setOpponent(String opponent) {
        this.opponent = opponent;
    }

    public String getPgnHash() {
        return pgnHash;
    }

    public void setPgnHash(String pgnHash) {
        this.pgnHash = pgnHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
