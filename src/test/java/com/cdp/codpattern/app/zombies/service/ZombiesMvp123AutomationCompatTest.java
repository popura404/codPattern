package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffType;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.app.zombies.model.ZombiesLifeState;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;
import com.cdp.codpattern.app.zombies.runtime.ZombiesPhaseStateMachine;
import com.cdp.codpattern.app.zombies.runtime.ZombiesRoomRuntimeState;
import com.cdp.codpattern.app.zombies.validation.ZombiesMapValidationProfile;
import com.cdp.codpattern.app.zombies.validation.ZombiesMapValidator;
import com.cdp.codpattern.config.zombies.ZombiesRulesConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public final class ZombiesMvp123AutomationCompatTest {
    private static final RoomId ROOM_ID = RoomId.of("zombies", "mvp123-automation");
    private static final ZombiesRulesConfig.Defaults DEFAULTS = new ZombiesRulesConfig.Defaults();
    private static final UUID PLAYER_ONE = playerId(1);
    private static final UUID PLAYER_TWO = playerId(2);
    private static final UUID PLAYER_THREE = playerId(3);
    private static final ZombiesErrorCode BARRIER_ALREADY_CLEARED =
            ZombiesErrorCode.of("barrier.already_cleared");

    private ZombiesMvp123AutomationCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        mvp1VotePreflightFailureLeavesRoundWaitingWithoutRuntimeSideEffects();
        mvp1SuccessfulVotePreflightDrivesPhaseAndResetCleanup();
        mvp2PurchaseFailuresDoNotSpendAndUnlockActivatesGroupAtomically();
        mvp3FailurePriorityBeatsVictoryAndRecordsPendingEndTeleport();
        mvp3IntermissionRespawnDecisionPreparesPlayerState();
    }

    private static void mvp1VotePreflightFailureLeavesRoundWaitingWithoutRuntimeSideEffects() throws IOException {
        withWaves("zombies-mvp123-m1-preflight-failure-", wavesDirectory -> {
            writeWave(wavesDirectory, "wave_001.json", "{\"wave\":1,\"mobs\":[]}");
            RoundHarness harness = new RoundHarness(wavesDirectory, missingInitialSpawnMap());
            harness.addMembers(PLAYER_ONE, PLAYER_TWO);
            harness.readyAll();

            require(harness.voteService.initiateStartVote(PLAYER_ONE), "ready members should start vote");
            long voteId = harness.activeVoteId();
            require(!harness.voteService.submitVoteResponse(PLAYER_ONE, voteId, true),
                    "first unanimous vote response should not pass");
            requirePhase(harness.state, ZombiesGamePhase.START_VOTE, "vote should enter START_VOTE before resolution");
            require(harness.voteService.submitVoteResponse(PLAYER_TWO, voteId, true),
                    "second unanimous vote response should pass");

            require(harness.preflightAttempts == 1, "passed vote should run exactly one startup preflight");
            require(harness.preflightSuccesses == 0, "invalid MVP1 map preflight should not succeed");
            require(harness.preflightFailures == 1, "invalid MVP1 map preflight should fail");
            requireIssue(harness.lastPreflightResult.orElseThrow(), "map.missing_initial_spawn");
            requirePhase(harness.state, ZombiesGamePhase.WAITING,
                    "preflight failure should return the room to WAITING");
            require(harness.state.waveState().maxWave() == 0,
                    "failed preflight should not configure wave runtime");
            require(harness.players.states().isEmpty(),
                    "failed preflight should not register per-player runtime state");
            require(harness.voteService.activeVoteSnapshot().isEmpty(),
                    "resolved vote should leave no active vote session");
        });
    }

    private static void mvp1SuccessfulVotePreflightDrivesPhaseAndResetCleanup() throws IOException {
        withWaves("zombies-mvp123-m1-success-", wavesDirectory -> {
            writeWave(wavesDirectory, "wave_001.json", "{\"wave\":1,\"mobs\":[]}");
            RoundHarness harness = new RoundHarness(wavesDirectory, validMvp1Map());
            harness.addMembers(PLAYER_ONE, PLAYER_TWO);
            harness.readyAll();

            passStartVote(harness);

            require(harness.preflightSuccesses == 1, "successful vote should pass startup preflight");
            requirePhase(harness.state, ZombiesGamePhase.OPENING_COUNTDOWN,
                    "successful preflight should enter OPENING_COUNTDOWN");
            require(harness.players.states().size() == 2, "successful preflight should register round members");
            require(harness.state.waveState().maxWave() == 1, "valid wave_001 should configure maxWave=1");

            ZombiesPhaseStateMachine.Config config = new ZombiesPhaseStateMachine.Config(1, 1, 1);
            tickAndApply(harness.state, config, ZombiesPhaseStateMachine.FailureCheckResult.none(), true);
            requirePhase(harness.state, ZombiesGamePhase.INTERMISSION,
                    "opening countdown should advance to intermission");
            require(harness.state.waveState().targetWave() == 1, "first intermission should prepare target wave 1");

            tickAndApply(harness.state, config, ZombiesPhaseStateMachine.FailureCheckResult.none(), true);
            requirePhase(harness.state, ZombiesGamePhase.WAVE_ACTIVE,
                    "intermission should advance to wave active");
            require(harness.state.waveState().currentWave() == 1, "wave active should begin target wave 1");

            harness.state.waveState().markWaveComplete();
            tickAndApply(harness.state, config, ZombiesPhaseStateMachine.FailureCheckResult.none(), true);
            requirePhase(harness.state, ZombiesGamePhase.VICTORY,
                    "empty final wave should settle to victory");

            tickAndApply(harness.state, config, ZombiesPhaseStateMachine.FailureCheckResult.none(), true);
            requirePhase(harness.state, ZombiesGamePhase.ENDING, "victory end delay should enter ENDING");

            ZombiesPhaseStateMachine.TickResult reset =
                    ZombiesPhaseStateMachine.tick(harness.state, config, ignored -> ZombiesPhaseStateMachine.FailureCheckResult.none(), ignored -> false);
            require(reset.resetTriggered(), "ENDING tick should request round reset cleanup");
            harness.clearRoundRuntime();
            applyTransition(harness.state, reset);
            requirePhase(harness.state, ZombiesGamePhase.WAITING, "cleanup reset should return to WAITING");
            require(harness.players.states().isEmpty(), "cleanup reset should clear player runtime state");
            require(harness.readyService.knownPlayers().isEmpty(), "cleanup reset should clear ready state");
            require(harness.state.waveState().currentWave() == 0, "cleanup reset should clear current wave");
            require(harness.state.waveState().maxWave() == 0, "cleanup reset should clear max wave");
        });
    }

    private static void mvp2PurchaseFailuresDoNotSpendAndUnlockActivatesGroupAtomically() {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        ZombiesWeaponInstanceService weapons = new ZombiesWeaponInstanceService(economy);
        ZombiesActiveSpawnGroupService activeGroups = new ZombiesActiveSpawnGroupService();
        UUID playerId = PLAYER_THREE;

        requireSuccess(economy.addPoints(playerId, 500.0D), "setup points should succeed");
        requireSuccess(weapons.purchaseWallWeapon(playerId, "tacz:m4a1", 2, 1.25D, 210, 200.0D),
                "initial wall weapon purchase should succeed");
        requirePoints(players, playerId, 300.0D, "weapon purchase should deduct cost");

        ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> duplicate =
                weapons.purchaseWallWeapon(playerId, "tacz:m4a1", 2, 1.25D, 210, 250.0D);
        requireFailure(duplicate, ZombiesErrorCode.WEAPON_ALREADY_OWNED,
                "duplicate wall weapon purchase should fail");
        requirePoints(players, playerId, 300.0D, "duplicate weapon failure should not spend points");
        requirePrimary(players, playerId, "tacz:m4a1", 2, "duplicate failure should keep current primary");

        ZombiesServiceResult<Integer> insufficientUnlock =
                unlockSpawnGroupAtomically(economy, activeGroups, playerId, 2, 400.0D);
        requireFailure(insufficientUnlock, ZombiesErrorCode.ECONOMY_NOT_ENOUGH_POINTS,
                "insufficient barrier unlock should fail before side effects");
        require(activeGroups.snapshot().equals(Set.of(1)),
                "insufficient unlock should leave only initial spawn group active");
        requirePoints(players, playerId, 300.0D, "insufficient unlock should not spend points");

        ZombiesServiceResult<Integer> unlock =
                unlockSpawnGroupAtomically(economy, activeGroups, playerId, 2, 125.0D);
        requireSuccess(unlock, "barrier-style unlock should succeed");
        require(activeGroups.snapshot().equals(Set.of(1, 2)),
                "successful unlock should activate the purchased spawn group");
        requirePoints(players, playerId, 175.0D, "successful unlock should spend points");

        ZombiesServiceResult<Integer> repeatUnlock =
                unlockSpawnGroupAtomically(economy, activeGroups, playerId, 2, 50.0D);
        requireFailure(repeatUnlock, BARRIER_ALREADY_CLEARED,
                "repeat unlock of an active group should fail");
        require(activeGroups.snapshot().equals(Set.of(1, 2)),
                "repeat unlock failure should keep active groups stable");
        requirePoints(players, playerId, 175.0D, "repeat unlock failure should not spend points");
    }

    private static void mvp3FailurePriorityBeatsVictoryAndRecordsPendingEndTeleport() {
        ZombiesRoomRuntimeState state = new ZombiesRoomRuntimeState(ROOM_ID);
        state.configureMaxWave(1);
        state.transitionTo(ZombiesGamePhase.INTERMISSION);
        state.transitionTo(ZombiesGamePhase.WAVE_ACTIVE);
        state.waveState().markWaveComplete();

        ZombiesPhaseStateMachine.TickResult failureResult = ZombiesPhaseStateMachine.tick(
                state,
                new ZombiesPhaseStateMachine.Config(1, 1, 1),
                ignored -> ZombiesPhaseStateMachine.FailureCheckResult.failed(ZombiesErrorCode.PLAYER_DEAD),
                ignored -> true);

        require(failureResult.nextPhase().orElseThrow() == ZombiesGamePhase.FAILED,
                "failure priority should choose FAILED over completed-wave victory");
        require(state.lastFailureCode().filter(ZombiesErrorCode.PLAYER_DEAD::equals).isPresent(),
                "failure priority should record the failure code");
        applyTransition(state, failureResult);
        requirePhase(state, ZombiesGamePhase.FAILED, "failure priority should transition to FAILED");

        ZombiesPostGameTeleportService pendingTeleport = new ZombiesPostGameTeleportService();
        ZombiesPostGameTeleportService.TeleportTarget endtp =
                new ZombiesPostGameTeleportService.TeleportTarget("minecraft:overworld", 10, 65, 12, 0.0F, 0.0F);
        ZombiesPostGameTeleportService.CleanupPendingSummary cleanup = pendingTeleport.recordPostGameCleanup(
                ROOM_ID,
                List.of(PLAYER_ONE, PLAYER_TWO),
                List.of(PLAYER_ONE),
                Optional.of(endtp),
                state.phase().name(),
                state.revision());

        require(cleanup.memberCount() == 2, "cleanup should inspect all round members");
        require(cleanup.pendingWritten() == 1, "cleanup should record one offline pending endtp");
        ZombiesPostGameTeleportService.PendingEndTeleport pending =
                pendingTeleport.peekPending(PLAYER_TWO).orElseThrow(() -> new AssertionError("expected pending endtp"));
        require(pending.roomId().equals(ROOM_ID), "pending endtp should retain room id");
        require("FAILED".equals(pending.reason()), "pending endtp should retain failure cleanup reason");
        require(pending.endTeleport().filter(ZombiesPostGameTeleportService.TeleportTarget::hasDimension).isPresent(),
                "pending endtp should retain a usable target");
        require(pendingTeleport.consumePending(PLAYER_TWO).isPresent(), "login recovery should consume pending endtp");
        require(pendingTeleport.pendingCount() == 0, "consumed pending endtp should not remain queued");
    }

    private static void mvp3IntermissionRespawnDecisionPreparesPlayerState() {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        ZombiesPowerService power = new ZombiesPowerService(economy);
        ZombiesBuffService buffs = new ZombiesBuffService(economy, power);
        ZombiesIntermissionRespawnService respawn = new ZombiesIntermissionRespawnService(players, buffs);

        UUID alive = PLAYER_ONE;
        UUID dead = PLAYER_TWO;
        List<UUID> members = List.of(alive, dead);
        players.registerPlayers(members);
        ZombiesPlayerRuntimeState deadState = players.getOrCreate(dead);
        deadState.setPrimaryWeapon(new ZombiesWeaponInstanceState("tacz:m4a1", 2, 1, 1.75D, 101, 210));
        requireSuccess(economy.addPoints(dead, 500.0D), "setup respawn points should succeed");
        requireSuccess(buffs.purchaseBuff(dead, ZombiesBuffType.DOUBLE_AMMO, 100.0D, false),
                "setup double ammo buff should succeed");
        requireSuccess(buffs.purchaseBuff(dead, ZombiesBuffType.DOUBLE_HEALTH, 100.0D, false),
                "setup double health buff should succeed");
        players.markDeadSpectating(dead);
        double pointsBeforeRespawn = deadState.points();

        ZombiesServiceResult<ZombiesIntermissionRespawnService.IntermissionRespawnDecision> decisionResult =
                respawn.selectRespawnCandidates(members, 100L, 40L);
        requireSuccess(decisionResult, "intermission respawn decision should succeed");
        ZombiesIntermissionRespawnService.IntermissionRespawnDecision decision =
                decisionResult.value().orElseThrow();
        require(decision.hasAliveMember(), "online survivor should allow wave-intermission respawn");
        require(decision.respawnPlayerIds().equals(List.of(dead)),
                "only the online dead spectator should be selected for respawn");

        ZombiesServiceResult<ZombiesIntermissionRespawnService.IntermissionRespawnStateChange> prep =
                respawn.prepareStateForRespawn(dead);
        requireSuccess(prep, "respawn state preparation should succeed");
        ZombiesIntermissionRespawnService.IntermissionRespawnStateChange change = prep.value().orElseThrow();
        require(change.clearedBuffs() == 2, "respawn prep should clear transient MVP3 buffs");
        require(change.halvedReserveAmmo(), "respawn prep should trim double-ammo reserve");
        require(deadState.lifeState() == ZombiesLifeState.ALIVE, "respawn prep should mark player alive");
        require(deadState.buffs().isEmpty(), "respawn prep should leave no transient buffs");
        require(deadState.primaryWeapon().orElseThrow().reserveAmmo() == 50,
                "respawn prep should floor-half current reserve ammo");
        requireClose(deadState.points(), pointsBeforeRespawn, "respawn prep should preserve points");
    }

    private static void passStartVote(RoundHarness harness) {
        require(harness.voteService.initiateStartVote(PLAYER_ONE), "ready members should start vote");
        long voteId = harness.activeVoteId();
        require(!harness.voteService.submitVoteResponse(PLAYER_ONE, voteId, true),
                "first unanimous vote response should not pass");
        require(harness.voteService.submitVoteResponse(PLAYER_TWO, voteId, true),
                "second unanimous vote response should pass");
    }

    private static ZombiesServiceResult<Integer> unlockSpawnGroupAtomically(
            ZombiesEconomyService economy,
            ZombiesActiveSpawnGroupService activeGroups,
            UUID playerId,
            int group,
            double cost
    ) {
        return economy.spendAtomically(playerId, cost, ignoredState -> {
            if (!activeGroups.activate(group)) {
                return ZombiesServiceResult.failure(BARRIER_ALREADY_CLEARED);
            }
            return ZombiesServiceResult.success(group);
        });
    }

    private static void tickAndApply(
            ZombiesRoomRuntimeState state,
            ZombiesPhaseStateMachine.Config config,
            ZombiesPhaseStateMachine.FailureCheckResult failure,
            boolean waveComplete
    ) {
        ZombiesPhaseStateMachine.TickResult result = ZombiesPhaseStateMachine.tick(
                state,
                config,
                ignored -> failure,
                ignored -> waveComplete);
        applyTransition(state, result);
    }

    private static void applyTransition(
            ZombiesRoomRuntimeState state,
            ZombiesPhaseStateMachine.TickResult result
    ) {
        result.nextPhase().ifPresent(state::transitionTo);
    }

    private static ZombiesStartupValidationService startupService(Path wavesDirectory) {
        return new ZombiesStartupValidationService(
                new ZombiesMapValidator(ZombiesMapValidationProfile.MVP1_MINIMAL),
                new ZombiesWaveConfigRepository(
                        wavesDirectory,
                        DEFAULTS,
                        new ZombiesWaveValidator()));
    }

    private static ZombiesMapSnapshot validMvp1Map() {
        return ZombiesMapSnapshot.of(
                ROOM_ID,
                ROOM_ID.mapName(),
                true,
                List.of(initialSpawn("initial-1"), zombieSpawn("zombie-1", 1)),
                List.of());
    }

    private static ZombiesMapSnapshot missingInitialSpawnMap() {
        return ZombiesMapSnapshot.of(
                ROOM_ID,
                ROOM_ID.mapName(),
                true,
                List.of(zombieSpawn("zombie-1", 1)),
                List.of());
    }

    private static ZombiesMapSnapshot.SpawnSnapshot initialSpawn(String objectId) {
        return new ZombiesMapSnapshot.SpawnSnapshot(objectId, "spawn", "INITIAL", 0, 0.0D, false);
    }

    private static ZombiesMapSnapshot.SpawnSnapshot zombieSpawn(String objectId, int group) {
        return new ZombiesMapSnapshot.SpawnSnapshot(objectId, "zombieSpawn", "", group, 1.0D, true);
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

    private static void requireIssue(
            ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result,
            String code
    ) {
        require(!result.success(), "expected preflight failure for " + code);
        ZombiesStartupPreflightSnapshot snapshot = result.value()
                .orElseThrow(() -> new AssertionError("preflight failure should carry snapshot"));
        require(snapshot.issues().stream().anyMatch(issue -> code.equals(issue.code().key())),
                "expected preflight issue " + code + " but got " + snapshot.issues());
    }

    private static void requirePhase(ZombiesRoomRuntimeState state, ZombiesGamePhase expected, String message) {
        require(state.phase() == expected, message + ": expected " + expected + " but was " + state.phase());
    }

    private static void requirePrimary(
            ZombiesPlayerStateService players,
            UUID playerId,
            String expectedGunId,
            int expectedLevel,
            String message
    ) {
        ZombiesWeaponInstanceState primary = players.get(playerId)
                .flatMap(ZombiesPlayerRuntimeState::primaryWeapon)
                .orElseThrow(() -> new AssertionError(message + ": expected primary weapon"));
        require(expectedGunId.equals(primary.gunId()), message + ": expected gun " + expectedGunId);
        require(primary.weaponLevel() == expectedLevel,
                message + ": expected level " + expectedLevel + " but was " + primary.weaponLevel());
    }

    private static void requirePoints(
            ZombiesPlayerStateService players,
            UUID playerId,
            double expected,
            String message
    ) {
        ZombiesPlayerRuntimeState state = players.get(playerId)
                .orElseThrow(() -> new AssertionError(message + ": missing player state"));
        requireClose(state.points(), expected, message);
    }

    private static void requireSuccess(ZombiesServiceResult<?> result, String message) {
        require(result.success(), message + ": " + result.code());
    }

    private static void requireFailure(ZombiesServiceResult<?> result, ZombiesErrorCode expectedCode, String message) {
        require(!result.success(), message + ": expected failure");
        require(expectedCode.equals(result.code()),
                message + ": expected " + expectedCode + " but was " + result.code());
    }

    private static void requireClose(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 0.000001D,
                message + ": expected " + expected + " but was " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static UUID playerId(int suffix) {
        return new UUID(0L, suffix);
    }

    private static final class RoundHarness {
        private final Set<UUID> members = new LinkedHashSet<>();
        private final ZombiesRoomRuntimeState state = new ZombiesRoomRuntimeState(ROOM_ID);
        private final ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        private final ZombiesReadyService readyService;
        private final ZombiesStartVoteService voteService;
        private final ZombiesStartupValidationService startupService;
        private final ZombiesMapSnapshot mapSnapshot;
        private final List<ZombiesStartVoteService.VoteSnapshot> passedSnapshots = new ArrayList<>();
        private Optional<ZombiesServiceResult<ZombiesStartupPreflightSnapshot>> lastPreflightResult = Optional.empty();
        private int preflightAttempts;
        private int preflightSuccesses;
        private int preflightFailures;
        private int dirtyCount;

        private RoundHarness(Path wavesDirectory, ZombiesMapSnapshot mapSnapshot) {
            this.startupService = startupService(wavesDirectory);
            this.mapSnapshot = mapSnapshot;
            this.readyService = new ZombiesReadyService(new ZombiesReadyService.Hooks() {
                @Override
                public boolean isWaitingPhase() {
                    return state.phase() == ZombiesGamePhase.WAITING;
                }

                @Override
                public void markRoomListDirty() {
                    dirtyCount++;
                }
            });
            this.voteService = new ZombiesStartVoteService(new VoteHooks());
        }

        private void addMembers(UUID... playerIds) {
            members.addAll(List.of(playerIds));
        }

        private void readyAll() {
            for (UUID member : members) {
                require(readyService.setPlayerReady(member, true), "member should become ready: " + member);
            }
        }

        private long activeVoteId() {
            return voteService.activeVoteSnapshot()
                    .orElseThrow(() -> new AssertionError("expected active vote"))
                    .voteId();
        }

        private void clearRoundRuntime() {
            players.clear();
            readyService.clear();
            voteService.clearActiveVoteSession();
        }

        private final class VoteHooks implements ZombiesStartVoteService.Hooks {
            @Override
            public Collection<UUID> currentMembers() {
                return List.copyOf(members);
            }

            @Override
            public boolean isWaitingPhase() {
                return state.phase() == ZombiesGamePhase.WAITING;
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

            @Override
            public void onVoteStarted(ZombiesStartVoteService.VoteSnapshot snapshot) {
                state.transitionTo(ZombiesGamePhase.START_VOTE);
            }

            @Override
            public void onVotePassed(ZombiesStartVoteService.VoteSnapshot snapshot) {
                passedSnapshots.add(snapshot);
                preflightAttempts++;
                ZombiesServiceResult<ZombiesStartupPreflightSnapshot> preflight =
                        startupService.preflight(mapSnapshot);
                lastPreflightResult = Optional.of(preflight);
                if (!preflight.success()) {
                    preflightFailures++;
                    state.transitionTo(ZombiesGamePhase.WAITING);
                    return;
                }

                preflightSuccesses++;
                ZombiesStartupPreflightSnapshot preflightSnapshot = preflight.value()
                        .orElseThrow(() -> new AssertionError("successful preflight should carry snapshot"));
                players.registerPlayers(snapshot.members().stream().toList());
                state.configureMaxWave(preflightSnapshot.maxWave());
                state.transitionTo(ZombiesGamePhase.OPENING_COUNTDOWN);
            }

            @Override
            public void onVoteFailed(
                    ZombiesStartVoteService.VoteSnapshot snapshot,
                    ZombiesStartVoteService.FailureReason reason
            ) {
                state.transitionTo(ZombiesGamePhase.WAITING);
            }

            @Override
            public void markRoomListDirty() {
                dirtyCount++;
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws IOException;
    }
}
