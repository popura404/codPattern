package com.cdp.codpattern.compat.fpsmatch.map;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiPredicate;

final class CodTdmEndTeleportRuntime {
    private final CodTdmMapSetupState mapSetupState;
    private final BiPredicate<ServerPlayer, SpawnPointData> teleportToPointAction;

    CodTdmEndTeleportRuntime(
            CodTdmMapSetupState mapSetupState,
            BiPredicate<ServerPlayer, SpawnPointData> teleportToPointAction
    ) {
        this.mapSetupState = mapSetupState;
        this.teleportToPointAction = teleportToPointAction;
    }

    boolean hasMatchEndTeleportPoint() {
        return mapSetupState.hasMatchEndTeleportPoint();
    }

    SpawnPointData getMatchEndTeleportPoint() {
        return mapSetupState.getMatchEndTeleportPoint();
    }

    void setMatchEndTeleportPoint(SpawnPointData point) {
        mapSetupState.setMatchEndTeleportPoint(point);
    }

    boolean teleportPlayerToMatchEndPoint(ServerPlayer player) {
        if (!hasMatchEndTeleportPoint()) {
            return false;
        }
        return teleportToPointAction.test(player, getMatchEndTeleportPoint());
    }
}
