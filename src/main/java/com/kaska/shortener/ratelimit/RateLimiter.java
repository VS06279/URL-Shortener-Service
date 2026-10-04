package com.kaska.shortener.ratelimit;

import com.kaska.shortener.config.ShortenerProperties;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Per-client rate limiting. Each client gets its own bucket, so one noisy caller
 * cannot consume the budget of another and latency stays predictable under load.
 */
@Component
public class RateLimiter {

    private final ShortenerProperties.RateLimit config;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(ShortenerProperties properties) {
        this.config = properties.rateLimit();
    }

    public TokenBucket.Decision check(String clientId) {
        long now = System.nanoTime();
        TokenBucket bucket = buckets.computeIfAbsent(
                clientId, unused -> new TokenBucket(config.capacity(), config.refillPerMinute(), now));
        return bucket.tryConsume(now);
    }

    public int limit() {
        return config.capacity();
    }
}
