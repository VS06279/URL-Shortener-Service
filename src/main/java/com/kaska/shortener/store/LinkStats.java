package com.kaska.shortener.store;

import com.kaska.shortener.domain.ClickEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rolled-up analytics for one short link.
 *
 * <p>Counters are aggregated on write instead of storing every raw event, so memory
 * stays bounded and a stats read is O(days) rather than O(clicks). Every collection
 * here is capped: a link that goes viral cannot grow the map without limit.
 */
public class LinkStats {

    private static final int MAX_TRACKED_DAYS = 90;
    private static final int MAX_TRACKED_REFERRERS = 50;
    private static final int MAX_TRACKED_VISITORS = 50_000;

    private final AtomicLong totalClicks = new AtomicLong();
    private final Map<LocalDate, AtomicLong> clicksByDay = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> clicksByReferrer = new ConcurrentHashMap<>();
    private final Set<String> uniqueVisitors = ConcurrentHashMap.newKeySet();

    private volatile Instant lastClickAt;

    public void record(ClickEvent event) {
        totalClicks.incrementAndGet();
        lastClickAt = event.at();

        LocalDate day = event.at().atZone(ZoneOffset.UTC).toLocalDate();
        increment(clicksByDay, day, MAX_TRACKED_DAYS);

        String referrer = event.referrerHost() == null ? "direct" : event.referrerHost();
        increment(clicksByReferrer, referrer, MAX_TRACKED_REFERRERS);

        if (event.visitorHash() != null && uniqueVisitors.size() < MAX_TRACKED_VISITORS) {
            uniqueVisitors.add(event.visitorHash());
        }
    }

    private <K> void increment(Map<K, AtomicLong> counters, K key, int maxKeys) {
        AtomicLong counter = counters.get(key);
        if (counter == null) {
            if (counters.size() >= maxKeys) {
                return; // cap reached: drop the long tail rather than grow unbounded
            }
            counter = counters.computeIfAbsent(key, unused -> new AtomicLong());
        }
        counter.incrementAndGet();
    }

    public long totalClicks() {
        return totalClicks.get();
    }

    public long uniqueVisitors() {
        return uniqueVisitors.size();
    }

    public Instant lastClickAt() {
        return lastClickAt;
    }

    /** Clicks per UTC day, oldest first. */
    public Map<String, Long> clicksByDay() {
        Map<String, Long> out = new LinkedHashMap<>();
        clicksByDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> out.put(entry.getKey().toString(), entry.getValue().get()));
        return out;
    }

    /** Referrer hosts sorted by click count, highest first. */
    public Map<String, Long> topReferrers(int limit) {
        Map<String, Long> out = new LinkedHashMap<>();
        clicksByReferrer.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(limit)
                .forEach(entry -> out.put(entry.getKey(), entry.getValue().get()));
        return out;
    }
}
