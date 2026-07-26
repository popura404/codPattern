package com.cdp.codpattern.app.match.runtime.entity;

import com.cdp.codpattern.app.match.extension.ModeEntityReconciliationContributor;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic callback route for missing tracked entities. */
public final class ModeEntityReconciliationRouter {
    private final Map<String, ModeEntityReconciliationContributor> contributors = new LinkedHashMap<>();

    public ModeEntityReconciliationRouter() {
    }

    public ModeEntityReconciliationRouter(Collection<? extends ModeEntityReconciliationContributor> contributors) {
        if (contributors != null) {
            contributors.forEach(this::register);
        }
    }

    public synchronized void register(ModeEntityReconciliationContributor contributor) {
        Objects.requireNonNull(contributor, "contributor");
        String id = normalizeId(contributor.id());
        ModeEntityReconciliationContributor existing = contributors.putIfAbsent(id, contributor);
        if (existing != null && existing != contributor) {
            throw new IllegalStateException("Duplicate entity-reconciliation contributor: " + id);
        }
    }

    public boolean onMissingEntity(ModeEntityOwnershipRegistry.Entry entry) {
        if (entry == null || entry.roomId() == null) {
            return false;
        }
        boolean handled = false;
        for (ModeEntityReconciliationContributor contributor : snapshot()) {
            if (contributor.supports(entry.roomId())) {
                contributor.onMissingEntity(entry);
                handled = true;
            }
        }
        return handled;
    }

    public synchronized List<ModeEntityReconciliationContributor> snapshot() {
        List<ModeEntityReconciliationContributor> ordered = new ArrayList<>(contributors.values());
        ordered.sort(Comparator.comparingInt(ModeEntityReconciliationContributor::order)
                .thenComparing(contributor -> normalizeId(contributor.id())));
        return List.copyOf(ordered);
    }

    private static String normalizeId(String id) {
        String normalized = Objects.requireNonNullElse(id, "").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("contributor id must not be blank");
        }
        return normalized;
    }
}
