package com.cdp.codpattern.app.match.runtime;

import com.cdp.codpattern.app.match.runtime.lease.ModeMapLeaseRegistry;
import com.cdp.codpattern.app.match.runtime.lifecycle.CleanupCoordinator;
import com.cdp.codpattern.app.match.runtime.player.DeferredPlayerActionRegistry;
import com.cdp.codpattern.app.match.runtime.player.PlayerGracePeriodRegistry;
import com.cdp.codpattern.app.match.runtime.player.ModePlayerSessionMarker;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.transaction.RollbackStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class Phase3RuntimePrimitivesCompatTest {
    private static final UUID PLAYER = UUID.fromString("52000000-0000-0000-0000-000000000001");

    private Phase3RuntimePrimitivesCompatTest() {
    }

    public static void main(String[] args) {
        cleanupOrdersParticipantsAndFinalizesOnlySuccess();
        cleanupFailureRetainsFinalizerState();
        leaseTokensRejectStaleReleaseAndSupportRecoveryOperations();
        deferredActionsAndGracePeriodsRemainPlayerScoped();
        sessionMarkersKeepModePayloadsOutsideNeutralState();
        rollbackCompensatesInReverseOrderAndRecordsFailures();
    }

    private static void cleanupOrdersParticipantsAndFinalizesOnlySuccess() {
        List<String> calls = new ArrayList<>();
        CleanupCoordinator<String, String, String> coordinator = new CleanupCoordinator<>(List.of(
                participant("late", 20, true, calls),
                participant("early", 10, true, calls)),
                CleanupCoordinator.FailurePolicy.noop(),
                context -> {
                    calls.add("finalizer");
                    return context + "-done";
                });

        CleanupCoordinator.Result<String, String> result = coordinator.execute("room");
        require(result.success(), "all successful cleanup participants should finalize");
        require(calls.equals(List.of("early", "late", "finalizer")),
                "cleanup participant order and finalizer position must stay deterministic");
        require(result.summary().orElseThrow().equals("room-done"), "cleanup summary should be retained");
    }

    private static void cleanupFailureRetainsFinalizerState() {
        List<String> calls = new ArrayList<>();
        CleanupCoordinator<String, String, String> coordinator = new CleanupCoordinator<>(List.of(
                participant("entities-already-cleaned", 0, true, calls),
                participant("pending-recovery", 10, false, calls),
                participant("dependent-clear", 20, true, calls)),
                (context, participant, failure, completed) -> calls.add("failure:" + participant),
                context -> {
                    calls.add("finalizer");
                    return "released";
                });

        CleanupCoordinator.Result<String, String> result = coordinator.execute("room");
        require(!result.success(), "participant failure should stop cleanup");
        require(calls.equals(List.of("entities-already-cleaned", "pending-recovery", "failure:pending-recovery")),
                "dependent clears and release must remain retained after participant failure");
        require("failure:pending-recovery".equals(result.failure().orElseThrow()),
                "the original participant failure should be preserved");
    }

    private static void leaseTokensRejectStaleReleaseAndSupportRecoveryOperations() {
        ModeMapLeaseRegistry registry = new ModeMapLeaseRegistry();
        ModeMapLeaseRegistry.LeaseKey key = new ModeMapLeaseRegistry.LeaseKey("zombies", "map-a");
        ModeMapLeaseRegistry.AcquireResult first = registry.acquire(key, "room-a");
        ModeMapLeaseRegistry.AcquireResult repeated = registry.acquire(key, "room-a");
        ModeMapLeaseRegistry.AcquireResult rejected = registry.acquire(key, "room-b");

        require(first.status() == ModeMapLeaseRegistry.AcquireStatus.ACQUIRED, "first acquire should succeed");
        require(repeated.status() == ModeMapLeaseRegistry.AcquireStatus.ALREADY_HELD,
                "same-owner repeated acquire should be idempotent");
        require(repeated.lease().equals(first.lease()), "repeated acquire should retain the lifecycle token");
        require(!rejected.acquired(), "another owner must not acquire an active lease");
        require(registry.release(first.lease()), "token-matched normal release should succeed");
        require(!registry.release(first.lease()), "normal release should be idempotent");

        ModeMapLeaseRegistry.Lease newer = registry.acquire(key, "room-b").lease();
        require(newer.generation() > first.lease().generation(), "reacquire should advance the generation");
        require(!registry.release(first.lease()), "a stale token must not release a newer lifecycle");
        require(registry.current(key).orElseThrow().equals(newer), "newer lifecycle must remain active");
        require(registry.forceInvalidate(key).orElseThrow().equals(newer),
                "administrative force invalidation should return the invalidated lease");
        registry.acquire(key, "room-c");
        require(registry.clear() == 1 && registry.size() == 0,
                "full ephemeral-registry clearing should report and remove all leases");
    }

    private static void deferredActionsAndGracePeriodsRemainPlayerScoped() {
        DeferredPlayerActionRegistry<String> deferred = new DeferredPlayerActionRegistry<>();
        require(deferred.put(PLAYER, "teleport").isEmpty(), "first deferred action should have no replacement");
        require(deferred.peek(PLAYER).orElseThrow().equals("teleport"), "deferred action should be inspectable");
        require(deferred.consume(PLAYER).orElseThrow().equals("teleport"), "deferred action should consume once");
        require(deferred.consume(PLAYER).isEmpty(), "consumed action must not replay");

        PlayerGracePeriodRegistry grace = new PlayerGracePeriodRegistry();
        grace.start(PLAYER, 0);
        require(grace.mutableTimers().get(PLAYER) == 1, "grace periods should retain the existing one-tick minimum");
        grace.clear(PLAYER);
        require(grace.mutableTimers().isEmpty(), "grace clear should remain player-scoped");
    }

    private static void sessionMarkersKeepModePayloadsOutsideNeutralState() {
        ModePlayerSessionMarker<String> marker = new ModePlayerSessionMarker<>(
                RoomId.of("zombies", "MixedCaseMap"),
                "pending_endtp",
                Optional.of("addon-owned-target"));
        require(marker.roomId().mapName().equals("MixedCaseMap"),
                "neutral session markers must preserve public room-id case");
        require(marker.hasState("pending_endtp"), "session state identity should be value-based");
        require(marker.recoveryTarget().orElseThrow().equals("addon-owned-target"),
                "mode-owned recovery payload should remain generic to the neutral marker");
    }

    private static void rollbackCompensatesInReverseOrderAndRecordsFailures() {
        List<String> calls = new ArrayList<>();
        RollbackStack<String, String> stack = new RollbackStack<>();
        stack.push(action("first", calls, false));
        stack.push(action("second", calls, true));

        RollbackStack.Report<String> report = stack.rollback(
                "startup",
                "ok"::equals,
                exception -> "exception:" + exception.getClass().getSimpleName());

        require(calls.equals(List.of("second", "first")), "rollback must compensate in reverse order");
        require(report.steps().size() == 2, "every rollback step should be reported");
        require(!report.success(), "a rollback failure should be preserved without stopping later compensation");
        require(stack.size() == 0, "rollback should consume the compensation stack");
    }

    private static CleanupCoordinator.Participant<String, String> participant(
            String name,
            int order,
            boolean success,
            List<String> calls
    ) {
        return new CleanupCoordinator.Participant<>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public CleanupCoordinator.ParticipantResult<String> cleanup(String context) {
                calls.add(name);
                return success
                        ? CleanupCoordinator.ParticipantResult.completed()
                        : CleanupCoordinator.ParticipantResult.failed("failure:" + name);
            }
        };
    }

    private static RollbackStack.Action<String, String> action(
            String name,
            List<String> calls,
            boolean success
    ) {
        return new RollbackStack.Action<>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String rollback(String context) {
                calls.add(name);
                return success ? "ok" : "failed";
            }
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
