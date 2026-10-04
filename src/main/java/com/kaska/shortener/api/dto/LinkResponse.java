package com.kaska.shortener.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kaska.shortener.domain.Link;
import java.time.Instant;

/** Public view of a short link. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LinkResponse(
        String code,
        String shortUrl,
        String targetUrl,
        Instant createdAt,
        Instant expiresAt) {

    public static LinkResponse from(Link link, String shortUrl) {
        return new LinkResponse(
                link.code(), shortUrl, link.targetUrl(), link.createdAt(), link.expiresAt());
    }
}
