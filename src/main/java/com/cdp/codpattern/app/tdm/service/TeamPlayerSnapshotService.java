package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class TeamPlayerSnapshotService {
    public record TeamRoster(String name, List<UUID> playerIds) {
    }

    private TeamPlayerSnapshotService() {
    }

    public static Map<String, List<PlayerInfo>> buildTeamPlayers(List<TeamRoster> teams,
            Function<UUID, Player> playerLookup,
            Map<UUID, Boolean> readyStates,
            Map<UUID, Integer> playerKills,
            Map<UUID, Integer> playerDeaths,
            Map<UUID, Integer> maxKillStreaks,
            Set<UUID> respawningPlayers,
            Set<UUID> invinciblePlayers) {
        return buildTeamPlayers(teams, playerLookup, readyStates, playerKills, playerDeaths, maxKillStreaks,
                respawningPlayers, invinciblePlayers, id -> null);
    }

    public static Map<String, List<PlayerInfo>> buildTeamPlayers(List<TeamRoster> teams,
            Function<UUID, Player> playerLookup,
            Map<UUID, Boolean> readyStates,
            Map<UUID, Integer> playerKills,
            Map<UUID, Integer> playerDeaths,
            Map<UUID, Integer> maxKillStreaks,
            Set<UUID> respawningPlayers,
            Set<UUID> invinciblePlayers,
            Function<UUID, String> offlineMemberName) {
        Map<String, List<PlayerInfo>> result = new HashMap<>();

        for (TeamRoster team : teams) {
            List<PlayerInfo> playerInfos = new ArrayList<>();

            for (UUID playerId : team.playerIds()) {
                Player player = playerLookup.apply(playerId);
                ServerPlayer online = player instanceof ServerPlayer joined ? joined : null;
                String name = online == null ? offlineMemberName.apply(playerId) : online.getName().getString();
                if (name != null) {
                    playerInfos.add(new PlayerInfo(playerId, name,
                            readyStates.getOrDefault(playerId, false), playerKills.getOrDefault(playerId, 0),
                            playerDeaths.getOrDefault(playerId, 0), maxKillStreaks.getOrDefault(playerId, 0),
                            online != null && online.gameMode.getGameModeForPlayer() == GameType.ADVENTURE
                                    && !respawningPlayers.contains(playerId),
                            online != null && invinciblePlayers.contains(playerId),
                            online == null ? -1 : Math.max(0, online.latency)));
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
            result.put(team.name(), playerInfos);
        }

        return result;
    }
}
