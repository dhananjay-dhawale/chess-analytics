package com.chessanalytics.dto;

import com.chessanalytics.model.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountRequest(
    @NotNull(message = "Platform is required")
    Platform platform,

    @NotBlank(message = "Username is required")
    @Size(max = 100, message = "Username must be 100 characters or less")
    String username,

    @Size(max = 100, message = "Label must be 100 characters or less")
    String label
) {}
