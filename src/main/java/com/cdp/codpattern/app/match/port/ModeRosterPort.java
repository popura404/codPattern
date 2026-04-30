package com.cdp.codpattern.app.match.port;

import net.minecraft.server.level.ServerPlayer;

public interface ModeRosterPort {
    void requestRosterResync(ServerPlayer player);

    void requestRosterPreview(ServerPlayer player);
}
