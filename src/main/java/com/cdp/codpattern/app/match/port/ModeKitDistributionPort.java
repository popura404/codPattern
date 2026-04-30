package com.cdp.codpattern.app.match.port;

import net.minecraft.server.level.ServerPlayer;

public interface ModeKitDistributionPort {
    void onBackpackSelected(ServerPlayer player, int backpackId);
}
