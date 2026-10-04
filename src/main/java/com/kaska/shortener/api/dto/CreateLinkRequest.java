package com.kaska.shortener.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a short link.
 *
 * @param url           destination, must be absolute http/https
 * @param alias         optional custom code; a random one is generated when absent
 * @param expiresInDays optional lifetime; the link never expires when absent
 */
public record CreateLinkRequest(
        @NotBlank(message = "is required")
        @Size(max = 2048, message = "must be 2048 characters or fewer")
        String url,

        @Size(min = 3, max = 32, message = "must be between 3 and 32 characters")
        String alias,

        @Min(value = 1, message = "must be at least 1")
        @Max(value = 3650, message = "must be 3650 or fewer")
        Integer expiresInDays) {
}
