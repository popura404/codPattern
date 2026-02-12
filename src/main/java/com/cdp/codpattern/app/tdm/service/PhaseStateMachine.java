package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.cdp.codpattern.network.tdm.CountdownPacket;
import com.cdp.codpattern.network.tdm.ScoreUpdatePacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;

public final class PhaseStateMachine {
    public interface Hooks {
        void broadcastCountdown(CountdownPacket packet);

        void broadcastScoreUpdate(ScoreUpdatePacket packet);

        void teleportAllPlayersToSpawn();

        void giveAllPlayersKits();

        void clearAllPlayersInventory();

        void restoreAllRoomPlayersToAdventure();

        void clearRoundTransientState();

        boolean hasMatchEndTeleportPoint();

        Iterable<ServerPlayer> getJoinedPlayers();

        void teleportPlayerToMatchEndPoint(ServerPlayer player);

        void notifyMissingEndTeleportPoint(ServerPlayer player);

        void resetGame();
    }

    public record EnterPhaseResult(int phaseTimer, int gameTimeTicks) {
    }

    public record TickResult(int phaseTimer,
            int gameTimeTicks,
            Optional<CodTdmMap.GamePhase> nextPhase,
            boolean resetTriggered) {
    }

    private PhaseStateMachine() {
    }

    public static EnterPhaseResult enterPhase(CodTdmMap.GamePhase newPhase,
            int gameTimeTicks,
            CodTdmConfig config,
            Hooks hooks) {
        int nextGameTimeTicks = gameTimeTicks;

        switch (newPhase) {
            case COUNTDOWN -> hooks.broadcastCountdown(new CountdownPacket(config.getPreGameCountdownTicks(), false));
            case WARMUP -> {
                hooks.teleportAllPlayersToSpawn();
                hooks.giveAllPlayersKits();
            }
            case PLAYING -> {
                nextGameTimeTicks = 0;
                hooks.teleportAllPlayersToSpawn();
                hooks.giveAllPlayersKits();
            }
            case ENDED -> {
                hooks.clearAllPlayersInventory();
                hooks.restoreAllRoomPlayersToAdventure();
                hooks.clearRoundTransientState();
            }
            default -> {
            }
        }

        return new EnterPhaseResult(0, nextGameTimeTicks);
    }

    public static TickResult tick(CodTdmMap.GamePhase phase,
            int phaseTimer,
            int gameTimeTicks,
            CodTdmConfig config,
            Map<String, Integer> teamScores,
            Hooks hooks) {
        return switch (phase) {
            case WAITING -> new TickResult(phaseTimer, gameTimeTicks, Optional.empty(), false);
            case COUNTDOWN -> tickCountdown(phaseTimer, gameTimeTicks, config, hooks);
            case WARMUP -> tickWarmup(phaseTimer, gameTimeTicks, config);
            case PLAYING -> tickPlaying(phaseTimer, gameTimeTicks, teamScores, hooks);
            case ENDED -> tickEnded(phaseTimer, gameTimeTicks, hooks);
        };
    }

    public static int getRemainingTimeTicks(CodTdmMap.GamePhase phase,
            int phaseTimer,
            int gameTimeTicks,
            CodTdmConfig config) {
        return switch (phase) {
            case COUNTDOWN -> Math.max(0, config.getPreGameCountdownTicks() - phaseTimer);
            case WARMUP -> Math.max(0, config.getWarmupTimeTicks() - phaseTimer);
            case PLAYING -> Math.max(0, (config.getTimeLimitSeconds() * 20) - gameTimeTicks);
            default -> 0;
        };
    }

    private static TickResult tickCountdown(int phaseTimer,
            int gameTimeTicks,
            CodTdmConfig config,
            Hooks hooks) {
        int nextPhaseTimer = phaseTimer + 1;
        int totalTicks = config.getPreGameCountdownTicks();
        int remaining = totalTicks - nextPhaseTimer;

        if (remaining > 0 && remaining % 20 == 0) {
            int timeUntilBlackout = totalTicks - config.getBlackoutStartTicks();
            if (nextPhaseTimer < timeUntilBlackout) {
                hooks.broadcastCountdown(new CountdownPacket(remaining, false));
            }
        }

        int timeUntilBlackout = totalTicks - config.getBlackoutStartTicks();
        if (nextPhaseTimer == timeUntilBlackout) {
            hooks.broadcastCountdown(new CountdownPacket(config.getBlackoutStartTicks(), true));
        }

        if (nextPhaseTimer >= totalTicks) {
            return new TickResult(nextPhaseTimer, gameTimeTicks, Optional.of(CodTdmMap.GamePhase.WARMUP), false);
        }
        return new TickResult(nextPhaseTimer, gameTimeTicks, Optional.empty(), false);
    }

    private static TickResult tickWarmup(int phaseTimer, int gameTimeTicks, CodTdmConfig config) {
        int nextPhaseTimer = phaseTimer + 1;
        if (nextPhaseTimer >= config.getWarmupTimeTicks()) {
            return new TickResult(nextPhaseTimer, gameTimeTicks, Optional.of(CodTdmMap.GamePhase.PLAYING), false);
        }
        return new TickResult(nextPhaseTimer, gameTimeTicks, Optional.empty(), false);
    }

    private static TickResult tickPlaying(int phaseTimer,
            int gameTimeTicks,
            Map<String, Integer> teamScores,
            Hooks hooks) {
        int nextGameTimeTicks = ScoreService.tickPlaying(gameTimeTicks, teamScores, hooks::broadcastScoreUpdate);
        return new TickResult(phaseTimer, nextGameTimeTicks, Optional.empty(), false);
    }

    private static TickResult tickEnded(int phaseTimer, int gameTimeTicks, Hooks hooks) {
        int nextPhaseTimer = phaseTimer + 1;
        if (nextPhaseTimer >= 100) {
            if (hooks.hasMatchEndTeleportPoint()) {
                for (ServerPlayer player : hooks.getJoinedPlayers()) {
                    hooks.teleportPlayerToMatchEndPoint(player);
                }
            } else {
                for (ServerPlayer player : hooks.getJoinedPlayers()) {
                    hooks.notifyMissingEndTeleportPoint(player);
                }
            }
            hooks.resetGame();
            return new TickResult(nextPhaseTimer, gameTimeTicks, Optional.empty(), true);
        }
        return new TickResult(nextPhaseTimer, gameTimeTicks, Optional.empty(), false);
    }
}
