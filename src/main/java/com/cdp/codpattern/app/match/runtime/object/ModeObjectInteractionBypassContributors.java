package com.cdp.codpattern.app.match.runtime.object;

import com.cdp.codpattern.app.match.extension.ModeObjectInteractionBypassContributor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Routes block-owned interaction bypass checks without naming mode block implementations. */
public final class ModeObjectInteractionBypassContributors {
    private static final List<ModeObjectInteractionBypassContributor> CONTRIBUTORS = new ArrayList<>();

    private ModeObjectInteractionBypassContributors() {
    }

    public static synchronized void register(ModeObjectInteractionBypassContributor contributor) {
        Objects.requireNonNull(contributor, "contributor");
        if (CONTRIBUTORS.stream().anyMatch(existing -> existing.id().equals(contributor.id()))) {
            throw new IllegalStateException("Duplicate object interaction bypass contributor: " + contributor.id());
        }
        CONTRIBUTORS.add(contributor);
        CONTRIBUTORS.sort(Comparator.comparingInt(ModeObjectInteractionBypassContributor::order)
                .thenComparing(ModeObjectInteractionBypassContributor::id));
    }

    public static synchronized boolean handlesOwnUse(BlockState state) {
        return state != null && CONTRIBUTORS.stream().anyMatch(contributor -> contributor.handlesOwnUse(state));
    }
}
