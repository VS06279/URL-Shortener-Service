package com.kaska.shortener.ratelimit;

/**
 * Token bucket for a single client.
 *
 * <p>Tokens refill continuously at a fixed rate up to a burst capacity. Refill is
 * computed lazily from the clock on each call, so there is no background timer
 * thread and an idle client costs nothing.
 */
public final class TokenBucket {

    private final double capacity;
    private final double tokensPerNanosecond;

    private double tokens;
    private long lastRefillNanos;

    public TokenBucket(int capacity, int refillPerMinute, long nowNanos) {
        this.capacity = capacity;
        this.tokensPerNanosecond = refillPerMinute / 60_000_000_000.0d;
        this.tokens = capacity;
        this.lastRefillNanos = nowNanos;
    }

    /** Takes one token if available. Returns the outcome, including tokens left. */
    public synchronized Decision tryConsume(long nowNanos) {
        refill(nowNanos);
        if (tokens >= 1.0d) {
            tokens -= 1.0d;
            return new Decision(true, (long) Math.floor(tokens), 0L);
        }
        double deficit = 1.0d - tokens;
        long waitSeconds = (long) Math.ceil(deficit / (tokensPerNanosecond * 1_000_000_000.0d));
        return new Decision(false, 0L, Math.max(1L, waitSeconds));
    }

    private void refill(long nowNanos) {
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }
        tokens = Math.min(capacity, tokens + elapsed * tokensPerNanosecond);
        lastRefillNanos = nowNanos;
    }

    /** Result of a rate limit check. */
    public record Decision(boolean allowed, long remaining, long retryAfterSeconds) {
    }
}
