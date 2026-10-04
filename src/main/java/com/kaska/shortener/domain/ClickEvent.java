package com.kaska.shortener.domain;

import java.time.Instant;

/**
 * A recorded click. The visitor is stored as a salted hash rather than a raw IP,
 * so unique-visitor counts work without retaining personal data.
 */
public record ClickEvent(Instant at, String referrerHost, String userAgent, String visitorHash) {
}
