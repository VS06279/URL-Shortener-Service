package com.kaska.shortener.domain;

import java.time.Instant;

/** A single short link owned by one client. */
public record Link(
        String code,
        String targetUrl,
        String clientId,
        Instant createdAt,
        Instant expiresAt) {

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public boolean ownedBy(String candidateClientId) {
        return clientId.equals(candidateClientId);
    }
}
