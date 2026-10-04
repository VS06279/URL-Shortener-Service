package com.kaska.shortener.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Generates random base62 codes.
 *
 * <p>Random rather than sequential on purpose: sequential ids let anyone walk the
 * whole link set by counting up, which would expose other clients' destinations.
 */
@Component
public class ShortCodeGenerator {

    private static final char[] ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private final SecureRandom random = new SecureRandom();

    public String next(int length) {
        char[] code = new char[length];
        for (int i = 0; i < length; i++) {
            code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}
