package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.ModeRoomTickContext;

public interface ModeRoomTickPort extends ModeRoomIdentityPort {
    void tick(ModeRoomTickContext context);
}
