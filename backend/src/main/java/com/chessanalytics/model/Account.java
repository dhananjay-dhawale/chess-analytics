package com.chessanalytics.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Represents a chess account from a specific platform.
 * A user can have multiple accounts across different platforms.
 */
@Entity
@Table(
    name = "account",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"platform", "username"},
        name = "uk_account_platform_username"
    )
)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Platform platform;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    public Account() {
        this.createdAt = LocalDateTime.now();
    }

    public Account(Platform platform, String username) {
        this();
        this.platform = platform;
        this.username = username;
    }

    public Account(Platform platform, String username, String label) {
        this();
        this.platform = platform;
        this.username = username;
        this.label = label;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(LocalDateTime lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }
}
