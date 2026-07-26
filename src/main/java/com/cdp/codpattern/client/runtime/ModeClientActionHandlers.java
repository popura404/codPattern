package com.cdp.codpattern.client.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Opaque client action routing so common packet bridges never type addon payloads or screens. */
public final class ModeClientActionHandlers {
    private static final Map<String, Consumer<Object>> HANDLERS = new LinkedHashMap<>();

    private ModeClientActionHandlers() {
    }

    public static synchronized void register(String actionId, Consumer<Object> handler) {
        String normalizedActionId = normalize(actionId);
        Objects.requireNonNull(handler, "handler");
        if (HANDLERS.putIfAbsent(normalizedActionId, handler) != null) {
            throw new IllegalStateException("Duplicate mode client action handler: " + normalizedActionId);
        }
    }

    public static synchronized boolean dispatch(String actionId, Object payload) {
        Consumer<Object> handler = HANDLERS.get(normalize(actionId));
        if (handler == null) {
            return false;
        }
        handler.accept(payload);
        return true;
    }

    private static String normalize(String actionId) {
        String normalized = Objects.requireNonNullElse(actionId, "").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("actionId must not be blank");
        }
        return normalized;
    }
}
