package com.cdp.codpattern.app.match.port;

import net.minecraft.server.level.ServerPlayer;

public interface ReadyStatePort {
    void initializeReadyState(ServerPlayer player);

    boolean setPlayerReady(ServerPlayer player, boolean ready);
}
