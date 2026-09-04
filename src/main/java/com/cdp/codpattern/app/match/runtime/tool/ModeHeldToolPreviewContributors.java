package com.cdp.codpattern.app.match.runtime.tool;

import com.cdp.codpattern.app.match.extension.ModeHeldToolPreviewContributor;
import com.cdp.codpattern.app.match.ModeModules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Exactly-one held preview sync over the frozen mode catalog. */
public final class ModeHeldToolPreviewContributors {
    private ModeHeldToolPreviewContributors() {
    }

    public static void route(ServerPlayer player, ItemStack stack, boolean accessAllowed) {
        if (player == null) {
            return;
        }
        ModeHeldToolPreviewContributor active = null;
        if (accessAllowed) {
            for (ModeHeldToolPreviewContributor contributor : ModeModules.catalog().heldToolPreviewContributors()) {
                if (contributor.matches(stack)) {
                    active = contributor;
                    break;
                }
            }
        }
        if (active != null) {
            active.sync(player, stack == null ? ItemStack.EMPTY : stack);
        }
        for (ModeHeldToolPreviewContributor contributor : ModeModules.catalog().heldToolPreviewContributors()) {
            if (contributor != active) {
                contributor.clear(player);
            }
        }
    }
}
