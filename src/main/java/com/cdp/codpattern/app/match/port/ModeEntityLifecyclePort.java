package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.EntityLifecycleContext;
import com.cdp.codpattern.app.match.model.RoomId;
import net.minecraft.world.entity.Entity;

public interface ModeEntityLifecyclePort extends ModeRoomIdentityPort {
    void onRoomEntityRemoved(Entity entity, EntityLifecycleContext context);

    void onRoomEntitiesCleared(RoomId roomId);
}
