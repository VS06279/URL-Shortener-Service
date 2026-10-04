package com.kaska.shortener.api.dto;

/** Consistent error body returned for every failure. */
public record ErrorResponse(int status, String error, String message) {
}
