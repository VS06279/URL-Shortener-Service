package com.kaska.shortener.api;

import com.kaska.shortener.api.dto.CreateLinkRequest;
import com.kaska.shortener.api.dto.LinkResponse;
import com.kaska.shortener.api.dto.StatsResponse;
import com.kaska.shortener.config.ShortenerProperties;
import com.kaska.shortener.domain.Link;
import com.kaska.shortener.service.LinkService;
import com.kaska.shortener.store.LinkStats;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Management API. Every endpoint is scoped to the calling client. */
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private final LinkService linkService;
    private final ShortenerProperties properties;

    public LinkController(LinkService linkService, ShortenerProperties properties) {
        this.linkService = linkService;
        this.properties = properties;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(
            @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) String clientId,
            @Valid @RequestBody CreateLinkRequest request) {

        Link link = linkService.create(
                clientId, request.url(), request.alias(), request.expiresInDays());
        String shortUrl = properties.shortUrlFor(link.code());

        return ResponseEntity.created(URI.create(shortUrl))
                .body(LinkResponse.from(link, shortUrl));
    }

    @GetMapping
    public List<LinkResponse> list(
            @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) String clientId) {

        return linkService.listFor(clientId).stream()
                .map(link -> LinkResponse.from(link, properties.shortUrlFor(link.code())))
                .toList();
    }

    @GetMapping("/{code}")
    public LinkResponse get(
            @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) String clientId,
            @PathVariable String code) {

        Link link = linkService.requireOwned(code, clientId);
        return LinkResponse.from(link, properties.shortUrlFor(link.code()));
    }

    @GetMapping("/{code}/stats")
    public StatsResponse stats(
            @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) String clientId,
            @PathVariable String code) {

        Link link = linkService.requireOwned(code, clientId);
        LinkStats stats = linkService.statsFor(code);

        return new StatsResponse(
                link.code(),
                properties.shortUrlFor(link.code()),
                link.targetUrl(),
                link.createdAt(),
                stats.totalClicks(),
                stats.uniqueVisitors(),
                stats.lastClickAt(),
                stats.clicksByDay(),
                stats.topReferrers(linkService.topReferrerLimit()));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(
            @RequestAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE) String clientId,
            @PathVariable String code) {

        linkService.delete(code, clientId);
        return ResponseEntity.noContent().build();
    }
}
