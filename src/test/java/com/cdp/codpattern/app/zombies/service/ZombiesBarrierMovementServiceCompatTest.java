package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

public final class ZombiesBarrierMovementServiceCompatTest {
    private ZombiesBarrierMovementServiceCompatTest() {
    }

    public static void main(String[] args) {
        activeUnclearedBarrierRestoresLastLegalPosition();
        crossingThinBarrierRestoresLastLegalPosition();
        clearedWaitingEndingDeadAndNonBlockingBarriersDoNotBlock();
        missingLastLegalPositionFallsBackOutsideBarrier();
    }

    private static void activeUnclearedBarrierRestoresLastLegalPosition() {
        ZombiesBarrierMovementService service = new ZombiesBarrierMovementService();
        ZombiesBarrierData barrier = barrier("barrier-2-a", true);
        ZombiesBarrierMovementService.LegalPosition previous = legal(4.30D, 65.0D, 6.0D);
        ZombiesBarrierMovementService.PositionSample current = sample(5.30D, 65.0D, 6.0D);

        ZombiesBarrierMovementService.MovementDecision decision = service.decideMovement(
                current,
                ZombiesGamePhase.WAVE_ACTIVE,
                List.of(barrier),
                ignored -> false,
                true,
                Optional.of(previous));

        require(decision.eligible(), "active round survivor should be eligible for barrier movement checks");
        require(decision.blocked(), "active uncleared barrier should block player body entering its area");
        require(previous.equals(decision.target().orElseThrow()),
                "blocked movement should restore the last legal position");
        require("barrier-2-a".equals(decision.barrierObjectId()),
                "decision should expose the blocking barrier object id");
    }

    private static void crossingThinBarrierRestoresLastLegalPosition() {
        ZombiesBarrierMovementService service = new ZombiesBarrierMovementService();
        ZombiesBarrierData barrier = barrier("barrier-2-cross", true);
        ZombiesBarrierMovementService.LegalPosition previous = legal(4.30D, 65.0D, 6.0D);
        ZombiesBarrierMovementService.PositionSample current = sample(6.70D, 65.0D, 6.0D);

        ZombiesBarrierMovementService.MovementDecision decision = service.decideMovement(
                current,
                ZombiesGamePhase.INTERMISSION,
                List.of(barrier),
                ignored -> false,
                true,
                Optional.of(previous));

        require(decision.blocked(), "movement segment crossing a thin barrier should be blocked");
        require(previous.equals(decision.target().orElseThrow()),
                "crossing a barrier should push the player back to the last legal position");
    }

    private static void clearedWaitingEndingDeadAndNonBlockingBarriersDoNotBlock() {
        ZombiesBarrierMovementService service = new ZombiesBarrierMovementService();
        ZombiesBarrierData blockingBarrier = barrier("barrier-2-clear", true);
        ZombiesBarrierData nonBlockingBarrier = barrier("barrier-2-decoration", false);
        ZombiesBarrierMovementService.PositionSample inside = sample(5.30D, 65.0D, 6.0D);

        requireAllowed(service.decideMovement(
                        inside,
                        ZombiesGamePhase.WAVE_ACTIVE,
                        List.of(blockingBarrier),
                        ignored -> true,
                        true,
                        Optional.empty()),
                "cleared barrier should no longer block");
        requireIgnored(service.decideMovement(
                        inside,
                        ZombiesGamePhase.WAITING,
                        List.of(blockingBarrier),
                        ignored -> false,
                        true,
                        Optional.empty()),
                "WAITING phase should not affect ordinary room players");
        requireIgnored(service.decideMovement(
                        inside,
                        ZombiesGamePhase.ENDING,
                        List.of(blockingBarrier),
                        ignored -> false,
                        true,
                        Optional.empty()),
                "ENDING phase should not rubber-band players");
        requireIgnored(service.decideMovement(
                        inside,
                        ZombiesGamePhase.WAVE_ACTIVE,
                        List.of(blockingBarrier),
                        ignored -> false,
                        false,
                        Optional.empty()),
                "dead or non-member player should not be blocked");
        requireAllowed(service.decideMovement(
                        inside,
                        ZombiesGamePhase.WAVE_ACTIVE,
                        List.of(nonBlockingBarrier),
                        ignored -> false,
                        true,
                        Optional.empty()),
                "barrier with blocksPlayersOnly=false should not participate in player blocking");
    }

    private static void missingLastLegalPositionFallsBackOutsideBarrier() {
        ZombiesBarrierMovementService service = new ZombiesBarrierMovementService();
        ZombiesBarrierData barrier = barrier("barrier-2-fallback", true);
        ZombiesBarrierMovementService.PositionSample current = sample(5.30D, 65.0D, 6.0D);

        ZombiesBarrierMovementService.MovementDecision decision = service.decideMovement(
                current,
                ZombiesGamePhase.OPENING_COUNTDOWN,
                List.of(barrier),
                ignored -> false,
                true,
                Optional.empty());

        require(decision.blocked(), "active barrier should still block without a cached last legal position");
        ZombiesBarrierMovementService.LegalPosition fallback = decision.target().orElseThrow();
        requireAllowed(service.decideMovement(
                        fallback.toPositionSample(),
                        ZombiesGamePhase.OPENING_COUNTDOWN,
                        List.of(barrier),
                        ignored -> false,
                        true,
                        Optional.empty()),
                "fallback target should be outside all active barrier areas");
    }

    private static ZombiesBarrierMovementService.PositionSample sample(double x, double y, double z) {
        return new ZombiesBarrierMovementService.PositionSample(dimension(), x, y, z, 0.0F, 0.0F);
    }

    private static ZombiesBarrierMovementService.LegalPosition legal(double x, double y, double z) {
        return new ZombiesBarrierMovementService.LegalPosition(dimension(), x, y, z, 0.0F, 0.0F);
    }

    private static ZombiesBarrierData barrier(String objectId, boolean blocksPlayersOnly) {
        return new ZombiesBarrierData(
                objectId,
                2,
                750,
                blocksPlayersOnly,
                dimension(),
                new BlockPos(5, 64, 5),
                new BlockPos(5, 66, 7),
                new BlockPos(5, 65, 5));
    }

    private static void requireAllowed(
            ZombiesBarrierMovementService.MovementDecision decision,
            String message
    ) {
        require(decision.eligible(), message + ": expected eligible decision");
        require(!decision.blocked(), message + ": expected movement to be allowed");
    }

    private static void requireIgnored(
            ZombiesBarrierMovementService.MovementDecision decision,
            String message
    ) {
        require(!decision.eligible(), message + ": expected movement check to be ignored");
        require(!decision.blocked(), message + ": ignored movement should not be blocked");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResourceKey<Level> dimension() {
        try {
            Constructor<ResourceKey> constructor =
                    ResourceKey.class.getDeclaredConstructor(ResourceLocation.class, ResourceLocation.class);
            constructor.setAccessible(true);
            return (ResourceKey<Level>) constructor.newInstance(
                    resourceLocation("minecraft:dimension"),
                    resourceLocation("minecraft:overworld"));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("failed to create test dimension key", exception);
        }
    }

    private static ResourceLocation resourceLocation(String value) {
        ResourceLocation location = ResourceLocation.tryParse(value);
        if (location == null) {
            throw new AssertionError("invalid resource location " + value);
        }
        return location;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
