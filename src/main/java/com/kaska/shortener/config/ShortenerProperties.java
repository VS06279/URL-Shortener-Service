package com.kaska.shortener.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration bound from the "shortener" section of application.yml.
 * Compact constructors apply defaults so the app still boots with an empty config.
 */
@ConfigurationProperties(prefix = "shortener")
public record ShortenerProperties(
        String baseUrl,
        int codeLength,
        RateLimit rateLimit,
        Map<String, String> clients) {

    public ShortenerProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (codeLength < 4 || codeLength > 16) {
            codeLength = 7;
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(60, 60);
        }
        clients = (clients == null) ? Map.of() : Map.copyOf(clients);
    }

    public String shortUrlFor(String code) {
        return baseUrl + "/" + code;
    }

    public record RateLimit(int capacity, int refillPerMinute) {
        public RateLimit {
            if (capacity <= 0) {
                capacity = 60;
            }
            if (refillPerMinute <= 0) {
                refillPerMinute = 60;
            }
        }
    }
}
