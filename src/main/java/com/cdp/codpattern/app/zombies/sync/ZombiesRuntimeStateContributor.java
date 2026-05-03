package com.cdp.codpattern.app.zombies.sync;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.model.RoomSummaryMetric;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adds HUD/runtime keys for one viewer; contributors append values and must not redefine existing key meanings.
 */
public interface ZombiesRuntimeStateContributor {
    default int order() {
        return 0;
    }

    void contribute(ZombiesRuntimeStateContext context, ZombiesRuntimeStateSink sink);

    record ZombiesRuntimeStateContext(
            RoomId roomId,
            ServerPlayer viewer,
            String phaseKey,
            long revision
    ) {
        public ZombiesRuntimeStateContext {
            Objects.requireNonNull(roomId, "roomId");
            Objects.requireNonNull(viewer, "viewer");
            phaseKey = Objects.requireNonNullElse(phaseKey, "").trim();
            revision = Math.max(0L, revision);
        }
    }

    record ZombiesRuntimeStateSink(
            List<RoomSummaryMetric> metrics,
            Map<String, ModePlayerValue> playerValues
    ) {
        public ZombiesRuntimeStateSink {
            Objects.requireNonNull(metrics, "metrics");
            Objects.requireNonNull(playerValues, "playerValues");
        }
    }
}
