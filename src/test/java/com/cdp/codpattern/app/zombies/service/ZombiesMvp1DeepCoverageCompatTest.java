package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.app.zombies.runtime.ZombiesLifecycleHooks;
import com.cdp.codpattern.app.zombies.runtime.ZombiesLifecycleRuntime;
import com.cdp.codpattern.app.zombies.runtime.ZombiesPhaseStateMachine;
import com.cdp.codpattern.app.zombies.runtime.ZombiesPhaseTransitionContext;
import com.cdp.codpattern.app.zombies.runtime.ZombiesRoomRuntimeState;
import com.cdp.codpattern.client.ClientModeObjectState;
import com.cdp.codpattern.client.ClientModeRuntimeState;
import com.cdp.codpattern.config.zombies.ZombiesRulesConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class ZombiesMvp1DeepCoverageCompatTest {
    private static final ZombiesRulesConfig.Defaults DEFAULTS = new ZombiesRulesConfig.Defaults();
    private static final ZombiesPhaseStateMachine.Config FAST_CONFIG =
            new ZombiesPhaseStateMachine.Config(1, 1, 1);

    private ZombiesMvp1DeepCoverageCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        startupPreflightFailureLeavesRoomAndClientStateUntouched();
        fixedOpeningCountdownAndIntermissionDurationsUseHardMvp1Values();
        completedNonFinalWaveWaitsThreeSecondsBeforeNextIntermission();
        singleWaveTimeoutFailsAtHardLimit();
        victoryCleanupClearsHudObjectStateAndReleasesOccupancyIdempotently();
        failureCleanupClearsHudObjectStateAndReleasesOccupancyIdempotently();
        failurePriorityBeatsFinalWaveVictoryBeforeCleanup();
    }

    private static void startupPreflightFailureLeavesRoomAndClientStateUntouched() throws IOException {
        RoomId roomId = RoomId.of("zombies", "mvp1-deep-preflight-failure");
        resetGlobalRoomState(roomId);
        ClientModeRuntimeState.update(runtimeSnapshot(roomId, ZombiesGamePhase.WAITING, 3L));
        ClientModeObjectState.replaceRoomStates(roomId.encode(), List.of(), 3L);

        withWaves("zombies-mvp1-deep-preflight-", wavesDirectory -> {
            writeWave(wavesDirectory, "wave_001.json", "{\"wave\":1,\"mobs\":[]}");

            ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result = validationService(wavesDirectory)
                    .preflight(snapshot(roomId, false, List.of(initialSpawn(), zombieSpawn(1, 1.0D))));

            require(!result.success(), "MVP3-default preflight failure should happen before startup side effects");
            requireIssue(result, "map.missing_barrier");
            require(!ZombiesMapOccupancyService.instance().isOccupied(roomId),
                    "preflight failure should not acquire map occupancy");
            require(ClientModeRuntimeState.snapshot(roomId.encode()).orElseThrow().revision() == 3L,
                    "preflight failure should not initialize or overwrite HUD runtime state");
            require(ClientModeObjectState.revision(roomId.encode()).orElseThrow() == 3L,
                    "preflight failure should not initialize or overwrite object state");
        });

        resetGlobalRoomState(roomId);
    }

    private static void fixedOpeningCountdownAndIntermissionDurationsUseHardMvp1Values() {
        ZombiesRoomRuntimeState state = new ZombiesRoomRuntimeState(
                RoomId.of("zombies", "mvp1-deep-fixed-durations"));
        ZombiesLifecycleRuntime lifecycle = new ZombiesLifecycleRuntime(
                state,
                ZombiesPhaseStateMachine.Config.fromSeconds(
                        ZombiesPhaseStateMachine.DEFAULT_OPENING_COUNTDOWN_SECONDS,
                        ZombiesPhaseStateMachine.DEFAULT_INTERMISSION_SECONDS,
                        1),
                ignored -> ZombiesPhaseStateMachine.FailureCheckResult.none(),
                ignored -> false,
                List.of());

        lifecycle.beginOpeningCountdown(1);
        requirePhase(state, ZombiesGamePhase.OPENING_COUNTDOWN,
                "successful startup should enter the fixed opening countdown");
        tickStay(lifecycle, state, ZombiesPhaseStateMachine.DEFAULT_OPENING_COUNTDOWN_SECONDS
                        * ZombiesPhaseStateMachine.TICKS_PER_SECOND - 1,
                ZombiesGamePhase.OPENING_COUNTDOWN,
                "opening countdown should not finish before 20 seconds");
        tickExpect(lifecycle, state, ZombiesGamePhase.INTERMISSION,
                "opening countdown should finish exactly at 20 seconds");

        tickStay(lifecycle, state, ZombiesPhaseStateMachine.DEFAULT_INTERMISSION_SECONDS
                        * ZombiesPhaseStateMachine.TICKS_PER_SECOND - 1,
                ZombiesGamePhase.INTERMISSION,
                "wave intermission should not finish before 5 seconds");
        tickExpect(lifecycle, state, ZombiesGamePhase.WAVE_ACTIVE,
                "wave intermission should finish exactly at 5 seconds");
    }

    private static void singleWaveTimeoutFailsAtHardLimit() {
        ZombiesRoomRuntimeState state = new ZombiesRoomRuntimeState(
                RoomId.of("zombies", "mvp1-deep-wave-timeout"));
        ZombiesLifecycleRuntime lifecycle = new ZombiesLifecycleRuntime(
                state,
                FAST_CONFIG,
                ignored -> ZombiesPhaseStateMachine.FailureCheckResult.none(),
                ignored -> false,
                List.of());

        state.configureMaxWave(1);
        state.transitionTo(ZombiesGamePhase.INTERMISSION);
        state.transitionTo(ZombiesGamePhase.WAVE_ACTIVE);

        tickStay(lifecycle, state, ZombiesPhaseStateMachine.DEFAULT_WAVE_TIMEOUT_TICKS - 1,
                ZombiesGamePhase.WAVE_ACTIVE,
                "wave active should not timeout before the fixed 1200 second limit");
        require(state.waveState().waveTimeTicks() == ZombiesPhaseStateMachine.DEFAULT_WAVE_TIMEOUT_TICKS - 1,
                "wave time should track active wave ticks before timeout");

        tickExpect(lifecycle, state, ZombiesGamePhase.FAILED,
                "wave active should fail exactly at the fixed 1200 second limit");
        require(state.lastFailureCode().filter(ZombiesErrorCode.WAVE_TIMEOUT::equals).isPresent(),
                "wave timeout should be recorded as the failure reason");
    }

    private static void completedNonFinalWaveWaitsThreeSecondsBeforeNextIntermission() {
        ZombiesRoomRuntimeState state = new ZombiesRoomRuntimeState(
                RoomId.of("zombies", "mvp1-deep-wave-complete-delay"));
        ZombiesLifecycleRuntime lifecycle = new ZombiesLifecycleRuntime(
                state,
                FAST_CONFIG,
                ignored -> ZombiesPhaseStateMachine.FailureCheckResult.none(),
                ignored -> true,
                List.of());

        state.configureMaxWave(2);
        state.transitionTo(ZombiesGamePhase.INTERMISSION);
        state.transitionTo(ZombiesGamePhase.WAVE_ACTIVE);
        state.waveState().markWaveComplete();

        tickStay(lifecycle, state, ZombiesPhaseStateMachine.WAVE_COMPLETE_DELAY_TICKS - 1,
                ZombiesGamePhase.WAVE_ACTIVE,
                "completed non-final wave should stay active during the hard wave-complete delay");
        tickExpect(lifecycle, state, ZombiesGamePhase.INTERMISSION,
                "completed non-final wave should enter the next wave intermission after three seconds");
        require(state.waveState().targetWave() == 2, "next intermission should prepare target wave 2");
    }

    private static void victoryCleanupClearsHudObjectStateAndReleasesOccupancyIdempotently() {
        ClosureHarness harness = new ClosureHarness("mvp1-deep-victory");
        ZombiesLifecycleRuntime lifecycle = harness.lifecycle(
                ignored -> ZombiesPhaseStateMachine.FailureCheckResult.none());

        harness.seedResidue();
        lifecycle.beginOpeningCountdown(1);
        driveToWaveActive(lifecycle, harness.state);
        harness.state.waveState().markWaveComplete();

        tickStay(lifecycle, harness.state, ZombiesPhaseStateMachine.WAVE_COMPLETE_DELAY_TICKS - 1,
                ZombiesGamePhase.WAVE_ACTIVE,
                "final completed wave should stay active during the hard wave-complete delay");
        tickExpect(lifecycle, harness.state, ZombiesGamePhase.VICTORY,
                "final completed wave should enter victory");
        tickExpect(lifecycle, harness.state, ZombiesGamePhase.ENDING,
                "victory delay should enter ending");
        tickExpect(lifecycle, harness.state, ZombiesGamePhase.WAITING,
                "ending should reset to waiting and run cleanup");

        harness.requireClean("victory cleanup");
        harness.runSyntheticCleanupAgain("victory cleanup repeat");
        harness.requireCleanAfterRepeat("victory cleanup repeat");
    }

    private static void failureCleanupClearsHudObjectStateAndReleasesOccupancyIdempotently() {
        ClosureHarness harness = new ClosureHarness("mvp1-deep-failure");
        ZombiesLifecycleRuntime lifecycle = harness.lifecycle(state ->
                state.phase() == ZombiesGamePhase.WAVE_ACTIVE
                        ? ZombiesPhaseStateMachine.FailureCheckResult.failed(ZombiesErrorCode.PLAYER_DEAD)
                        : ZombiesPhaseStateMachine.FailureCheckResult.none());

        harness.seedResidue();
        lifecycle.beginOpeningCountdown(1);
        driveToWaveActive(lifecycle, harness.state);

        tickExpect(lifecycle, harness.state, ZombiesGamePhase.FAILED,
                "all-dead failure check should enter failed");
        require(harness.state.lastFailureCode().filter(ZombiesErrorCode.PLAYER_DEAD::equals).isPresent(),
                "failure code should stay visible until ending cleanup");
        tickExpect(lifecycle, harness.state, ZombiesGamePhase.ENDING,
                "failed delay should enter ending");
        tickExpect(lifecycle, harness.state, ZombiesGamePhase.WAITING,
                "ending should reset to waiting and run cleanup");

        harness.requireClean("failure cleanup");
        harness.runSyntheticCleanupAgain("failure cleanup repeat");
        harness.requireCleanAfterRepeat("failure cleanup repeat");
    }

    private static void failurePriorityBeatsFinalWaveVictoryBeforeCleanup() {
        ClosureHarness harness = new ClosureHarness("mvp1-deep-failure-priority");
        ZombiesLifecycleRuntime lifecycle = harness.lifecycle(
                ignored -> ZombiesPhaseStateMachine.FailureCheckResult.failed(ZombiesErrorCode.PLAYER_DEAD));

        harness.seedResidue();
        harness.state.configureMaxWave(1);
        harness.state.transitionTo(ZombiesGamePhase.INTERMISSION);
        harness.state.transitionTo(ZombiesGamePhase.WAVE_ACTIVE);
        harness.state.waveState().markWaveComplete();

        tickExpect(lifecycle, harness.state, ZombiesGamePhase.FAILED,
                "failure priority should choose failed over final-wave victory");
        require(harness.state.lastFailureCode().filter(ZombiesErrorCode.PLAYER_DEAD::equals).isPresent(),
                "failure-priority tick should record player-dead reason");
        require(ClientModeRuntimeState.snapshot(harness.roomKey()).isPresent(),
                "failure priority should not cleanup HUD before ending");
        require(ClientModeObjectState.revision(harness.roomKey()).isPresent(),
                "failure priority should not cleanup object state before ending");

        tickExpect(lifecycle, harness.state, ZombiesGamePhase.ENDING,
                "failed phase should enter ending");
        tickExpect(lifecycle, harness.state, ZombiesGamePhase.WAITING,
                "ending should reset to waiting and run cleanup");
        harness.requireClean("failure-priority cleanup");
    }

    private static void driveToWaveActive(ZombiesLifecycleRuntime lifecycle, ZombiesRoomRuntimeState state) {
        requirePhase(state, ZombiesGamePhase.OPENING_COUNTDOWN, "round should start in opening countdown");
        tickExpect(lifecycle, state, ZombiesGamePhase.INTERMISSION,
                "opening countdown should enter intermission");
        tickExpect(lifecycle, state, ZombiesGamePhase.WAVE_ACTIVE,
                "intermission should enter wave active");
        require(state.waveState().currentWave() == 1, "wave active should begin wave 1");
    }

    private static void tickExpect(
            ZombiesLifecycleRuntime lifecycle,
            ZombiesRoomRuntimeState state,
            ZombiesGamePhase expected,
            String message
    ) {
        ZombiesServiceResult<Void> result = lifecycle.tick();
        require(result.success(), message + ": tick hooks failed with " + result.code());
        requirePhase(state, expected, message);
    }

    private static void tickStay(
            ZombiesLifecycleRuntime lifecycle,
            ZombiesRoomRuntimeState state,
            int ticks,
            ZombiesGamePhase expected,
            String message
    ) {
        for (int tick = 0; tick < ticks; tick++) {
            tickExpect(lifecycle, state, expected, message + " at tick " + (tick + 1));
        }
    }

    private static void requirePhase(ZombiesRoomRuntimeState state, ZombiesGamePhase expected, String message) {
        require(state.phase() == expected, message + ": expected " + expected + " but was " + state.phase());
    }

    private static ZombiesStartupValidationService validationService(Path wavesDirectory) {
        return new ZombiesStartupValidationService(new ZombiesWaveConfigRepository(
                wavesDirectory,
                DEFAULTS,
                new ZombiesWaveValidator()));
    }

    private static ZombiesMapSnapshot snapshot(
            RoomId roomId,
            boolean hasEndTeleportPoint,
            List<ZombiesMapSnapshot.SpawnSnapshot> spawns
    ) {
        return ZombiesMapSnapshot.of(roomId, roomId.mapName(), hasEndTeleportPoint, spawns, List.of());
    }

    private static ZombiesMapSnapshot.SpawnSnapshot initialSpawn() {
        return new ZombiesMapSnapshot.SpawnSnapshot("initial-1", "spawn", "INITIAL", 0, 0.0D, false);
    }

    private static ZombiesMapSnapshot.SpawnSnapshot zombieSpawn(int group, double weight) {
        return new ZombiesMapSnapshot.SpawnSnapshot(
                "zombie-" + group,
                "zombieSpawn",
                "",
                group,
                weight,
                true);
    }

    private static ModeRuntimeStateSnapshot runtimeSnapshot(RoomId roomId, ZombiesGamePhase phase, long revision) {
        return new ModeRuntimeStateSnapshot(
                roomId.encode(),
                phase.key(),
                0,
                List.of(),
                Map.of(),
                List.of(),
                revision);
    }

    private static void requireIssue(
            ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result,
            String code
    ) {
        require(result.value().isPresent(), "preflight failure should carry snapshot issues");
        ZombiesStartupPreflightSnapshot snapshot = result.value().get();
        require(snapshot.issues().stream().anyMatch(issue -> code.equals(issue.code().key())),
                "expected issue " + code + ", got " + issueCodes(snapshot));
    }

    private static String issueCodes(ZombiesStartupPreflightSnapshot snapshot) {
        return snapshot.issues().stream()
                .map(issue -> issue.code().key())
                .toList()
                .toString();
    }

    private static void writeWave(Path wavesDirectory, String fileName, String json) throws IOException {
        Files.createDirectories(wavesDirectory);
        Files.writeString(wavesDirectory.resolve(fileName), json);
    }

    private static void withWaves(String prefix, ThrowingConsumer<Path> test) throws IOException {
        Path tempRoot = Files.createTempDirectory(prefix);
        try {
            test.accept(tempRoot.resolve("waves"));
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> sortedPaths = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path path : sortedPaths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void resetGlobalRoomState(RoomId roomId) {
        ZombiesMapOccupancyService.instance().forceRelease(roomId.gameType(), roomId.mapName());
        ClientModeRuntimeState.clear(roomId.encode());
        ClientModeObjectState.clear(roomId.encode());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class ClosureHarness {
        private final RoomId roomId;
        private final ZombiesRoomRuntimeState state;
        private final CleanupHook cleanupHook;
        private boolean objectRuntimeDirty;

        private ClosureHarness(String mapName) {
            this.roomId = RoomId.of("zombies", mapName);
            this.state = new ZombiesRoomRuntimeState(roomId);
            this.cleanupHook = new CleanupHook(this);
            resetGlobalRoomState(roomId);
        }

        private ZombiesLifecycleRuntime lifecycle(ZombiesPhaseStateMachine.FailurePriority failurePriority) {
            return new ZombiesLifecycleRuntime(
                    state,
                    FAST_CONFIG,
                    failurePriority,
                    currentState -> currentState.waveState().isWaveComplete(),
                    List.of(cleanupHook));
        }

        private void seedResidue() {
            require(ZombiesMapOccupancyService.instance().acquire(roomId).success(),
                    "setup should acquire map occupancy");
            ClientModeRuntimeState.update(runtimeSnapshot(roomId, ZombiesGamePhase.WAVE_ACTIVE, 10L));
            ClientModeObjectState.replaceRoomStates(roomKey(), List.of(), 10L);
            objectRuntimeDirty = true;
        }

        private String roomKey() {
            return roomId.encode();
        }

        private void requireClean(String context) {
            require(state.phase() == ZombiesGamePhase.WAITING, context + " should leave room waiting");
            require(state.waveState().currentWave() == 0, context + " should clear current wave");
            require(state.waveState().maxWave() == 0, context + " should clear max wave");
            require(state.lastFailureCode().isEmpty(), context + " should clear failure code");
            require(!ZombiesMapOccupancyService.instance().isOccupied(roomId),
                    context + " should release map occupancy");
            require(ClientModeRuntimeState.snapshot(roomKey()).isEmpty(),
                    context + " should clear HUD runtime state");
            require(ClientModeObjectState.revision(roomKey()).isEmpty(),
                    context + " should clear object state revision");
            require(ClientModeObjectState.roomStates(roomKey()).isEmpty(),
                    context + " should clear object states");
            require(!objectRuntimeDirty, context + " should clear JVM object runtime residue");
            require(cleanupHook.cleanupCalls == 1, context + " should run cleanup once");
            require(cleanupHook.successfulOccupancyReleases == 1,
                    context + " should release occupancy on first cleanup");
        }

        private void runSyntheticCleanupAgain(String reason) {
            ZombiesServiceResult<Void> result = cleanupHook.onCleanup(new ZombiesPhaseTransitionContext(
                    roomId,
                    ZombiesGamePhase.ENDING.key(),
                    ZombiesGamePhase.WAITING.key(),
                    ZombiesGamePhase.WAITING.key(),
                    0L));
            require(result.success(), reason + " should be idempotent");
        }

        private void requireCleanAfterRepeat(String context) {
            require(!ZombiesMapOccupancyService.instance().isOccupied(roomId),
                    context + " should still leave map unoccupied");
            require(ClientModeRuntimeState.snapshot(roomKey()).isEmpty(),
                    context + " should still leave HUD state empty");
            require(ClientModeObjectState.revision(roomKey()).isEmpty(),
                    context + " should still leave object state empty");
            require(!objectRuntimeDirty, context + " should still leave object runtime clean");
            require(cleanupHook.cleanupCalls == 2, context + " should record the repeated cleanup call");
            require(cleanupHook.successfulOccupancyReleases == 1,
                    context + " should not report a second occupancy release");
        }
    }

    private static final class CleanupHook implements ZombiesLifecycleHooks {
        private final ClosureHarness harness;
        private int cleanupCalls;
        private int successfulOccupancyReleases;

        private CleanupHook(ClosureHarness harness) {
            this.harness = harness;
        }

        @Override
        public ZombiesServiceResult<Void> onCleanup(ZombiesPhaseTransitionContext context) {
            cleanupCalls++;
            harness.objectRuntimeDirty = false;
            ClientModeRuntimeState.clear(harness.roomKey());
            ClientModeObjectState.clear(harness.roomKey());
            if (ZombiesMapOccupancyService.instance().release(harness.roomId)) {
                successfulOccupancyReleases++;
            }
            return ZombiesServiceResult.ok();
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws IOException;
    }
}
