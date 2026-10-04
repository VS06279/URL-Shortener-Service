package com.kaska.shortener.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/** Click analytics for one short link. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatsResponse(
        String code,
        String shortUrl,
        String targetUrl,
        Instant createdAt,
        long totalClicks,
        long uniqueVisitors,
        Instant lastClickAt,
        Map<String, Long> clicksByDay,
        Map<String, Long> topReferrers) {
}
