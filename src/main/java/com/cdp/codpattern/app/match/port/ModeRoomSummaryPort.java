package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.RoomSummaryMetric;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;

import java.util.List;
import java.util.Optional;

public interface ModeRoomSummaryPort extends ModeRoomIdentityPort {
    String lifecycleStateKey();

    boolean isJoinable();

    boolean isRunning();

    int playerCount();

    int maxPlayers();

    int remainingTimeTicks();

    List<RoomSummaryMetric> metrics();

    AreaData mapArea();

    String dimensionId();

    /** Whether this mode can inspect its configured match-end teleport point. */
    default boolean supportsConfiguredEndPoint() {
        return false;
    }

    /** The configured point, if present; this never supplies a recovery fallback. */
    default Optional<SpawnPointData> configuredEndPoint() {
        return Optional.empty();
    }
}
