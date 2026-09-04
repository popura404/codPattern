package com.cdp.codpattern.app.match;

import com.cdp.codpattern.app.match.extension.ModeModule;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Thread-safe construction-time collector with a one-way freeze lifecycle. */
public final class ModeModuleCollector {
    private final Map<ResourceLocation, ModeModule> contributions = new LinkedHashMap<>();
    private ModeCatalog catalog;
    private RuntimeException freezeFailure;
    private boolean freezing;

    public synchronized void contribute(ModeModule module) {
        Objects.requireNonNull(module, "module");
        ResourceLocation id = Objects.requireNonNull(module.id(), "mode module id");
        if (freezing) {
            throw new IllegalStateException("Mode module collection is already frozen: " + id);
        }
        ModeModule existing = contributions.get(id);
        if (existing == module) {
            return;
        }
        if (existing != null) {
            throw new IllegalStateException("Different mode module instance already uses id: " + id);
        }
        contributions.put(id, module);
    }

    public synchronized ModeCatalog freeze() {
        if (catalog != null) {
            return catalog;
        }
        if (freezeFailure != null) {
            throw freezeFailure;
        }
        freezing = true;
        try {
            catalog = ModeCatalog.create(contributions.values());
            return catalog;
        } catch (RuntimeException failure) {
            freezeFailure = failure;
            throw failure;
        }
    }

    public synchronized ModeCatalog catalog() {
        if (catalog != null) {
            return catalog;
        }
        if (freezeFailure != null) {
            throw new IllegalStateException("Mode catalog freeze failed", freezeFailure);
        }
        throw new IllegalStateException("Mode catalog is not frozen; read it after FML common setup");
    }

    public synchronized int contributionCount() {
        return contributions.size();
    }
}
