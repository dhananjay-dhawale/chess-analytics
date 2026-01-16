package com.chessanalytics.dto;

import jakarta.validation.constraints.Size;

/**
 * Request to update an account's label.
 * Only label can be modified - platform and username are immutable.
 */
public record UpdateAccountRequest(
    @Size(max = 100, message = "Label must be 100 characters or less")
    String label
) {}
