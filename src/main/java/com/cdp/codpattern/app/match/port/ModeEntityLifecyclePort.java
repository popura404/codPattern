package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.EntityLifecycleContext;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import net.minecraft.world.entity.Entity;

public interface ModeEntityLifecyclePort extends ModeRoomIdentityPort {
    void onRoomEntityRemoved(Entity entity, EntityLifecycleContext context);

    default boolean onRoomEntityMissing(ModeEntityOwnershipRegistry.Entry entry, EntityLifecycleContext context) {
        return false;
    }

    void onRoomEntitiesCleared(RoomId roomId);
}
