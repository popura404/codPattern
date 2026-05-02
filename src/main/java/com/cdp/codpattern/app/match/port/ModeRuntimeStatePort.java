package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;
import net.minecraft.server.level.ServerPlayer;

public interface ModeRuntimeStatePort extends ModeRoomIdentityPort {
    ModeRuntimeStateSnapshot runtimeStateSnapshot(ServerPlayer viewer);
}
