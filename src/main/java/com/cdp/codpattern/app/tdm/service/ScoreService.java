package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;
import com.cdp.codpattern.app.tdm.model.TdmTeamNames;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.network.tdm.ScoreUpdatePacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class ScoreService {
    public interface Hooks {
        Optional<String> findTeamNameByPlayer(ServerPlayer player);

        void broadcastScoreUpdate(ScoreUpdatePacket packet);

        void markRoomListDirty();
    }

    private ScoreService() {
    }

    public static boolean hasReachedVictoryGoal(TdmGamePhase phase,
            int gameTimeTicks,
            Map<String, Integer> teamScores,
            CodTdmConfig config) {
        if (phase != TdmGamePhase.PLAYING) {
            return false;
        }

        if (gameTimeTicks >= config.getTimeLimitSeconds() * 20) {
            return true;
        }

        for (Integer score : teamScores.values()) {
            if (score >= config.getScoreLimit()) {
                return true;
            }
        }

        return false;
    }

    public static int tickPlaying(int gameTimeTicks,
            Map<String, Integer> teamScores,
            Consumer<ScoreUpdatePacket> scoreBroadcaster) {
        int nextGameTimeTicks = gameTimeTicks + 1;
        if (nextGameTimeTicks % 20 == 0) {
            scoreBroadcaster.accept(new ScoreUpdatePacket(teamScores, nextGameTimeTicks));
        }
        return nextGameTimeTicks;
    }

    public static void onPlayerKill(ServerPlayer killer,
            ServerPlayer victim,
            TdmGamePhase phase,
            Map<UUID, Integer> playerKills,
            Map<UUID, Integer> playerDeaths,
            Map<UUID, Integer> currentKillStreaks,
            Map<UUID, Integer> maxKillStreaks,
            Map<String, Integer> teamScores,
            int gameTimeTicks,
            Hooks hooks) {
        if (phase != TdmGamePhase.PLAYING || killer == null || victim == null) {
            return;
        }

        playerKills.merge(killer.getUUID(), 1, Integer::sum);
        playerDeaths.merge(victim.getUUID(), 1, Integer::sum);

        int killerStreak = currentKillStreaks.merge(killer.getUUID(), 1, Integer::sum);
        int currentMax = maxKillStreaks.getOrDefault(killer.getUUID(), 0);
        if (killerStreak > currentMax) {
            maxKillStreaks.put(killer.getUUID(), killerStreak);
        }
        currentKillStreaks.put(victim.getUUID(), 0);

        hooks.findTeamNameByPlayer(killer).ifPresent(teamName -> {
            teamScores.merge(teamName, 1, Integer::sum);
            hooks.broadcastScoreUpdate(new ScoreUpdatePacket(teamScores, gameTimeTicks));
        });

        hooks.markRoomListDirty();
    }

    public static void onNonPlayerDeath(ServerPlayer victim,
            ServerPlayer killer,
            TdmGamePhase phase,
            Map<UUID, Integer> playerDeaths,
            Map<UUID, Integer> currentKillStreaks,
            Map<String, Integer> teamScores,
            int gameTimeTicks,
            Hooks hooks) {
        if (phase != TdmGamePhase.PLAYING || victim == null) {
            return;
        }

        // 仅在“非其他玩家击杀”的死亡场景触发：
        // killer 为空（环境伤害）或 killer 就是死者本人（自杀/反伤等）。
        if (killer != null && !killer.getUUID().equals(victim.getUUID())) {
            return;
        }

        playerDeaths.merge(victim.getUUID(), 1, Integer::sum);
        currentKillStreaks.put(victim.getUUID(), 0);

        hooks.findTeamNameByPlayer(victim)
                .flatMap(victimTeam -> findOpponentTeam(victimTeam, teamScores))
                .ifPresent(opponentTeam -> {
                    teamScores.merge(opponentTeam, 1, Integer::sum);
                    hooks.broadcastScoreUpdate(new ScoreUpdatePacket(teamScores, gameTimeTicks));
                });

        hooks.markRoomListDirty();
    }

    private static Optional<String> findOpponentTeam(String victimTeam, Map<String, Integer> teamScores) {
        if (teamScores.containsKey(TdmTeamNames.KORTAC) && teamScores.containsKey(TdmTeamNames.SPECGRU)) {
            if (TdmTeamNames.KORTAC.equals(victimTeam)) {
                return Optional.of(TdmTeamNames.SPECGRU);
            }
            if (TdmTeamNames.SPECGRU.equals(victimTeam)) {
                return Optional.of(TdmTeamNames.KORTAC);
            }
        }
        return teamScores.keySet().stream()
                .filter(teamName -> !teamName.equals(victimTeam))
                .sorted()
                .findFirst();
    }
}
