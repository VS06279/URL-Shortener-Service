package com.kaska.shortener.ratelimit;

import com.kaska.shortener.api.ApiKeyFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies the per-client token bucket to the management API. Runs after
 * {@link ApiKeyFilter}, so the client id is already resolved.
 *
 * <p>Public redirects are deliberately not throttled here: they are the read path
 * that end users hit, and they are cheap.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Object clientId = request.getAttribute(ApiKeyFilter.CLIENT_ID_ATTRIBUTE);
        if (clientId == null) {
            chain.doFilter(request, response); // unauthenticated: already rejected upstream
            return;
        }

        TokenBucket.Decision decision = rateLimiter.check(clientId.toString());
        response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimiter.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (!decision.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\","
                            + "\"message\":\"Rate limit exceeded. Retry in "
                            + decision.retryAfterSeconds() + "s.\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
