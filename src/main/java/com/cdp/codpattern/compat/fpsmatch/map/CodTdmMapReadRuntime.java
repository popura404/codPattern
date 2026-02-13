package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final record CodTdmMapReadRuntime(
        CodTdmMatchRuntimeState matchState,
        CodTdmCombatRuntime combatRuntime,
        CodTdmRespawnRuntime respawnRuntime,
        CodTdmEndTeleportRuntime endTeleportRuntime,
        CodTdmMatchProgressRuntime matchProgressRuntime
) {

    String phaseName() {
        return matchState.phase().name();
    }

    boolean isPlayingPhase() {
        return matchState.phase() == TdmGamePhase.PLAYING;
    }

    boolean isWaitingPhase() {
        return matchState.phase() == TdmGamePhase.WAITING;
    }

    boolean canDealDamage() {
        return combatRuntime.canDealDamage();
    }

    boolean isPlayerInvincible(UUID playerId) {
        return respawnRuntime.isInvincible(playerId);
    }

    boolean hasMatchEndTeleportPoint() {
        return endTeleportRuntime.hasMatchEndTeleportPoint();
    }

    int remainingTimeTicks() {
        return matchProgressRuntime.getRemainingTimeTicks();
    }

    Map<String, Integer> teamScoresSnapshot() {
        return matchState.teamScoresSnapshot();
    }

    Optional<SpawnPointData> matchEndTeleportPoint() {
        return Optional.ofNullable(endTeleportRuntime.getMatchEndTeleportPoint());
    }
}
