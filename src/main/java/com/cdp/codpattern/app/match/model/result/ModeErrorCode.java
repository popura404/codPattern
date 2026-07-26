package com.cdp.codpattern.app.match.model.result;

import java.util.Objects;

/** Stable value-based error identity for public mode operations. */
public final class ModeErrorCode {
    public static final ModeErrorCode OK = new ModeErrorCode("ok");

    private final String key;

    private ModeErrorCode(String key) {
        this.key = normalize(key);
    }

    public static ModeErrorCode of(String key) {
        String normalized = normalize(key);
        return OK.key.equals(normalized) ? OK : new ModeErrorCode(normalized);
    }

    public String key() {
        return key;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ModeErrorCode that && key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return key;
    }

    private static String normalize(String key) {
        String normalized = Objects.requireNonNullElse(key, "").trim();
        return normalized.isEmpty() ? "ok" : normalized;
    }
}
