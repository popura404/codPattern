package com.cdp.codpattern.app.match.runtime.tool;

import com.cdp.codpattern.app.match.extension.ModeHeldToolPreviewContributor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Exactly-one held preview sync followed by deterministic clearing of all inactive previews. */
public final class ModeHeldToolPreviewContributors {
    private static final List<ModeHeldToolPreviewContributor> CONTRIBUTORS = new ArrayList<>();

    private ModeHeldToolPreviewContributors() {
    }

    public static synchronized void register(ModeHeldToolPreviewContributor contributor) {
        Objects.requireNonNull(contributor, "contributor");
        if (CONTRIBUTORS.stream().anyMatch(existing -> existing.id().equals(contributor.id()))) {
            throw new IllegalStateException("Duplicate held-tool preview contributor: " + contributor.id());
        }
        CONTRIBUTORS.add(contributor);
        CONTRIBUTORS.sort(Comparator.comparingInt(ModeHeldToolPreviewContributor::order)
                .thenComparing(ModeHeldToolPreviewContributor::id));
    }

    public static synchronized void route(ServerPlayer player, ItemStack stack, boolean accessAllowed) {
        if (player == null) {
            return;
        }
        ModeHeldToolPreviewContributor active = null;
        if (accessAllowed) {
            for (ModeHeldToolPreviewContributor contributor : CONTRIBUTORS) {
                if (contributor.matches(stack)) {
                    active = contributor;
                    break;
                }
            }
        }
        if (active != null) {
            active.sync(player, stack == null ? ItemStack.EMPTY : stack);
        }
        for (ModeHeldToolPreviewContributor contributor : CONTRIBUTORS) {
            if (contributor != active) {
                contributor.clear(player);
            }
        }
    }
}
