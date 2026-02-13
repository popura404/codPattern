package com.cdp.codpattern.compat.fpsmatch.map;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

final class CodTdmEndTeleportRuntime {
    private final CodTdmMapSetupState mapSetupState;
    private final BiConsumer<ServerPlayer, SpawnPointData> teleportToPointAction;

    CodTdmEndTeleportRuntime(
            CodTdmMapSetupState mapSetupState,
            BiConsumer<ServerPlayer, SpawnPointData> teleportToPointAction
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
        teleportToPointAction.accept(player, getMatchEndTeleportPoint());
        return true;
    }
}
