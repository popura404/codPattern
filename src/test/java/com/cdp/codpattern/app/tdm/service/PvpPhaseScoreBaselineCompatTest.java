package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.network.match.CountdownPacket;
import com.cdp.codpattern.network.match.ScoreUpdatePacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PvpPhaseScoreBaselineCompatTest {
    private PvpPhaseScoreBaselineCompatTest() {
    }

    public static void main(String[] args) {
        verifyPhaseTransitionsAndWarmupContract();
        verifyScoreAndEndConditions();
        verifyEndTeleportAndCleanupContract();
        System.out.println("PASS PVP phase/score baseline compat");
    }

    private static void verifyPhaseTransitionsAndWarmupContract() {
        CodTdmConfig config = new CodTdmConfig();
        RecordingHooks hooks = new RecordingHooks();

        PhaseStateMachine.EnterPhaseResult countdown = PhaseStateMachine.enterPhase(
                TdmGamePhase.COUNTDOWN, 37, config, hooks);
        requireEquals(0, countdown.phaseTimer(), "countdown entry resets phase timer");
        requireEquals(37, countdown.gameTimeTicks(), "countdown entry preserves game timer");
        requireEquals(1, hooks.countdownPackets.size(), "countdown entry broadcasts once");
        requireEquals(1, hooks.clearCountdownCalls, "countdown entry clears action bar");

        PhaseStateMachine.TickResult countdownEnd = PhaseStateMachine.tick(
                TdmGamePhase.COUNTDOWN,
                PhaseStateMachine.PRE_GAME_COUNTDOWN_TICKS - 1,
                37,
                config,
                Map.of(),
                hooks);
        requireEquals(TdmGamePhase.WARMUP, countdownEnd.nextPhase().orElseThrow(),
                "countdown transitions to warmup at the frozen duration");

        hooks.events.clear();
        PhaseStateMachine.enterPhase(TdmGamePhase.WARMUP, 37, config, hooks);
        requireEquals(List.of("clear-countdown", "restore-adventure", "teleport-spawn", "kits-silent", "lock"),
                hooks.events,
                "warmup entry preserves restore/teleport/kit/lock ordering");

        PhaseStateMachine.TickResult warmupEnd = PhaseStateMachine.tick(
                TdmGamePhase.WARMUP,
                PhaseStateMachine.PREPARE_DURATION_TICKS - 1,
                37,
                config,
                Map.of(),
                hooks);
        requireEquals(TdmGamePhase.PLAYING, warmupEnd.nextPhase().orElseThrow(),
                "warmup transitions to playing at the frozen duration");
        requireTrue(hooks.lockCalls >= 2, "warmup tick keeps movement locked");

        PhaseStateMachine.EnterPhaseResult playing = PhaseStateMachine.enterPhase(
                TdmGamePhase.PLAYING, 123, config, hooks);
        requireEquals(0, playing.gameTimeTicks(), "playing entry resets match timer");
        requireEquals(1, hooks.unlockCalls, "playing entry unlocks movement");
    }

    private static void verifyScoreAndEndConditions() {
        CodTdmConfig config = new CodTdmConfig();
        config.setTimeLimitSeconds(2);
        config.setScoreLimit(3);

        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("kortac", 2);
        scores.put("specgru", 1);

        requireFalse(ScoreService.hasReachedVictoryGoal(TdmGamePhase.WARMUP, 40, scores, config),
                "victory is disabled outside playing");
        requireFalse(ScoreService.hasReachedVictoryGoal(TdmGamePhase.PLAYING, 39, scores, config),
                "match continues below score and time limits");
        requireTrue(ScoreService.hasReachedVictoryGoal(TdmGamePhase.PLAYING, 40, scores, config),
                "time limit ends the match");

        scores.put("kortac", 3);
        requireTrue(ScoreService.hasReachedVictoryGoal(TdmGamePhase.PLAYING, 1, scores, config),
                "score limit ends the match");

        List<ScoreUpdatePacket> packets = new ArrayList<>();
        requireEquals(20, ScoreService.tickPlaying(19, scores, packets::add),
                "playing timer advances by one tick");
        requireEquals(1, packets.size(), "score snapshot broadcasts every 20 ticks");
        requireEquals(21, ScoreService.tickPlaying(20, scores, packets::add),
                "playing timer remains monotonic");
        requireEquals(1, packets.size(), "score snapshot does not broadcast off cadence");
    }

    private static void verifyEndTeleportAndCleanupContract() {
        CodTdmConfig config = new CodTdmConfig();
        RecordingHooks hooks = new RecordingHooks();

        hooks.events.clear();
        PhaseStateMachine.enterPhase(TdmGamePhase.ENDED, 55, config, hooks);
        requireEquals(List.of(
                        "unlock",
                        "notify-ended",
                        "on-ended",
                        "clear-inventory",
                        "restore-adventure",
                        "clear-transient"),
                hooks.events,
                "ended entry preserves notification and cleanup ordering");

        hooks.joinedPlayers = Collections.singletonList(null);
        hooks.hasEndTeleport = false;
        PhaseStateMachine.TickResult missingEndPoint = PhaseStateMachine.tick(
                TdmGamePhase.ENDED,
                PhaseStateMachine.END_PHASE_TOTAL_TICKS - 1,
                55,
                config,
                Map.of(),
                hooks);
        requireTrue(missingEndPoint.resetTriggered(), "ended summary triggers reset at the frozen duration");
        requireEquals(1, hooks.missingEndTeleportCalls,
                "missing end teleport is reported once per joined player before reset");
        requireEquals(1, hooks.resetCalls, "ended summary resets the room");

        RecordingHooks unusableHooks = new RecordingHooks();
        unusableHooks.joinedPlayers = Collections.singletonList(null);
        unusableHooks.hasEndTeleport = true;
        unusableHooks.teleportSucceeds = false;
        PhaseStateMachine.tick(
                TdmGamePhase.ENDED,
                PhaseStateMachine.END_PHASE_TOTAL_TICKS - 1,
                0,
                config,
                Map.of(),
                unusableHooks);
        requireEquals(1, unusableHooks.teleportCalls, "configured end teleport is attempted");
        requireEquals(1, unusableHooks.unusableEndTeleportCalls,
                "failed end teleport is reported before reset");
        requireEquals(1, unusableHooks.resetCalls, "failed teleport does not suppress cleanup reset");
    }

    private static final class RecordingHooks implements PhaseStateMachine.Hooks {
        private final List<String> events = new ArrayList<>();
        private final List<CountdownPacket> countdownPackets = new ArrayList<>();
        private int clearCountdownCalls;
        private int lockCalls;
        private int unlockCalls;
        private int missingEndTeleportCalls;
        private int unusableEndTeleportCalls;
        private int teleportCalls;
        private int resetCalls;
        private boolean hasEndTeleport;
        private boolean teleportSucceeds = true;
        private Iterable<ServerPlayer> joinedPlayers = List.of();

        @Override
        public void broadcastCountdown(CountdownPacket packet) {
            countdownPackets.add(packet);
        }

        @Override
        public void broadcastScoreUpdate(ScoreUpdatePacket packet) {
        }

        @Override
        public void showCountdownActionBar(int secondsLeft) {
        }

        @Override
        public void clearCountdownActionBar() {
            clearCountdownCalls++;
            events.add("clear-countdown");
        }

        @Override
        public void teleportAllPlayersToSpawn() {
            events.add("teleport-spawn");
        }

        @Override
        public void giveAllPlayersKits() {
            events.add("kits");
        }

        @Override
        public void giveAllPlayersKitsSilently() {
            events.add("kits-silent");
        }

        @Override
        public void lockWarmupMovement() {
            lockCalls++;
            events.add("lock");
        }

        @Override
        public void unlockAllRoomPlayersMovement() {
            unlockCalls++;
            events.add("unlock");
        }

        @Override
        public void clearAllPlayersInventory() {
            events.add("clear-inventory");
        }

        @Override
        public void restoreAllRoomPlayersToAdventure() {
            events.add("restore-adventure");
        }

        @Override
        public void clearRoundTransientState() {
            events.add("clear-transient");
        }

        @Override
        public void notifyMatchEnded() {
            events.add("notify-ended");
        }

        @Override
        public void onMatchEnded() {
            events.add("on-ended");
        }

        @Override
        public boolean hasMatchEndTeleportPoint() {
            return hasEndTeleport;
        }

        @Override
        public Iterable<ServerPlayer> getJoinedPlayers() {
            return joinedPlayers;
        }

        @Override
        public boolean teleportPlayerToMatchEndPoint(ServerPlayer player) {
            teleportCalls++;
            return teleportSucceeds;
        }

        @Override
        public void notifyMissingEndTeleportPoint(ServerPlayer player) {
            missingEndTeleportCalls++;
        }

        @Override
        public void notifyUnusableEndTeleportPoint(ServerPlayer player) {
            unusableEndTeleportCalls++;
        }

        @Override
        public void showPlayingIntro() {
            events.add("playing-intro");
        }

        @Override
        public void resetGame() {
            resetCalls++;
            events.add("reset");
        }
    }

    private static void requireTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void requireFalse(boolean value, String message) {
        requireTrue(!value, message);
    }

    private static void requireEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
