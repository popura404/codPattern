package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.app.match.ModeRoomBackedMap;
import com.cdp.codpattern.app.match.port.ModeRoomReadPort;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class MatchEndTeleportRespawnService {
    private MatchEndTeleportRespawnService() {
    }

    public static boolean teleportToRandomMatchEndPoint(ServerPlayer player) {
        if (player == null || player.server == null) {
            return false;
        }

        List<MatchEndTeleportCandidate> candidates = collectCandidates();
        if (candidates.isEmpty()) {
            return false;
        }

        Collections.shuffle(candidates);
        for (MatchEndTeleportCandidate candidate : candidates) {
            if (candidate.map().teleportToPoint(player, candidate.point())) {
                return true;
            }
        }
        return false;
    }

    private static List<MatchEndTeleportCandidate> collectCandidates() {
        List<MatchEndTeleportCandidate> candidates = new ArrayList<>();
        FPSMCore.getInstance().getAllMaps().values().stream()
                .flatMap(List::stream)
                .distinct()
                .forEach(map -> readMatchEndTeleportPoint(map)
                        .ifPresent(point -> candidates.add(new MatchEndTeleportCandidate(map, point))));
        return candidates;
    }

    private static Optional<SpawnPointData> readMatchEndTeleportPoint(BaseMap map) {
        if (map instanceof ModeRoomBackedMap backedMap
                && backedMap.roomHandle().summaryPort() instanceof ModeRoomReadPort readPort) {
            return readPort.matchEndTeleportPoint();
        }
        return Optional.empty();
    }

    private record MatchEndTeleportCandidate(BaseMap map, SpawnPointData point) {
    }
}
