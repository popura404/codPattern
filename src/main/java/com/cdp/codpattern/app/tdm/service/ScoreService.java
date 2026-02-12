package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.cdp.codpattern.network.tdm.ScoreUpdatePacket;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class ScoreService {
    public interface Hooks {
        Optional<BaseTeam> findTeamByPlayer(ServerPlayer player);

        void broadcastScoreUpdate(ScoreUpdatePacket packet);

        void markRoomListDirty();
    }

    private ScoreService() {
    }

    public static boolean hasReachedVictoryGoal(CodTdmMap.GamePhase phase,
            int gameTimeTicks,
            Map<String, Integer> teamScores,
            CodTdmConfig config) {
        if (phase != CodTdmMap.GamePhase.PLAYING) {
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
            CodTdmMap.GamePhase phase,
            Map<UUID, Integer> playerKills,
            Map<UUID, Integer> playerDeaths,
            Map<String, Integer> teamScores,
            int gameTimeTicks,
            Hooks hooks) {
        if (phase != CodTdmMap.GamePhase.PLAYING || killer == null || victim == null) {
            return;
        }

        playerKills.merge(killer.getUUID(), 1, Integer::sum);
        playerDeaths.merge(victim.getUUID(), 1, Integer::sum);

        hooks.findTeamByPlayer(killer).ifPresent(team -> {
            teamScores.merge(team.name, 1, Integer::sum);
            hooks.broadcastScoreUpdate(new ScoreUpdatePacket(teamScores, gameTimeTicks));
        });

        hooks.markRoomListDirty();
    }
}
