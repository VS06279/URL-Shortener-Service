package com.kaska.shortener.service;

import com.kaska.shortener.api.ApiException;
import com.kaska.shortener.config.ShortenerProperties;
import com.kaska.shortener.domain.ClickContext;
import com.kaska.shortener.domain.ClickEvent;
import com.kaska.shortener.domain.Link;
import com.kaska.shortener.store.LinkRepository;
import com.kaska.shortener.store.LinkStats;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Creation, resolution, and analytics for short links. */
@Service
public class LinkService {

    private static final Logger log = LoggerFactory.getLogger(LinkService.class);

    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");
    private static final Set<String> RESERVED_ALIASES =
            Set.of("api", "actuator", "health", "admin", "static", "favicon.ico", "robots.txt");
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final int TOP_REFERRERS = 10;

    private final LinkRepository repository;
    private final ShortCodeGenerator codeGenerator;
    private final ShortenerProperties properties;

    /** Per-process salt so stored visitor hashes are not reversible via a rainbow table. */
    private final byte[] visitorSalt = new byte[16];

    /**
     * Click writes run off the request thread with a bounded queue. If analytics ever
     * fall behind, extra events are dropped instead of adding latency to the redirect.
     */
    private final ThreadPoolExecutor analyticsExecutor = new ThreadPoolExecutor(
            1, 2, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(10_000),
            runnable -> {
                Thread thread = new Thread(runnable, "click-analytics");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardPolicy());

    public LinkService(
            LinkRepository repository,
            ShortCodeGenerator codeGenerator,
            ShortenerProperties properties) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.properties = properties;
        new SecureRandom().nextBytes(visitorSalt);
    }

    public Link create(String clientId, String url, String alias, Integer expiresInDays) {
        String targetUrl = validateTargetUrl(url);
        Instant now = Instant.now();
        Instant expiresAt = (expiresInDays == null) ? null : now.plus(expiresInDays, ChronoUnit.DAYS);

        if (alias != null && !alias.isBlank()) {
            String requested = alias.trim();
            validateAlias(requested);
            Link link = new Link(requested, targetUrl, clientId, now, expiresAt);
            if (!repository.save(link)) {
                throw ApiException.conflict("Alias '" + requested + "' is already taken.");
            }
            return link;
        }

        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.next(properties.codeLength());
            Link link = new Link(code, targetUrl, clientId, now, expiresAt);
            if (repository.save(link)) {
                return link;
            }
        }
        throw new IllegalStateException("Could not allocate a unique short code.");
    }

    /** Returns the link only if it exists and has not expired. */
    public Optional<Link> resolve(String code) {
        return repository.findByCode(code).filter(link -> !link.isExpired(Instant.now()));
    }

    public List<Link> listFor(String clientId) {
        return repository.findByClientId(clientId);
    }

    /** Fetches a link the client owns, or 404s. Not-owned looks identical to not-found. */
    public Link requireOwned(String code, String clientId) {
        return repository.findByCode(code)
                .filter(link -> link.ownedBy(clientId))
                .orElseThrow(() -> ApiException.notFound("No link found with code '" + code + "'."));
    }

    public LinkStats statsFor(String code) {
        return repository.statsFor(code)
                .orElseThrow(() -> ApiException.notFound("No link found with code '" + code + "'."));
    }

    public void delete(String code, String clientId) {
        requireOwned(code, clientId);
        repository.delete(code);
    }

    /** Queues a click for recording. Returns immediately. */
    public void recordClick(String code, ClickContext context) {
        Instant at = Instant.now();
        analyticsExecutor.execute(() -> {
            try {
                ClickEvent event = new ClickEvent(
                        at,
                        referrerHost(context.referrer()),
                        context.userAgent(),
                        hashVisitor(context.remoteAddress()));
                repository.recordClick(code, event);
            } catch (RuntimeException exception) {
                log.warn("Failed to record click for code {}", code, exception);
            }
        });
    }

    public int topReferrerLimit() {
        return TOP_REFERRERS;
    }

    private String validateTargetUrl(String url) {
        String candidate = url.trim();
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw ApiException.badRequest("URL must start with http:// or https://.");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw ApiException.badRequest("URL must include a host.");
            }
            return uri.toString();
        } catch (URISyntaxException exception) {
            throw ApiException.badRequest("URL is not valid.");
        }
    }

    private void validateAlias(String alias) {
        if (!ALIAS_PATTERN.matcher(alias).matches()) {
            throw ApiException.badRequest(
                    "Alias may only contain letters, numbers, hyphens, and underscores.");
        }
        if (RESERVED_ALIASES.contains(alias.toLowerCase(Locale.ROOT))) {
            throw ApiException.badRequest("Alias '" + alias + "' is reserved.");
        }
    }

    private String referrerHost(String referrer) {
        if (referrer == null) {
            return null;
        }
        try {
            String host = new URI(referrer).getHost();
            return (host == null || host.isBlank()) ? null : host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private String hashVisitor(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(visitorSalt);
            byte[] hash = digest.digest(remoteAddress.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            return null;
        }
    }

    @PreDestroy
    void shutdown() {
        analyticsExecutor.shutdown();
        try {
            if (!analyticsExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                analyticsExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            analyticsExecutor.shutdownNow();
        }
    }
}
