package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;
import java.util.function.Supplier;

final record CodTdmPlayerDeathHooksMapPortAdapter(
        Consumer<ServerPlayer> clearPlayerInventoryAction,
        Supplier<ServerLevel> serverLevelSupplier,
        Consumer<ServerPlayer> scheduleRespawnAction
) implements CodTdmPlayerDeathHooksPort {

    @Override
    public void clearPlayerInventory(ServerPlayer player) {
        clearPlayerInventoryAction.accept(player);
    }

    @Override
    public ServerLevel serverLevel() {
        return serverLevelSupplier.get();
    }

    @Override
    public void scheduleRespawn(ServerPlayer player) {
        scheduleRespawnAction.accept(player);
    }
}
