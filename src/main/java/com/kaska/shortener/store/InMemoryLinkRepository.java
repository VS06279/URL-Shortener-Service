package com.kaska.shortener.store;

import com.kaska.shortener.domain.ClickEvent;
import com.kaska.shortener.domain.Link;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * Concurrent in-memory store. Reads and writes are lock-free at the map level and
 * per-link stats use atomic counters, so redirect throughput is not gated on a
 * shared lock.
 */
@Repository
public class InMemoryLinkRepository implements LinkRepository {

    private final Map<String, Link> linksByCode = new ConcurrentHashMap<>();
    private final Map<String, LinkStats> statsByCode = new ConcurrentHashMap<>();

    @Override
    public boolean save(Link link) {
        boolean stored = linksByCode.putIfAbsent(link.code(), link) == null;
        if (stored) {
            statsByCode.put(link.code(), new LinkStats());
        }
        return stored;
    }

    @Override
    public Optional<Link> findByCode(String code) {
        return Optional.ofNullable(linksByCode.get(code));
    }

    @Override
    public List<Link> findByClientId(String clientId) {
        return linksByCode.values().stream()
                .filter(link -> link.ownedBy(clientId))
                .sorted(Comparator.comparing(Link::createdAt).reversed())
                .toList();
    }

    @Override
    public void recordClick(String code, ClickEvent event) {
        LinkStats stats = statsByCode.get(code);
        if (stats != null) {
            stats.record(event);
        }
    }

    @Override
    public Optional<LinkStats> statsFor(String code) {
        return Optional.ofNullable(statsByCode.get(code));
    }

    @Override
    public boolean delete(String code) {
        statsByCode.remove(code);
        return linksByCode.remove(code) != null;
    }
}
