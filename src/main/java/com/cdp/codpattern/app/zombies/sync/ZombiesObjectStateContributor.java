package com.cdp.codpattern.app.zombies.sync;

import com.cdp.codpattern.app.match.model.ModeObjectState;
import com.cdp.codpattern.app.match.model.RoomId;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;

/**
 * Adds object states for one viewer; contributors append ModeObjectState entries using stable object/state keys.
 */
public interface ZombiesObjectStateContributor {
    default int order() {
        return 0;
    }

    void contribute(ZombiesObjectStateContext context, List<ModeObjectState> states);

    record ZombiesObjectStateContext(
            RoomId roomId,
            ServerPlayer viewer,
            long revision
    ) {
        public ZombiesObjectStateContext {
            Objects.requireNonNull(roomId, "roomId");
            Objects.requireNonNull(viewer, "viewer");
            revision = Math.max(0L, revision);
        }
    }
}
