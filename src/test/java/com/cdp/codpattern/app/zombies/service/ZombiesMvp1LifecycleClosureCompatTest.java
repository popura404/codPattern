package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.app.zombies.model.ZombiesLifeState;
import com.cdp.codpattern.app.zombies.runtime.ZombiesLifecycleHooks;
import com.cdp.codpattern.app.zombies.runtime.ZombiesLifecycleRuntime;
import com.cdp.codpattern.app.zombies.runtime.ZombiesPhaseStateMachine;
import com.cdp.codpattern.app.zombies.runtime.ZombiesPhaseTransitionContext;
import com.cdp.codpattern.app.zombies.runtime.ZombiesRoomRuntimeState;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ZombiesMvp1LifecycleClosureCompatTest {
    private static final UUID PLAYER_ONE = playerId(1);
    private static final UUID PLAYER_TWO = playerId(2);
    private static final ZombiesPhaseStateMachine.Config FAST_CONFIG =
            new ZombiesPhaseStateMachine.Config(1, 1, 1);

    private ZombiesMvp1LifecycleClosureCompatTest() {
    }

    public static void main(String[] args) {
        cancelStartVoteReturnsWaitingWithoutCleanup();
        victoryClosureCleansRuntimeAndReleasesMapOccupancy();
        failureClosureCleansRuntimeAndReleasesMapOccupancy();
        failurePriorityBeatsFinalWaveVictoryBeforeCleanup();
    }

    private static void cancelStartVoteReturnsWaitingWithoutCleanup() {
        ClosureHarness harness = new ClosureHarness("mvp1-start-vote-cancel");
        harness.seedRuntimeResidue();
        ZombiesLifecycleRuntime lifecycle = harness.lifecycle((ignored) -> ZombiesPhaseStateMachine.FailureCheckResult.none());

        lifecycle.beginStartVote();
        requirePhase(harness.state, ZombiesGamePhase.START_VOTE, "beginStartVote should enter START_VOTE");

        lifecycle.cancelStartVote();

        requirePhase(harness.state, ZombiesGamePhase.WAITING, "cancelStartVote should return to WAITING");
        require(harness.cleanupHooks.afterCleanupCount == 0, "cancelStartVote should not run cleanup hooks");
        require(!harness.cleanupHooks.clearHooksRan(), "cancelStartVote should not run cleanup clear hooks");
        require(harness.voteService.activeVoteSnapshot().isPresent(),
                "cancelStartVote should not clear active start vote via cleanup");
    }

    private static void victoryClosureCleansRuntimeAndReleasesMapOccupancy() {
        ClosureHarness harness = new ClosureHarness("mvp1-victory-closure");
        harness.seedRuntimeResidue();
        ZombiesLifecycleRuntime lifecycle = harness.lifecycle((ignored) -> ZombiesPhaseStateMachine.FailureCheckResult.none());

        lifecycle.beginOpeningCountdown(1);
        driveToWaveActive(lifecycle, harness.state);
        harness.state.waveState().markWaveComplete();

        tickExpect(lifecycle, harness.state, ZombiesGamePhase.VICTORY, "completed final wave should enter VICTORY");
        tickExpect(lifecycle, harness.state, ZombiesGamePhase.ENDING, "victory delay should enter ENDING");
        tickExpect(lifecycle, harness.state, ZombiesGamePhase.WAITING, "ENDING should reset to WAITING");

        harness.requireClean("victory cleanup");
        require(harness.cleanupHooks.afterCleanupCount == 1, "victory cleanup should run exactly once");
        require(harness.cleanupHooks.lastReason.equals(ZombiesGamePhase.ENDING.key()),
                "victory cleanup should be tied to ENDING reset");
    }

    private static void failureClosureCleansRuntimeAndReleasesMapOccupancy() {
        ClosureHarness harness = new ClosureHarness("mvp1-failure-closure");
        harness.seedRuntimeResidue();
        ZombiesLifecycleRuntime lifecycle = harness.lifecycle((state) -> {
            if (state.phase() == ZombiesGamePhase.WAVE_ACTIVE
                    && !harness.players.hasAnyAlive(state.roomTick(), 0L)) {
                return ZombiesPhaseStateMachine.FailureCheckResult.failed(ZombiesErrorCode.PLAYER_DEAD);
            }
            return ZombiesPhaseStateMachine.FailureCheckResult.none();
        });

        lifecycle.beginOpeningCountdown(1);
        driveToWaveActive(lifecycle, harness.state);
        harness.players.markDeadSpectating(PLAYER_ONE);
        harness.players.markDeadSpectating(PLAYER_TWO);

        ZombiesServiceResult<Void> failureTick = lifecycle.tick();
        require(failureTick.success(), "failure transition tick should not fail hook processing");
        requirePhase(harness.state, ZombiesGamePhase.FAILED, "all-dead failure should enter FAILED");
        require(harness.state.lastFailureCode().filter(ZombiesErrorCode.PLAYER_DEAD::equals).isPresent(),
                "failure path should retain PLAYER_DEAD until ending cleanup");

        tickExpect(lifecycle, harness.state, ZombiesGamePhase.ENDING, "failure delay should enter ENDING");
        tickExpect(lifecycle, harness.state, ZombiesGamePhase.WAITING, "ENDING should reset to WAITING");

        harness.requireClean("failure cleanup");
        require(harness.cleanupHooks.afterCleanupCount == 1, "failure cleanup should run exactly once");
    }

    private static void failurePriorityBeatsFinalWaveVictoryBeforeCleanup() {
        ClosureHarness harness = new ClosureHarness("mvp1-failure-priority");
        harness.seedRuntimeResidue();
        ZombiesLifecycleRuntime lifecycle = harness.lifecycle((ignored) ->
                ZombiesPhaseStateMachine.FailureCheckResult.failed(ZombiesErrorCode.PLAYER_DEAD));

        harness.state.configureMaxWave(1);
        harness.state.transitionTo(ZombiesGamePhase.INTERMISSION);
        harness.state.transitionTo(ZombiesGamePhase.WAVE_ACTIVE);
        harness.state.waveState().markWaveComplete();

        ZombiesServiceResult<Void> result = lifecycle.tick();
        require(result.success(), "priority tick should not fail hook processing");
        requirePhase(harness.state, ZombiesGamePhase.FAILED,
                "failure priority should choose FAILED instead of final-wave VICTORY");
        require(harness.state.lastFailureCode().filter(ZombiesErrorCode.PLAYER_DEAD::equals).isPresent(),
                "failure priority should record player-dead reason");

        tickExpect(lifecycle, harness.state, ZombiesGamePhase.ENDING, "failed phase should enter ENDING");
        tickExpect(lifecycle, harness.state, ZombiesGamePhase.WAITING, "ENDING should reset to WAITING");
        harness.requireClean("failure-priority cleanup");
    }

    private static void driveToWaveActive(ZombiesLifecycleRuntime lifecycle, ZombiesRoomRuntimeState state) {
        requirePhase(state, ZombiesGamePhase.OPENING_COUNTDOWN, "round should start in opening countdown");
        tickExpect(lifecycle, state, ZombiesGamePhase.INTERMISSION, "opening countdown should enter intermission");
        tickExpect(lifecycle, state, ZombiesGamePhase.WAVE_ACTIVE, "intermission should enter wave active");
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

    private static void requirePhase(ZombiesRoomRuntimeState state, ZombiesGamePhase expected, String message) {
        require(state.phase() == expected, message + ": expected " + expected + " but was " + state.phase());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static UUID playerId(int suffix) {
        return new UUID(0L, suffix);
    }

    private static final class ClosureHarness {
        private final RoomId roomId;
        private final ZombiesRoomRuntimeState state;
        private final ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        private final ZombiesReadyService readyService = new ZombiesReadyService(() -> true);
        private final ZombiesActiveSpawnGroupService activeGroups = new ZombiesActiveSpawnGroupService();
        private final ZombiesMapOccupancyService occupancy = ZombiesMapOccupancyService.instance();
        private final TrackingCleanupHooks cleanupHooks = new TrackingCleanupHooks(
                players,
                readyService,
                activeGroups);
        private final ZombiesCleanupService cleanupService;
        private final ZombiesStartVoteService voteService;

        private ClosureHarness(String mapName) {
            this.roomId = RoomId.of("zombies", mapName);
            this.state = new ZombiesRoomRuntimeState(roomId);
            this.occupancy.forceRelease(roomId.gameType(), roomId.mapName());
            ModeEntityOwnershipRegistry.instance().clearRoom(roomId);
            this.cleanupService = new ZombiesCleanupService(
                    ModeEntityOwnershipRegistry.instance(),
                    occupancy,
                    cleanupHooks,
                    List.of());
            this.voteService = new ZombiesStartVoteService(new VoteHooks());
            this.cleanupHooks.setVoteService(voteService);
        }

        private ZombiesLifecycleRuntime lifecycle(ZombiesPhaseStateMachine.FailurePriority failurePriority) {
            return new ZombiesLifecycleRuntime(
                    state,
                    FAST_CONFIG,
                    failurePriority,
                    state -> state.waveState().isWaveComplete(),
                    List.of(new CleanupLifecycleHook(cleanupService)));
        }

        private void seedRuntimeResidue() {
            require(occupancy.acquire(roomId).success(), "setup should acquire map occupancy");
            players.registerPlayers(List.of(PLAYER_ONE, PLAYER_TWO));
            players.getOrCreate(PLAYER_ONE).addPoints(125.0D);
            players.markAlive(PLAYER_ONE);
            players.markAlive(PLAYER_TWO);
            require(players.get(PLAYER_ONE).orElseThrow().lifeState() == ZombiesLifeState.ALIVE,
                    "setup should mark player one alive");

            require(readyService.setPlayerReady(PLAYER_ONE, true), "setup should ready player one");
            require(readyService.setPlayerReady(PLAYER_TWO, true), "setup should ready player two");
            require(voteService.initiateStartVote(PLAYER_ONE), "setup should create a stale start vote");

            activeGroups.activate(2);
            cleanupHooks.markObjectRuntimeDirty();
        }

        private void requireClean(String context) {
            require(state.phase() == ZombiesGamePhase.WAITING, context + " should leave phase WAITING");
            require(state.waveState().currentWave() == 0, context + " should clear current wave");
            require(state.waveState().maxWave() == 0, context + " should clear max wave");
            require(state.lastFailureCode().isEmpty(), context + " should clear failure code");
            require(players.states().isEmpty(), context + " should clear player states");
            require(readyService.knownPlayers().isEmpty(), context + " should clear ready known players");
            require(readyService.readyPlayers().isEmpty(), context + " should clear ready players");
            require(voteService.activeVoteSnapshot().isEmpty(), context + " should clear active start vote");
            require(activeGroups.snapshot().equals(Set.of(1)), context + " should reset active spawn groups");
            require(!cleanupHooks.objectRuntimeDirty, context + " should clear object runtime state");
            require(!occupancy.isOccupied(roomId), context + " should release map occupancy");
            require(cleanupHooks.clearHooksRan(), context + " should run every cleanup clear hook");
        }

        private final class VoteHooks implements ZombiesStartVoteService.Hooks {
            @Override
            public Collection<UUID> currentMembers() {
                return List.of(PLAYER_ONE, PLAYER_TWO);
            }

            @Override
            public boolean isWaitingPhase() {
                return true;
            }

            @Override
            public boolean isPlayerReady(UUID playerId) {
                return readyService.isPlayerReady(playerId);
            }

            @Override
            public int minPlayersToStart() {
                return 2;
            }

            @Override
            public int votePercentageToStart() {
                return 100;
            }
        }
    }

    private static final class CleanupLifecycleHook implements ZombiesLifecycleHooks {
        private final ZombiesCleanupService cleanupService;

        private CleanupLifecycleHook(ZombiesCleanupService cleanupService) {
            this.cleanupService = cleanupService;
        }

        @Override
        public ZombiesServiceResult<Void> onCleanup(ZombiesPhaseTransitionContext context) {
            ZombiesServiceResult<ZombiesCleanupService.CleanupSummary> result =
                    cleanupService.cleanup(context.roomId(), context.previousPhase(), dimension -> null);
            return result.success()
                    ? ZombiesServiceResult.ok()
                    : ZombiesServiceResult.failure(result.code(), result.params(), result.logMessage());
        }
    }

    private static final class TrackingCleanupHooks implements ZombiesCleanupService.Hooks {
        private final ZombiesPlayerStateService players;
        private final ZombiesReadyService readyService;
        private final ZombiesActiveSpawnGroupService activeGroups;
        private ZombiesStartVoteService voteService;
        private String lastReason = "";
        private boolean objectRuntimeDirty;
        private int objectClears;
        private int playerClears;
        private int readyClears;
        private int voteClears;
        private int lifecycleClears;
        private int hudClears;
        private int afterCleanupCount;

        private TrackingCleanupHooks(
                ZombiesPlayerStateService players,
                ZombiesReadyService readyService,
                ZombiesActiveSpawnGroupService activeGroups
        ) {
            this.players = players;
            this.readyService = readyService;
            this.activeGroups = activeGroups;
        }

        private void setVoteService(ZombiesStartVoteService voteService) {
            this.voteService = voteService;
        }

        private void markObjectRuntimeDirty() {
            objectRuntimeDirty = true;
        }

        @Override
        public void clearObjectRuntime(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            objectClears++;
            objectRuntimeDirty = false;
            activeGroups.resetToInitial();
        }

        @Override
        public void clearPlayerRuntime(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            playerClears++;
            players.clear();
        }

        @Override
        public void clearReadyState(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            readyClears++;
            readyService.clear();
        }

        @Override
        public void clearStartVote(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            voteClears++;
            if (voteService != null) {
                voteService.clearActiveVoteSession();
            }
        }

        @Override
        public void clearLifecycleRuntime(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            lifecycleClears++;
        }

        @Override
        public void clearHudState(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            hudClears++;
        }

        @Override
        public void afterCleanup(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            afterCleanupCount++;
            lastReason = context.reason();
        }

        private boolean clearHooksRan() {
            return objectClears == 1
                    && playerClears == 1
                    && readyClears == 1
                    && voteClears == 1
                    && lifecycleClears == 1
                    && hudClears == 1;
        }
    }
}
