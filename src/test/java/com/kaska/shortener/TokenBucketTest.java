package com.kaska.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.kaska.shortener.ratelimit.TokenBucket;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    @Test
    void allowsUpToCapacityThenRejects() {
        long now = 0L;
        TokenBucket bucket = new TokenBucket(3, 60, now);

        assertThat(bucket.tryConsume(now).allowed()).isTrue();
        assertThat(bucket.tryConsume(now).allowed()).isTrue();
        assertThat(bucket.tryConsume(now).allowed()).isTrue();

        TokenBucket.Decision rejected = bucket.tryConsume(now);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isZero();
        assertThat(rejected.retryAfterSeconds()).isPositive();
    }

    @Test
    void refillsOverTime() {
        long start = 0L;
        TokenBucket bucket = new TokenBucket(1, 60, start);

        assertThat(bucket.tryConsume(start).allowed()).isTrue();
        assertThat(bucket.tryConsume(start).allowed()).isFalse();

        long twoSecondsLater = start + TimeUnit.SECONDS.toNanos(2);
        assertThat(bucket.tryConsume(twoSecondsLater).allowed()).isTrue();
    }

    @Test
    void neverRefillsAboveCapacity() {
        long start = 0L;
        TokenBucket bucket = new TokenBucket(2, 600, start);

        long anHourLater = start + TimeUnit.HOURS.toNanos(1);
        assertThat(bucket.tryConsume(anHourLater).allowed()).isTrue();
        assertThat(bucket.tryConsume(anHourLater).allowed()).isTrue();
        assertThat(bucket.tryConsume(anHourLater).allowed()).isFalse();
    }
}
