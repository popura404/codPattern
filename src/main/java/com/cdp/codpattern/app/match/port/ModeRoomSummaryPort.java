package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.RoomSummaryMetric;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;

import java.util.List;

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
}
