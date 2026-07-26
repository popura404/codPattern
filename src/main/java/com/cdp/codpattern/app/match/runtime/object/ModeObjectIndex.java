package com.cdp.codpattern.app.match.runtime.object;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Insertion-ordered object index with normalized non-blank identities. */
public final class ModeObjectIndex<T> {
    private final Map<String, T> entries = new LinkedHashMap<>();

    public Optional<T> get(String objectId) {
        return Optional.ofNullable(entries.get(normalize(objectId)));
    }

    public Optional<T> put(String objectId, T value) {
        Objects.requireNonNull(value, "value");
        return Optional.ofNullable(entries.put(normalize(objectId), value));
    }

    public void reset(Map<String, ? extends T> replacements) {
        entries.clear();
        if (replacements != null) {
            replacements.forEach(this::put);
        }
    }

    public List<String> objectIds() {
        return List.copyOf(entries.keySet());
    }

    public Map<String, T> snapshot() {
        return Map.copyOf(entries);
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    private static String normalize(String objectId) {
        String normalized = Objects.requireNonNullElse(objectId, "").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("objectId must not be blank");
        }
        return normalized;
    }
}
