package com.kaska.shortener.domain;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The small slice of request data captured on the redirect path.
 * It is pulled off the request synchronously (cheap) so the servlet request is
 * never touched from the background analytics thread.
 */
public record ClickContext(String referrer, String userAgent, String remoteAddress) {

    public static ClickContext from(HttpServletRequest request) {
        return new ClickContext(
                trim(request.getHeader("Referer")),
                trim(request.getHeader("User-Agent")),
                clientIp(request));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > 256 ? value.substring(0, 256) : value;
    }
}
