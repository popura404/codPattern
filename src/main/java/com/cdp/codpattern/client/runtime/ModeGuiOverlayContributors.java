package com.cdp.codpattern.client.runtime;

import com.cdp.codpattern.client.extension.ModeGuiOverlayContributor;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Deterministic client overlay contribution registry. */
public final class ModeGuiOverlayContributors {
    private static final List<ModeGuiOverlayContributor> CONTRIBUTORS = new ArrayList<>();

    private ModeGuiOverlayContributors() {
    }

    public static synchronized void register(ModeGuiOverlayContributor contributor) {
        Objects.requireNonNull(contributor, "contributor");
        if (CONTRIBUTORS.stream().anyMatch(existing -> existing.id().equals(contributor.id()))) {
            throw new IllegalStateException("Duplicate GUI overlay contributor: " + contributor.id());
        }
        CONTRIBUTORS.add(contributor);
        CONTRIBUTORS.sort(Comparator.comparingInt(ModeGuiOverlayContributor::order)
                .thenComparing(ModeGuiOverlayContributor::id));
    }

    public static synchronized void registerAll(RegisterGuiOverlaysEvent event) {
        CONTRIBUTORS.forEach(contributor -> contributor.register(event));
    }
}
