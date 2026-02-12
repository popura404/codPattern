package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class TeamPlayerSnapshotService {
    private TeamPlayerSnapshotService() {
    }

    public static Map<String, List<PlayerInfo>> buildTeamPlayers(List<BaseTeam> teams,
            Function<UUID, Player> playerLookup,
            Map<UUID, Boolean> readyStates,
            Map<UUID, Integer> playerKills,
            Map<UUID, Integer> playerDeaths,
            Set<UUID> respawningPlayers) {
        Map<String, List<PlayerInfo>> result = new HashMap<>();

        for (BaseTeam team : teams) {
            List<PlayerInfo> playerInfos = new ArrayList<>();

            for (UUID playerId : team.getPlayerList()) {
                Player player = playerLookup.apply(playerId);
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    PlayerInfo info = new PlayerInfo(
                            playerId,
                            serverPlayer.getName().getString(),
                            readyStates.getOrDefault(playerId, false),
                            playerKills.getOrDefault(playerId, 0),
                            playerDeaths.getOrDefault(playerId, 0),
                            !respawningPlayers.contains(playerId),
                            Math.max(0, serverPlayer.latency)
                    );
                    playerInfos.add(info);
                }
            }

            playerInfos.sort((a, b) -> {
                int byKills = Integer.compare(b.kills(), a.kills());
                if (byKills != 0) {
                    return byKills;
                }
                int byDeaths = Integer.compare(a.deaths(), b.deaths());
                if (byDeaths != 0) {
                    return byDeaths;
                }
                return a.name().compareToIgnoreCase(b.name());
            });
            result.put(team.name, playerInfos);
        }

        return result;
    }
}
