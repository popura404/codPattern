package com.cdp.codpattern.app.match.runtime.object;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Type-keyed interaction dispatch without mode-specific branches. */
public final class ModeObjectInteractionDispatcher<K, C, R> {
    private final Map<K, Handler<C, R>> handlers = new LinkedHashMap<>();

    public void register(K type, Handler<C, R> handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(type, handler) != null) {
            throw new IllegalStateException("Duplicate object interaction handler: " + type);
        }
    }

    public Optional<R> dispatch(K type, C context) {
        Handler<C, R> handler = handlers.get(type);
        return handler == null ? Optional.empty() : Optional.ofNullable(handler.handle(context));
    }

    public int size() {
        return handlers.size();
    }

    @FunctionalInterface
    public interface Handler<C, R> {
        R handle(C context);
    }
}
