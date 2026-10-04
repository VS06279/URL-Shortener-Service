package com.kaska.shortener.api;

import com.kaska.shortener.domain.ClickContext;
import com.kaska.shortener.domain.Link;
import com.kaska.shortener.service.LinkService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public redirect endpoint. This is the hot path, so it does one map lookup, hands
 * the click off to a background thread, and returns.
 *
 * <p>302 rather than 301 on purpose: browsers cache a permanent redirect forever,
 * which would silently stop click tracking and make the link impossible to retarget.
 */
@RestController
public class RedirectController {

    private final LinkService linkService;

    public RedirectController(LinkService linkService) {
        this.linkService = linkService;
    }

    @GetMapping("/{code:[A-Za-z0-9_-]{3,32}}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        Link link = linkService.resolve(code)
                .orElseThrow(() -> ApiException.notFound("This link does not exist or has expired."));

        linkService.recordClick(code, ClickContext.from(request));

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(link.targetUrl()))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
