package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModeObjectInteractionContext;
import com.cdp.codpattern.app.match.model.ModeObjectState;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeInteractableObjectPort;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public final class ZombiesObjectInteractionService implements ModeInteractableObjectPort {
    private static final double DEFAULT_INTERACTION_DISTANCE = 4.5D;
    private static final double MAX_INTERACTION_DISTANCE = 6.0D;

    private final RoomId roomId;
    private final Supplier<Collection<ZombiesBarrierData>> barriersSupplier;
    private final ZombiesBarrierService barrierService;
    private final ZombiesObjectStateStore objectStateStore;
    private final ConcurrentMap<InteractionKey, Long> recentInteractions = new ConcurrentHashMap<>();

    public ZombiesObjectInteractionService(
            RoomId roomId,
            Supplier<Collection<ZombiesBarrierData>> barriersSupplier,
            ZombiesBarrierService barrierService,
            ZombiesObjectStateStore objectStateStore
    ) {
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.barriersSupplier = Objects.requireNonNull(barriersSupplier, "barriersSupplier");
        this.barrierService = Objects.requireNonNull(barrierService, "barrierService");
        this.objectStateStore = Objects.requireNonNull(objectStateStore, "objectStateStore");
    }

    @Override
    public RoomId roomId() {
        return roomId;
    }

    @Override
    public String gameType() {
        return roomId.gameType();
    }

    @Override
    public String mapName() {
        return roomId.mapName();
    }

    @Override
    public String modeDisplayNameKey() {
        return GameModeRegistry.getOrDefault(gameType()).displayNameKey();
    }

    @Override
    public InteractionResult interact(ServerPlayer player, ModeObjectInteractionContext context) {
        if (player == null || context == null || !roomId.equals(context.roomId())) {
            return InteractionResult.PASS;
        }

        Optional<ZombiesBarrierData> barrier = findBarrier(player, context);
        if (barrier.isEmpty()) {
            return InteractionResult.PASS;
        }

        long gameTime = Math.max(0L, player.level().getGameTime());
        cleanupRecentInteractions(gameTime);
        InteractionKey key = new InteractionKey(
                player.getUUID(),
                ZombiesObjectStateStore.objectKey(barrier.get()),
                gameTime);
        if (recentInteractions.putIfAbsent(key, gameTime) != null) {
            return InteractionResult.SUCCESS;
        }

        ZombiesServiceResult<ZombiesBarrierService.BarrierPurchaseResult> result =
                barrierService.purchase(player, barrier.get());
        return result.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override
    public List<ModeObjectState> objectStatesForClient(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        return objectStateStore.barrierStates(barriersSupplier.get());
    }

    private Optional<ZombiesBarrierData> findBarrier(ServerPlayer player, ModeObjectInteractionContext context) {
        List<ZombiesBarrierData> candidates = barriersSupplier.get().stream()
                .filter(Objects::nonNull)
                .filter(barrier -> player.level().dimension().equals(barrier.dimension()))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        BlockPos clickedPos = context.blockPos();
        if (clickedPos != null) {
            return candidates.stream()
                    .filter(barrier -> clickedPos.equals(barrier.interactionPos()))
                    .findFirst();
        }

        double maxDistance = Math.min(DEFAULT_INTERACTION_DISTANCE, MAX_INTERACTION_DISTANCE);
        double maxDistanceSqr = maxDistance * maxDistance;
        Vec3 playerPos = player.position();
        return candidates.stream()
                .filter(barrier -> distanceToInteractionSqr(playerPos, barrier) <= maxDistanceSqr)
                .min(Comparator.comparingDouble(barrier -> distanceToInteractionSqr(playerPos, barrier)));
    }

    private void cleanupRecentInteractions(long gameTime) {
        if (gameTime % 200L != 0L) {
            return;
        }
        long cutoff = Math.max(0L, gameTime - 20L);
        recentInteractions.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    private static double distanceToInteractionSqr(Vec3 playerPos, ZombiesBarrierData barrier) {
        BlockPos interactionPos = barrier.interactionPos();
        Vec3 targetPos = Vec3.atCenterOf(interactionPos == null ? BlockPos.ZERO : interactionPos);
        return playerPos.distanceToSqr(targetPos);
    }

    private record InteractionKey(
            UUID playerId,
            String objectId,
            long gameTime
    ) {
    }
}
