package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.JoinRoomRequest;
import com.cdp.codpattern.app.match.model.JoinRoomResult;
import com.cdp.codpattern.app.match.model.LeaveRoomResult;
import net.minecraft.server.level.ServerPlayer;

public interface ModeRoomLifecyclePort extends ModeRoomIdentityPort {
    JoinRoomResult join(ServerPlayer player, JoinRoomRequest request);

    LeaveRoomResult leave(ServerPlayer player);

    void syncToClient();
}
