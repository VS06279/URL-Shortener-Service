package com.kaska.shortener.store;

import com.kaska.shortener.domain.ClickEvent;
import com.kaska.shortener.domain.Link;
import java.util.List;
import java.util.Optional;

/**
 * Storage boundary for links and their analytics.
 *
 * <p>The service layer only talks to this interface, so the in-memory implementation
 * can be swapped for JDBC/JPA (links) plus Redis (counters) without touching the API.
 */
public interface LinkRepository {

    /** Stores the link. Returns false if the code is already taken. */
    boolean save(Link link);

    Optional<Link> findByCode(String code);

    List<Link> findByClientId(String clientId);

    void recordClick(String code, ClickEvent event);

    Optional<LinkStats> statsFor(String code);

    boolean delete(String code);
}
