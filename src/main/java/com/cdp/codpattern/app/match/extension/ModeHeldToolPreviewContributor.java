package com.cdp.codpattern.app.match.extension;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** One held-tool preview lifecycle owned by core or an installed mode. */
public interface ModeHeldToolPreviewContributor {
    String id();

    default int order() {
        return 0;
    }

    boolean matches(ItemStack stack);

    void sync(ServerPlayer player, ItemStack stack);

    void clear(ServerPlayer player);
}
