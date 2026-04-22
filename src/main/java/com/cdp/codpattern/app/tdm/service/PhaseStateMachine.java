package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.network.tdm.CountdownPacket;
import com.cdp.codpattern.network.tdm.ScoreUpdatePacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;

public final class PhaseStateMachine {
    public static final int END_SUMMARY_PAGE_TICKS = 100;
    public static final int END_SUMMARY_PAGE_COUNT = 3;
    public static final int END_PHASE_TOTAL_TICKS = END_SUMMARY_PAGE_TICKS * END_SUMMARY_PAGE_COUNT;
    public static final int PREPARE_DURATION_TICKS = 400;
    public static final int PREPARE_BLACKOUT_FADE_IN_TICKS = 20;
    public static final int PREPARE_BLACKOUT_HOLD_TICKS = 100;
    public static final int PREPARE_BLACKOUT_FADE_OUT_TICKS = 20;
    public static final int PREPARE_BLACKOUT_TOTAL_TICKS = PREPARE_BLACKOUT_FADE_IN_TICKS
            + PREPARE_BLACKOUT_HOLD_TICKS
            + PREPARE_BLACKOUT_FADE_OUT_TICKS;

    public interface Hooks {
        void broadcastCountdown(CountdownPacket packet);

        void broadcastScoreUpdate(ScoreUpdatePacket packet);

        void showCountdownActionBar(int secondsLeft);

        void clearCountdownActionBar();

        void teleportAllPlayersToSpawn();

        void giveAllPlayersKits();

        void lockWarmupMovement();

        void unlockAllRoomPlayersMovement();

        void clearAllPlayersInventory();

        void restoreAllRoomPlayersToAdventure();

        void clearRoundTransientState();

        void notifyMatchEnded();

        void onMatchEnded();

        boolean hasMatchEndTeleportPoint();

        Iterable<ServerPlayer> getJoinedPlayers();

        boolean teleportPlayerToMatchEndPoint(ServerPlayer player);

        void notifyMissingEndTeleportPoint(ServerPlayer player);

        void notifyUnusableEndTeleportPoint(ServerPlayer player);

        void showPlayingIntro();

        void resetGame();
    }

    public record EnterPhaseResult(int phaseTimer, int gameTimeTicks) {
    }

    public record TickResult(int phaseTimer,
            int gameTimeTicks,
            Optional<TdmGamePhase> nextPhase,
            boolean resetTriggered) {
    }

    private PhaseStateMachine() {
    }

    public static EnterPhaseResult enterPhase(TdmGamePhase newPhase,
            int gameTimeTicks,
            CodTdmConfig config,
            Hooks hooks) {
        int nextGameTimeTicks = gameTimeTicks;

        switch (newPhase) {
            case COUNTDOWN -> {
                if (config.getPreGameCountdownTicks() <= PREPARE_BLACKOUT_TOTAL_TICKS) {
                    startPrepareBlackout(hooks);
                } else {
                    hooks.broadcastCountdown(new CountdownPacket(config.getPreGameCountdownTicks(), false));
                    hooks.showCountdownActionBar(secondsFromTicks(config.getPreGameCountdownTicks()));
                }
            }
            case WARMUP -> {
                hooks.clearCountdownActionBar();
                hooks.lockWarmupMovement();
            }
            case PLAYING -> {
                nextGameTimeTicks = 0;
                hooks.unlockAllRoomPlayersMovement();
            }
            case ENDED -> {
                hooks.unlockAllRoomPlayersMovement();
                hooks.notifyMatchEnded();
                hooks.onMatchEnded();
                hooks.clearAllPlayersInventory();
                hooks.restoreAllRoomPlayersToAdventure();
                hooks.clearRoundTransientState();
            }
            default -> {
            }
        }

        return new EnterPhaseResult(0, nextGameTimeTicks);
    }

    public static TickResult tick(TdmGamePhase phase,
            int phaseTimer,
            int gameTimeTicks,
            CodTdmConfig config,
            Map<String, Integer> teamScores,
            Hooks hooks) {
        return switch (phase) {
            case WAITING -> new TickResult(phaseTimer, gameTimeTicks, Optional.empty(), false);
            case COUNTDOWN -> tickCountdown(phaseTimer, gameTimeTicks, config, hooks);
            case WARMUP -> tickWarmup(phaseTimer, gameTimeTicks, config, hooks);
            case PLAYING -> tickPlaying(phaseTimer, gameTimeTicks, teamScores, hooks);
            case ENDED -> tickEnded(phaseTimer, gameTimeTicks, hooks);
        };
    }

    public static int getRemainingTimeTicks(TdmGamePhase phase,
            int phaseTimer,
            int gameTimeTicks,
            CodTdmConfig config) {
        return switch (phase) {
            case COUNTDOWN -> Math.max(0, config.getPreGameCountdownTicks() - phaseTimer);
            case WARMUP -> Math.max(0, PREPARE_DURATION_TICKS - phaseTimer);
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
        int blackoutStartTick = Math.max(0, totalTicks - PREPARE_BLACKOUT_TOTAL_TICKS);

        if (remaining > 0 && remaining % 20 == 0) {
            if (nextPhaseTimer < blackoutStartTick) {
                hooks.showCountdownActionBar(secondsFromTicks(remaining));
                hooks.broadcastCountdown(new CountdownPacket(remaining, false));
            }
        }

        if (blackoutStartTick > 0 && nextPhaseTimer == blackoutStartTick) {
            startPrepareBlackout(hooks);
        }

        if (nextPhaseTimer >= totalTicks) {
            return new TickResult(nextPhaseTimer, gameTimeTicks, Optional.of(TdmGamePhase.WARMUP), false);
        }
        return new TickResult(nextPhaseTimer, gameTimeTicks, Optional.empty(), false);
    }

    private static TickResult tickWarmup(
            int phaseTimer,
            int gameTimeTicks,
            CodTdmConfig config,
            Hooks hooks
    ) {
        hooks.lockWarmupMovement();
        int nextPhaseTimer = phaseTimer + 1;
        if (nextPhaseTimer >= PREPARE_DURATION_TICKS) {
            return new TickResult(nextPhaseTimer, gameTimeTicks, Optional.of(TdmGamePhase.PLAYING), false);
        }
        return new TickResult(nextPhaseTimer, gameTimeTicks, Optional.empty(), false);
    }

    private static TickResult tickPlaying(int phaseTimer,
            int gameTimeTicks,
            Map<String, Integer> teamScores,
            Hooks hooks) {
        int nextGameTimeTicks = ScoreService.tickPlaying(gameTimeTicks, teamScores, hooks::broadcastScoreUpdate);
        if (nextGameTimeTicks == 60) {
            hooks.showPlayingIntro();
        }
        return new TickResult(phaseTimer, nextGameTimeTicks, Optional.empty(), false);
    }

    private static TickResult tickEnded(int phaseTimer, int gameTimeTicks, Hooks hooks) {
        int nextPhaseTimer = phaseTimer + 1;
        if (nextPhaseTimer >= END_PHASE_TOTAL_TICKS) {
            if (hooks.hasMatchEndTeleportPoint()) {
                for (ServerPlayer player : hooks.getJoinedPlayers()) {
                    if (!hooks.teleportPlayerToMatchEndPoint(player)) {
                        hooks.notifyUnusableEndTeleportPoint(player);
                    }
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

    private static int secondsFromTicks(int ticks) {
        return Math.max(1, (Math.max(0, ticks) + 19) / 20);
    }

    private static void startPrepareBlackout(Hooks hooks) {
        hooks.clearCountdownActionBar();
        hooks.restoreAllRoomPlayersToAdventure();
        hooks.teleportAllPlayersToSpawn();
        hooks.giveAllPlayersKits();
        hooks.lockWarmupMovement();
        hooks.broadcastCountdown(new CountdownPacket(PREPARE_BLACKOUT_TOTAL_TICKS, true));
    }
}
