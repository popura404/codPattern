package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ZombiesBarrierService {
    private static final ZombiesErrorCode OBJECT_PHASE_LOCKED = ZombiesErrorCode.of("object.phase_locked");

    private final RoomId roomId;
    private final Supplier<Collection<ZombiesBarrierData>> barriersSupplier;
    private final ZombiesEconomyService economyService;
    private final ZombiesObjectStateStore objectStateStore;
    private final ZombiesActiveSpawnGroupService activeSpawnGroupService;
    private final Predicate<UUID> roomMemberPredicate;
    private final Supplier<ZombiesGamePhase> phaseSupplier;
    private final Consumer<BarrierPurchaseResult> purchaseSuccessListener;

    public ZombiesBarrierService(
            RoomId roomId,
            Supplier<Collection<ZombiesBarrierData>> barriersSupplier,
            ZombiesEconomyService economyService,
            ZombiesObjectStateStore objectStateStore,
            ZombiesActiveSpawnGroupService activeSpawnGroupService,
            Predicate<UUID> roomMemberPredicate,
            Supplier<ZombiesGamePhase> phaseSupplier
    ) {
        this(
                roomId,
                barriersSupplier,
                economyService,
                objectStateStore,
                activeSpawnGroupService,
                roomMemberPredicate,
                phaseSupplier,
                ignored -> {
                });
    }

    public ZombiesBarrierService(
            RoomId roomId,
            Supplier<Collection<ZombiesBarrierData>> barriersSupplier,
            ZombiesEconomyService economyService,
            ZombiesObjectStateStore objectStateStore,
            ZombiesActiveSpawnGroupService activeSpawnGroupService,
            Predicate<UUID> roomMemberPredicate,
            Supplier<ZombiesGamePhase> phaseSupplier,
            Consumer<BarrierPurchaseResult> purchaseSuccessListener
    ) {
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.barriersSupplier = Objects.requireNonNull(barriersSupplier, "barriersSupplier");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.objectStateStore = Objects.requireNonNull(objectStateStore, "objectStateStore");
        this.activeSpawnGroupService = Objects.requireNonNull(activeSpawnGroupService, "activeSpawnGroupService");
        this.roomMemberPredicate = roomMemberPredicate == null ? ignored -> false : roomMemberPredicate;
        this.phaseSupplier = phaseSupplier == null ? () -> ZombiesGamePhase.WAITING : phaseSupplier;
        this.purchaseSuccessListener = purchaseSuccessListener == null ? ignored -> {
        } : purchaseSuccessListener;
    }

    public ZombiesServiceResult<BarrierPurchaseResult> purchase(ServerPlayer player, ZombiesBarrierData barrier) {
        if (player == null || barrier == null) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.OBJECT_NOT_FOUND);
        }
        UUID playerId = player.getUUID();
        if (!roomMemberPredicate.test(playerId)) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.OBJECT_ROOM_MISMATCH);
        }
        if (!player.level().dimension().equals(barrier.dimension())) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.OBJECT_OUT_OF_RANGE);
        }
        ZombiesGamePhase phase = phaseSupplier.get();
        if (phase == null || !phase.allowsPurchases()) {
            return ZombiesServiceResult.failure(OBJECT_PHASE_LOCKED);
        }

        return economyService.spendAtomically(playerId, barrier.cost(), ignoredState -> {
            ZombiesServiceResult<ZombiesObjectStateStore.BarrierGroupUpdate> clearResult =
                    objectStateStore.clearBarrierGroup(barrier.group(), barriersSupplier.get());
            if (!clearResult.success()) {
                return ZombiesServiceResult.failure(clearResult.code(), clearResult.params(), clearResult.logMessage());
            }

            ZombiesObjectStateStore.BarrierGroupUpdate update = clearResult.value().orElseThrow();
            activeSpawnGroupService.activate(update.group());
            economyService.recordBarrierOpened(playerId);
            BarrierPurchaseResult purchase = new BarrierPurchaseResult(
                    roomId,
                    update.group(),
                    update.objectIds(),
                    update.revision());
            purchaseSuccessListener.accept(purchase);
            return ZombiesServiceResult.success(purchase);
        });
    }

    public record BarrierPurchaseResult(
            RoomId roomId,
            int group,
            Collection<String> clearedObjectIds,
            long revision
    ) {
        public BarrierPurchaseResult {
            Objects.requireNonNull(roomId, "roomId");
            clearedObjectIds = clearedObjectIds == null ? List.of() : List.copyOf(clearedObjectIds);
            revision = Math.max(0L, revision);
        }
    }
}
