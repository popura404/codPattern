package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.RoomId;

public interface ModeRoomIdentityPort {
    RoomId roomId();

    String gameType();

    String mapName();

    String modeDisplayNameKey();
}
