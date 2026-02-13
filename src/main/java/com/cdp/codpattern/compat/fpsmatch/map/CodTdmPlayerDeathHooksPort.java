package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

interface CodTdmPlayerDeathHooksPort {
    void clearPlayerInventory(ServerPlayer player);

    ServerLevel serverLevel();

    void scheduleRespawn(ServerPlayer player);
}
