package com.kaska.shortener.api;

import com.kaska.shortener.config.ShortenerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the calling client from the X-API-Key header and puts its id on the
 * request. Everything downstream (rate limiting, ownership checks) keys off that id.
 *
 * <p>Keys live in configuration here. Swapping in a database or secrets manager means
 * replacing this lookup only.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID_ATTRIBUTE = "shortener.clientId";
    private static final String HEADER = "X-API-Key";

    private final Map<String, String> clientsByApiKey;

    public ApiKeyFilter(ShortenerProperties properties) {
        this.clientsByApiKey = properties.clients();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(HEADER);
        String clientId = (apiKey == null) ? null : clientsByApiKey.get(apiKey);

        if (clientId == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":401,\"error\":\"Unauthorized\","
                            + "\"message\":\"Missing or unknown X-API-Key header.\"}");
            return;
        }

        request.setAttribute(CLIENT_ID_ATTRIBUTE, clientId);
        chain.doFilter(request, response);
    }
}
