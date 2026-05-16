package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.ModeObjectInteractionContext;
import com.cdp.codpattern.app.match.model.ModeObjectState;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeInteractableObjectPort;
import com.cdp.codpattern.app.zombies.map.object.ZombiesAmmoBoxData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesArmorStationData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesPowerSwitchData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesSodaMachineData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesUltimateMachineData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesWeaponWallData;
import com.cdp.codpattern.app.zombies.model.ZombiesEquipmentSlot;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public final class ZombiesObjectInteractionService implements ModeInteractableObjectPort {
    private static final double DEFAULT_INTERACTION_DISTANCE = 4.5D;
    private static final double MAX_INTERACTION_DISTANCE = 6.0D;
    private static final String MESSAGE_PREFIX = "message.codpattern.zombies.interaction.";
    private static final String FAILURE_DEAD = MESSAGE_PREFIX + "failure.dead";
    private static final String FAILURE_ROOM_MISMATCH = MESSAGE_PREFIX + "failure.room_mismatch";
    private static final String FAILURE_NOT_IN_ROOM = MESSAGE_PREFIX + "failure.not_in_room";
    private static final String FAILURE_OBJECT_NOT_FOUND = MESSAGE_PREFIX + "failure.object_not_found";
    private static final String FAILURE_OBJECT_OUT_OF_RANGE = MESSAGE_PREFIX + "failure.object_out_of_range";
    private static final String FAILURE_NOT_ENOUGH_POINTS = MESSAGE_PREFIX + "failure.not_enough_points";
    private static final String FAILURE_BARRIER_ALREADY_CLEARED = MESSAGE_PREFIX + "failure.barrier_already_cleared";
    private static final String FAILURE_WEAPON_ALREADY_OWNED = MESSAGE_PREFIX + "failure.weapon_already_owned";
    private static final String FAILURE_AMMO_ALREADY_FULL = MESSAGE_PREFIX + "failure.ammo_already_full";
    private static final String FAILURE_ARMOR_ALREADY_OWNED = MESSAGE_PREFIX + "failure.armor_already_owned";
    private static final String FAILURE_SODA_ALREADY_OWNED = MESSAGE_PREFIX + "failure.soda_already_owned";
    private static final String FAILURE_REQUIRES_POWER = MESSAGE_PREFIX + "failure.requires_power";
    private static final String FAILURE_INVALID_CURRENT_WEAPON = MESSAGE_PREFIX + "failure.invalid_current_weapon";
    private static final String FAILURE_MAX_UPGRADE = MESSAGE_PREFIX + "failure.max_upgrade";
    private static final String FAILURE_GENERIC = MESSAGE_PREFIX + "failure.generic";
    private static final String NOTICE_POWER_ALREADY_ON = MESSAGE_PREFIX + "notice.power_already_on";
    private static final String SUCCESS_BARRIER = MESSAGE_PREFIX + "success.barrier";
    private static final String SUCCESS_WEAPON_WALL = MESSAGE_PREFIX + "success.weapon_wall";
    private static final String SUCCESS_AMMO = MESSAGE_PREFIX + "success.ammo";
    private static final String SUCCESS_ARMOR = MESSAGE_PREFIX + "success.armor";
    private static final String SUCCESS_POWER = MESSAGE_PREFIX + "success.power";
    private static final String SUCCESS_SODA = MESSAGE_PREFIX + "success.soda";
    private static final String SUCCESS_ULTIMATE = MESSAGE_PREFIX + "success.ultimate";
    private static final ZombiesErrorCode BARRIER_ALREADY_CLEARED = ZombiesErrorCode.of("barrier.already_cleared");
    private static final ZombiesErrorCode AMMO_ALREADY_FULL = ZombiesErrorCode.of("ammo.already_full");

    private final RoomId roomId;
    private final Supplier<Collection<ZombiesBarrierData>> barriersSupplier;
    private final Supplier<Collection<ZombiesWeaponWallData>> weaponWallsSupplier;
    private final Supplier<Collection<ZombiesAmmoBoxData>> ammoBoxesSupplier;
    private final Supplier<Collection<ZombiesArmorStationData>> armorStationsSupplier;
    private final Supplier<Optional<ZombiesPowerSwitchData>> powerSwitchSupplier;
    private final Supplier<Collection<ZombiesSodaMachineData>> sodaMachinesSupplier;
    private final Supplier<Collection<ZombiesUltimateMachineData>> ultimateMachinesSupplier;
    private final ZombiesBarrierService barrierService;
    private final ZombiesWeaponInstanceService weaponInstanceService;
    private final ZombiesAmmoBoxService ammoBoxService;
    private final ZombiesArmorService armorService;
    private final ZombiesPowerService powerService;
    private final ZombiesBuffService buffService;
    private final ZombiesUltimateMachineService ultimateMachineService;
    private final ZombiesWeaponInventoryService weaponInventoryService;
    private final ZombiesObjectStateStore objectStateStore;
    private final ZombiesBarrierBlockRuntimeService barrierBlockRuntimeService;
    private final ConcurrentMap<InteractionKey, Long> recentInteractions = new ConcurrentHashMap<>();

    public ZombiesObjectInteractionService(
            RoomId roomId,
            Supplier<Collection<ZombiesBarrierData>> barriersSupplier,
            Supplier<Collection<ZombiesWeaponWallData>> weaponWallsSupplier,
            Supplier<Collection<ZombiesAmmoBoxData>> ammoBoxesSupplier,
            Supplier<Collection<ZombiesArmorStationData>> armorStationsSupplier,
            Supplier<Optional<ZombiesPowerSwitchData>> powerSwitchSupplier,
            Supplier<Collection<ZombiesSodaMachineData>> sodaMachinesSupplier,
            Supplier<Collection<ZombiesUltimateMachineData>> ultimateMachinesSupplier,
            ZombiesBarrierService barrierService,
            ZombiesWeaponInstanceService weaponInstanceService,
            ZombiesAmmoBoxService ammoBoxService,
            ZombiesArmorService armorService,
            ZombiesPowerService powerService,
            ZombiesBuffService buffService,
            ZombiesUltimateMachineService ultimateMachineService,
            ZombiesObjectStateStore objectStateStore
    ) {
        this(
                roomId,
                barriersSupplier,
                weaponWallsSupplier,
                ammoBoxesSupplier,
                armorStationsSupplier,
                powerSwitchSupplier,
                sodaMachinesSupplier,
                ultimateMachinesSupplier,
                barrierService,
                weaponInstanceService,
                ammoBoxService,
                armorService,
                powerService,
                buffService,
                ultimateMachineService,
                new ZombiesWeaponInventoryService(),
                objectStateStore,
                ZombiesBarrierBlockRuntimeService.instance());
    }

    public ZombiesObjectInteractionService(
            RoomId roomId,
            Supplier<Collection<ZombiesBarrierData>> barriersSupplier,
            Supplier<Collection<ZombiesWeaponWallData>> weaponWallsSupplier,
            Supplier<Collection<ZombiesAmmoBoxData>> ammoBoxesSupplier,
            Supplier<Collection<ZombiesArmorStationData>> armorStationsSupplier,
            Supplier<Optional<ZombiesPowerSwitchData>> powerSwitchSupplier,
            Supplier<Collection<ZombiesSodaMachineData>> sodaMachinesSupplier,
            Supplier<Collection<ZombiesUltimateMachineData>> ultimateMachinesSupplier,
            ZombiesBarrierService barrierService,
            ZombiesWeaponInstanceService weaponInstanceService,
            ZombiesAmmoBoxService ammoBoxService,
            ZombiesArmorService armorService,
            ZombiesPowerService powerService,
            ZombiesBuffService buffService,
            ZombiesUltimateMachineService ultimateMachineService,
            ZombiesWeaponInventoryService weaponInventoryService,
            ZombiesObjectStateStore objectStateStore
    ) {
        this(
                roomId,
                barriersSupplier,
                weaponWallsSupplier,
                ammoBoxesSupplier,
                armorStationsSupplier,
                powerSwitchSupplier,
                sodaMachinesSupplier,
                ultimateMachinesSupplier,
                barrierService,
                weaponInstanceService,
                ammoBoxService,
                armorService,
                powerService,
                buffService,
                ultimateMachineService,
                weaponInventoryService,
                objectStateStore,
                ZombiesBarrierBlockRuntimeService.instance());
    }

    public ZombiesObjectInteractionService(
            RoomId roomId,
            Supplier<Collection<ZombiesBarrierData>> barriersSupplier,
            Supplier<Collection<ZombiesWeaponWallData>> weaponWallsSupplier,
            Supplier<Collection<ZombiesAmmoBoxData>> ammoBoxesSupplier,
            Supplier<Collection<ZombiesArmorStationData>> armorStationsSupplier,
            Supplier<Optional<ZombiesPowerSwitchData>> powerSwitchSupplier,
            Supplier<Collection<ZombiesSodaMachineData>> sodaMachinesSupplier,
            Supplier<Collection<ZombiesUltimateMachineData>> ultimateMachinesSupplier,
            ZombiesBarrierService barrierService,
            ZombiesWeaponInstanceService weaponInstanceService,
            ZombiesAmmoBoxService ammoBoxService,
            ZombiesArmorService armorService,
            ZombiesPowerService powerService,
            ZombiesBuffService buffService,
            ZombiesUltimateMachineService ultimateMachineService,
            ZombiesWeaponInventoryService weaponInventoryService,
            ZombiesObjectStateStore objectStateStore,
            ZombiesBarrierBlockRuntimeService barrierBlockRuntimeService
    ) {
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.barriersSupplier = Objects.requireNonNull(barriersSupplier, "barriersSupplier");
        this.weaponWallsSupplier = Objects.requireNonNull(weaponWallsSupplier, "weaponWallsSupplier");
        this.ammoBoxesSupplier = Objects.requireNonNull(ammoBoxesSupplier, "ammoBoxesSupplier");
        this.armorStationsSupplier = Objects.requireNonNull(armorStationsSupplier, "armorStationsSupplier");
        this.powerSwitchSupplier = Objects.requireNonNull(powerSwitchSupplier, "powerSwitchSupplier");
        this.sodaMachinesSupplier = Objects.requireNonNull(sodaMachinesSupplier, "sodaMachinesSupplier");
        this.ultimateMachinesSupplier = Objects.requireNonNull(ultimateMachinesSupplier, "ultimateMachinesSupplier");
        this.barrierService = Objects.requireNonNull(barrierService, "barrierService");
        this.weaponInstanceService = Objects.requireNonNull(weaponInstanceService, "weaponInstanceService");
        this.ammoBoxService = Objects.requireNonNull(ammoBoxService, "ammoBoxService");
        this.armorService = Objects.requireNonNull(armorService, "armorService");
        this.powerService = Objects.requireNonNull(powerService, "powerService");
        this.buffService = Objects.requireNonNull(buffService, "buffService");
        this.ultimateMachineService = Objects.requireNonNull(ultimateMachineService, "ultimateMachineService");
        this.weaponInventoryService = Objects.requireNonNull(weaponInventoryService, "weaponInventoryService");
        this.objectStateStore = Objects.requireNonNull(objectStateStore, "objectStateStore");
        this.barrierBlockRuntimeService = barrierBlockRuntimeService == null
                ? ZombiesBarrierBlockRuntimeService.instance()
                : barrierBlockRuntimeService;
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
        if (player == null || context == null) {
            return InteractionResult.PASS;
        }
        if (!roomId.equals(context.roomId())) {
            sendMessage(player, FAILURE_ROOM_MISMATCH, roomId, context.roomId());
            return InteractionResult.FAIL;
        }

        TargetLookup lookup = findTarget(player, context);
        if (lookup.target().isEmpty()) {
            lookup.failure().ifPresent(failure -> sendTargetFailure(player, failure));
            return lookup.failure().isPresent() ? InteractionResult.FAIL : InteractionResult.PASS;
        }
        InteractionTarget target = lookup.target().orElseThrow();

        long gameTime = Math.max(0L, player.level().getGameTime());
        cleanupRecentInteractions(gameTime);
        InteractionKey key = new InteractionKey(
                player.getUUID(),
                target.objectId(),
                gameTime);
        if (recentInteractions.putIfAbsent(key, gameTime) != null) {
            return InteractionResult.SUCCESS;
        }

        return switch (target.type()) {
            case BARRIER -> purchaseBarrier(player, target, (ZombiesBarrierData) target.data());
            case WEAPON_WALL -> purchaseWeaponWall(player, target, (ZombiesWeaponWallData) target.data());
            case AMMO_BOX -> refillAmmoBox(player, target, (ZombiesAmmoBoxData) target.data(), context.itemStack());
            case ARMOR_STATION -> purchaseArmor(player, target, (ZombiesArmorStationData) target.data());
            case POWER_SWITCH -> purchasePowerSwitch(player, target, (ZombiesPowerSwitchData) target.data());
            case SODA_MACHINE -> purchaseSodaMachine(player, target, (ZombiesSodaMachineData) target.data());
            case ULTIMATE_MACHINE -> useUltimateMachine(
                    player,
                    target,
                    (ZombiesUltimateMachineData) target.data(),
                    context.itemStack());
        };
    }

    @Override
    public List<ModeObjectState> objectStatesForClient(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        return objectStateStore.objectStates(
                barriersSupplier.get(),
                weaponWallsSupplier.get(),
                ammoBoxesSupplier.get(),
                armorStationsSupplier.get(),
                powerSwitchSupplier.get(),
                sodaMachinesSupplier.get(),
                ultimateMachinesSupplier.get());
    }

    private InteractionResult purchaseBarrier(ServerPlayer player, InteractionTarget target, ZombiesBarrierData barrier) {
        ZombiesServiceResult<ZombiesBarrierService.BarrierPurchaseResult> result =
                barrierService.purchase(player, barrier);
        if (result.success()) {
            sendMessage(player, SUCCESS_BARRIER, target.objectId(), barrier.cost(), barrier.group());
            return InteractionResult.SUCCESS;
        }
        sendFailureMessage(player, target, result);
        return InteractionResult.FAIL;
    }

    private InteractionResult purchaseWeaponWall(ServerPlayer player, InteractionTarget target, ZombiesWeaponWallData weaponWall) {
        ZombiesObjectStateStore.WeaponWallOffer offer = objectStateStore.currentWeaponWallOffer(weaponWall);
        if (!offer.purchasable()) {
            sendFailureMessage(
                    player,
                    target,
                    ZombiesServiceResult.failure(ZombiesErrorCode.of("weapon_wall.invalid_offer")));
            return InteractionResult.FAIL;
        }
        ZombiesWeaponInstanceState offeredWeapon = ZombiesWeaponInstanceState.primary(
                offer.gunId(),
                offer.weaponLevel(),
                offer.levelDamageMultiplier(),
                offer.maxReserveAmmo());
        ZombiesServiceResult<ZombiesWeaponInventoryService.PreparedWeaponStack> preparedResult =
                weaponInventoryService.preparePurchasedPrimaryWeapon(roomId, offeredWeapon);
        if (!preparedResult.success() || preparedResult.value().isEmpty()) {
            sendFailureMessage(player, target, preparedResult);
            return InteractionResult.FAIL;
        }

        ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> result =
                purchaseWeaponWallState(player.getUUID(), offer, (currentWeapon, purchasedWeapon) ->
                        weaponInventoryService.applyPreparedPrimaryWeapon(
                                player,
                                roomId,
                                preparedResult.value().get(),
                                purchasedWeapon));
        if (result.success()) {
            ZombiesWeaponInstanceService.WallWeaponPurchaseResult purchase = result.value().orElse(null);
            String gunId = purchase == null ? offer.gunId() : purchase.weapon().gunId();
            int weaponLevel = purchase == null ? offer.weaponLevel() : purchase.weapon().weaponLevel();
            double cost = purchase == null ? offer.price() : purchase.cost();
            objectStateStore.markWeaponWallPurchased(weaponWall);
            sendMessage(player, SUCCESS_WEAPON_WALL, gunId, weaponLevel, target.objectId(), displayCost(cost));
            return InteractionResult.SUCCESS;
        }
        sendFailureMessage(player, target, result);
        return InteractionResult.FAIL;
    }

    ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> purchaseWeaponWall(
            UUID playerId,
            ZombiesWeaponWallData weaponWall
    ) {
        ZombiesObjectStateStore.WeaponWallOffer offer = objectStateStore.currentWeaponWallOffer(weaponWall);
        if (!offer.purchasable()) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.of("weapon_wall.invalid_offer"));
        }
        ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> result =
                purchaseWeaponWallState(playerId, offer);
        if (result.success()) {
            objectStateStore.markWeaponWallPurchased(weaponWall);
        }
        return result;
    }

    ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> purchaseWeaponWall(
            UUID playerId,
            ZombiesWeaponWallData weaponWall,
            ZombiesWeaponInstanceService.WallWeaponCommitGuard commitGuard
    ) {
        ZombiesObjectStateStore.WeaponWallOffer offer = objectStateStore.currentWeaponWallOffer(weaponWall);
        if (!offer.purchasable()) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.of("weapon_wall.invalid_offer"));
        }
        ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> result =
                purchaseWeaponWallState(playerId, offer, commitGuard);
        if (result.success()) {
            objectStateStore.markWeaponWallPurchased(weaponWall);
        }
        return result;
    }

    private ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> purchaseWeaponWallState(
            UUID playerId,
            ZombiesObjectStateStore.WeaponWallOffer offer
    ) {
        return purchaseWeaponWallState(playerId, offer, null);
    }

    private ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> purchaseWeaponWallState(
            UUID playerId,
            ZombiesObjectStateStore.WeaponWallOffer offer,
            ZombiesWeaponInstanceService.WallWeaponCommitGuard commitGuard
    ) {
        if (offer == null || !offer.purchasable()) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.of("weapon_wall.invalid_offer"));
        }
        ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> result =
                weaponInstanceService.purchaseWallWeapon(
                        playerId,
                        offer.gunId(),
                        offer.weaponLevel(),
                        offer.levelDamageMultiplier(),
                        offer.maxReserveAmmo(),
                        offer.price(),
                        commitGuard);
        return result;
    }

    private InteractionResult refillAmmoBox(
            ServerPlayer player,
            InteractionTarget target,
            ZombiesAmmoBoxData ammoBox,
            ItemStack currentItemStack
    ) {
        Optional<ZombiesWeaponItemStackService.ZombiesWeaponTagData> currentWeaponTag =
                weaponInventoryService.currentWeaponTag(roomId, currentItemStack);
        if (currentWeaponTag.filter(tag -> tag.slot() == ZombiesEquipmentSlot.STARTER).isPresent()) {
            return refillStarterAmmoBox(player, target, ammoBox, currentWeaponTag.get(), currentItemStack);
        }
        return refillPrimaryAmmoBox(player, target, ammoBox, currentItemStack);
    }

    private InteractionResult refillPrimaryAmmoBox(
            ServerPlayer player,
            InteractionTarget target,
            ZombiesAmmoBoxData ammoBox,
            ItemStack currentItemStack
    ) {
        ZombiesServiceResult<ZombiesWeaponInstanceState> currentWeaponResult =
                weaponInstanceService.currentPrimaryWeapon(player.getUUID());
        if (!currentWeaponResult.success() || currentWeaponResult.value().isEmpty()) {
            sendFailureMessage(player, target, currentWeaponResult);
            return InteractionResult.FAIL;
        }
        ZombiesWeaponInstanceState refilledWeapon = currentWeaponResult.value().get().refillReserveAmmo();
        ZombiesServiceResult<ZombiesWeaponInventoryService.InventoryMutationResult> validationResult =
                weaponInventoryService.validateReserveAmmoSync(
                        player,
                        roomId,
                        ZombiesEquipmentSlot.PRIMARY,
                        refilledWeapon,
                        currentItemStack);
        if (!validationResult.success()) {
            sendFailureMessage(player, target, validationResult);
            return InteractionResult.FAIL;
        }

        ZombiesServiceResult<ZombiesAmmoBoxService.AmmoRefillResult> result =
                ammoBoxService.refillPrimaryWeapon(
                        player.getUUID(),
                        ammoBox.pricesByWeaponLevel(),
                        (currentWeapon, refilled) -> weaponInventoryService.syncReserveAmmo(
                                player,
                                roomId,
                                ZombiesEquipmentSlot.PRIMARY,
                                refilled,
                                currentItemStack));
        if (result.success()) {
            ZombiesAmmoBoxService.AmmoRefillResult refill = result.value().orElse(null);
            ZombiesWeaponInstanceState weapon = refill == null ? refilledWeapon : refill.weapon();
            objectStateStore.markAmmoBoxUsed(ammoBox);
            String gunId = refill == null ? "" : refill.weapon().gunId();
            int weaponLevel = refill == null ? 0 : refill.weapon().weaponLevel();
            double cost = refill == null ? displayAmmoCost(ammoBox) : refill.cost();
            sendMessage(player, SUCCESS_AMMO, gunId, weaponLevel, target.objectId(), displayCost(cost));
            return InteractionResult.SUCCESS;
        }
        sendFailureMessage(player, target, result);
        return InteractionResult.FAIL;
    }

    ZombiesServiceResult<ZombiesAmmoBoxService.AmmoRefillResult> refillPrimaryAmmoBox(
            UUID playerId,
            ZombiesAmmoBoxData ammoBox,
            ZombiesAmmoBoxService.AmmoRefillCommitGuard commitGuard
    ) {
        if (ammoBox == null) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.OBJECT_NOT_FOUND);
        }
        ZombiesServiceResult<ZombiesAmmoBoxService.AmmoRefillResult> result =
                ammoBoxService.refillPrimaryWeapon(playerId, ammoBox.pricesByWeaponLevel(), commitGuard);
        if (result.success()) {
            objectStateStore.markAmmoBoxUsed(ammoBox);
        }
        return result;
    }

    private InteractionResult refillStarterAmmoBox(
            ServerPlayer player,
            InteractionTarget target,
            ZombiesAmmoBoxData ammoBox,
            ZombiesWeaponItemStackService.ZombiesWeaponTagData currentWeaponTag,
            ItemStack currentItemStack
    ) {
        ZombiesWeaponInstanceState currentWeapon = currentWeaponTag.toWeaponState();
        if (currentWeapon.isReserveFull()) {
            sendFailureMessage(
                    player,
                    target,
                    ZombiesServiceResult.failure(AMMO_ALREADY_FULL, weaponParams(currentWeapon), ""));
            return InteractionResult.FAIL;
        }

        ZombiesWeaponInstanceState refilledWeapon = currentWeapon.refillReserveAmmo();
        ZombiesServiceResult<ZombiesWeaponInventoryService.InventoryMutationResult> validationResult =
                weaponInventoryService.validateReserveAmmoSync(
                        player,
                        roomId,
                        ZombiesEquipmentSlot.STARTER,
                        refilledWeapon,
                        currentItemStack);
        if (!validationResult.success()) {
            sendFailureMessage(player, target, validationResult);
            return InteractionResult.FAIL;
        }

        ZombiesServiceResult<ZombiesAmmoBoxService.StarterAmmoRefillResult> result =
                ammoBoxService.refillStarterPistol(
                        player.getUUID(),
                        currentWeapon.gunId(),
                        currentWeapon.maxReserveAmmo());
        if (result.success()) {
            ZombiesAmmoBoxService.StarterAmmoRefillResult refill = result.value().orElse(null);
            ZombiesWeaponInstanceState weapon = refill == null
                    ? refilledWeapon
                    : currentWeapon.withReserveAmmo(refill.reserveAmmo());
            ZombiesServiceResult<ZombiesWeaponInventoryService.InventoryMutationResult> syncResult =
                    weaponInventoryService.syncReserveAmmo(
                            player,
                            roomId,
                            ZombiesEquipmentSlot.STARTER,
                            weapon,
                            currentItemStack);
            if (!syncResult.success()) {
                sendFailureMessage(player, target, syncResult);
                return InteractionResult.FAIL;
            }
            objectStateStore.markAmmoBoxUsed(ammoBox);
            sendMessage(player, SUCCESS_AMMO, weapon.gunId(), weapon.weaponLevel(), target.objectId(), 0);
            return InteractionResult.SUCCESS;
        }
        sendFailureMessage(player, target, result);
        return InteractionResult.FAIL;
    }

    private InteractionResult purchaseArmor(ServerPlayer player, InteractionTarget target, ZombiesArmorStationData armorStation) {
        ZombiesServiceResult<ZombiesArmorService.ArmorPurchaseResult> result =
                armorService.purchaseArmor(
                        player.getUUID(),
                        armorStation.armorLevel(),
                        armorStation.damageTakenMultiplier(),
                        armorStation.buyCost());
        if (result.success()) {
            objectStateStore.markArmorStationPurchased(armorStation);
            ZombiesArmorService.ArmorPurchaseResult purchase = result.value().orElse(null);
            int armorLevel = purchase == null ? armorStation.armorLevel() : purchase.armor().armorLevel();
            double cost = purchase == null ? armorStation.buyCost() : purchase.cost();
            sendMessage(player, SUCCESS_ARMOR, armorLevel, target.objectId(), displayCost(cost));
            return InteractionResult.SUCCESS;
        }
        sendFailureMessage(player, target, result);
        return InteractionResult.FAIL;
    }

    private InteractionResult purchasePowerSwitch(ServerPlayer player, InteractionTarget target, ZombiesPowerSwitchData powerSwitch) {
        boolean alreadyOn = powerService.isPowerOn();
        ZombiesServiceResult<ZombiesPowerService.PowerPurchaseResult> result =
                purchasePowerSwitch(player.getUUID(), powerSwitch);
        if (result.success()) {
            if (alreadyOn) {
                sendMessage(player, NOTICE_POWER_ALREADY_ON, target.objectId());
            } else {
                ZombiesPowerService.PowerPurchaseResult purchase = result.value().orElse(null);
                double cost = purchase == null ? powerSwitch.cost() : purchase.cost();
                sendMessage(player, SUCCESS_POWER, target.objectId(), displayCost(cost));
            }
            return InteractionResult.SUCCESS;
        }
        sendFailureMessage(player, target, result);
        return InteractionResult.FAIL;
    }

    ZombiesServiceResult<ZombiesPowerService.PowerPurchaseResult> purchasePowerSwitch(
            UUID playerId,
            ZombiesPowerSwitchData powerSwitch
    ) {
        if (powerSwitch == null) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.OBJECT_NOT_FOUND);
        }
        if (powerService.isPowerOn()) {
            return ZombiesServiceResult.success(new ZombiesPowerService.PowerPurchaseResult(true, 0.0D));
        }
        ZombiesServiceResult<ZombiesPowerService.PowerPurchaseResult> result =
                powerService.turnOn(playerId, powerSwitch.cost());
        if (result.success()) {
            objectStateStore.markPowerSwitchTurnedOn(powerSwitch);
            return result;
        }
        if (ZombiesErrorCode.POWER_ALREADY_ON.equals(result.code())) {
            objectStateStore.markPowerSwitchTurnedOn(powerSwitch);
            return ZombiesServiceResult.success(new ZombiesPowerService.PowerPurchaseResult(true, 0.0D));
        }
        return result;
    }

    private InteractionResult purchaseSodaMachine(ServerPlayer player, InteractionTarget target, ZombiesSodaMachineData sodaMachine) {
        ZombiesServiceResult<ZombiesBuffService.BuffPurchaseResult> result =
                purchaseSodaMachine(player.getUUID(), sodaMachine);
        if (result.success()) {
            ZombiesBuffService.BuffPurchaseResult purchase = result.value().orElse(null);
            String buffId = purchase == null ? sodaMachine.buffId() : purchase.buff().type().id();
            if (purchase != null && purchase.alreadyOwned()) {
                sendMessage(player, FAILURE_SODA_ALREADY_OWNED, buffId, target.objectId());
            } else {
                double cost = purchase == null ? sodaMachine.cost() : purchase.cost();
                sendMessage(player, SUCCESS_SODA, buffId, target.objectId(), displayCost(cost));
            }
            return InteractionResult.SUCCESS;
        }
        sendFailureMessage(player, target, result);
        return InteractionResult.FAIL;
    }

    ZombiesServiceResult<ZombiesBuffService.BuffPurchaseResult> purchaseSodaMachine(
            UUID playerId,
            ZombiesSodaMachineData sodaMachine
    ) {
        if (sodaMachine == null) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.OBJECT_NOT_FOUND);
        }
        ZombiesServiceResult<ZombiesBuffService.BuffPurchaseResult> result =
                buffService.purchaseBuff(
                        playerId,
                        sodaMachine.buffId(),
                        sodaMachine.cost(),
                        sodaMachine.requiresPower());
        if (result.success()) {
            objectStateStore.markSodaMachinePurchased(sodaMachine);
        }
        return result;
    }

    private InteractionResult useUltimateMachine(
            ServerPlayer player,
            InteractionTarget target,
            ZombiesUltimateMachineData ultimateMachine,
            ItemStack currentItemStack
    ) {
        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> result =
                useUltimateMachine(player.getUUID(), ultimateMachine, (currentWeapon, upgradedWeapon) ->
                        weaponInventoryService.syncReserveAmmo(
                                player,
                                roomId,
                                ZombiesEquipmentSlot.PRIMARY,
                                upgradedWeapon,
                                currentItemStack));
        if (result.success()) {
            ZombiesUltimateMachineService.WeaponUpgradeResult upgrade = result.value().orElse(null);
            String gunId = upgrade == null ? "" : upgrade.weapon().gunId();
            int weaponLevel = upgrade == null ? 0 : upgrade.weapon().weaponLevel();
            int upgradeLevel = upgrade == null ? 0 : upgrade.weapon().upgradeLevel();
            double cost = upgrade == null ? displayUltimateCost(ultimateMachine) : upgrade.cost();
            sendMessage(player, SUCCESS_ULTIMATE, gunId, weaponLevel, upgradeLevel, target.objectId(), displayCost(cost));
            return InteractionResult.SUCCESS;
        }
        sendFailureMessage(player, target, result);
        return InteractionResult.FAIL;
    }

    ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> useUltimateMachine(
            UUID playerId,
            ZombiesUltimateMachineData ultimateMachine
    ) {
        if (ultimateMachine == null) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.OBJECT_NOT_FOUND);
        }
        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> result =
                ultimateMachineService.upgradePrimaryWeapon(playerId, ultimateMachine);
        if (result.success()) {
            objectStateStore.markUltimateMachineUsed(ultimateMachine);
        }
        return result;
    }

    ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> useUltimateMachine(
            UUID playerId,
            ZombiesUltimateMachineData ultimateMachine,
            ZombiesUltimateMachineService.WeaponUpgradeCommitGuard commitGuard
    ) {
        if (ultimateMachine == null) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.OBJECT_NOT_FOUND);
        }
        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> result =
                ultimateMachineService.upgradePrimaryWeapon(playerId, ultimateMachine, commitGuard);
        if (result.success()) {
            objectStateStore.markUltimateMachineUsed(ultimateMachine);
        }
        return result;
    }

    private TargetLookup findTarget(ServerPlayer player, ModeObjectInteractionContext context) {
        List<InteractionTarget> candidates = collectCandidates(player);
        if (candidates.isEmpty()) {
            return context.blockPos() == null
                    ? TargetLookup.empty()
                    : TargetLookup.failure(ZombiesErrorCode.OBJECT_NOT_FOUND, "", context.blockPos());
        }

        BlockPos clickedPos = context.blockPos();
        Vec3 playerPos = player.position();
        if (clickedPos != null) {
            double maxDistanceSqr = MAX_INTERACTION_DISTANCE * MAX_INTERACTION_DISTANCE;
            Optional<InteractionTarget> blockBarrierTarget = barrierBlockRuntimeService.cellAt(roomId, player.level(), clickedPos)
                    .flatMap(cell -> barrierByObjectId(cell.objectId()))
                    .map(barrier -> new InteractionTarget(
                            InteractionType.BARRIER,
                            ZombiesObjectStateStore.objectKey(barrier),
                            clickedPos,
                            barrier));
            if (blockBarrierTarget.isPresent()) {
                InteractionTarget target = blockBarrierTarget.orElseThrow();
                if (distanceToInteractionSqr(playerPos, target) > maxDistanceSqr) {
                    return TargetLookup.failure(ZombiesErrorCode.OBJECT_OUT_OF_RANGE, target.objectId(), target.position());
                }
                return TargetLookup.target(target);
            }
            Optional<InteractionTarget> clickedTarget = candidates.stream()
                    .filter(candidate -> clickedPos.equals(candidate.position()))
                    .findFirst();
            if (clickedTarget.isEmpty()) {
                return TargetLookup.failure(ZombiesErrorCode.OBJECT_NOT_FOUND, "", clickedPos);
            }
            InteractionTarget target = clickedTarget.orElseThrow();
            if (distanceToInteractionSqr(playerPos, target) > maxDistanceSqr) {
                return TargetLookup.failure(ZombiesErrorCode.OBJECT_OUT_OF_RANGE, target.objectId(), target.position());
            }
            return TargetLookup.target(target);
        }

        double maxDistance = Math.min(DEFAULT_INTERACTION_DISTANCE, MAX_INTERACTION_DISTANCE);
        double maxDistanceSqr = maxDistance * maxDistance;
        return candidates.stream()
                .filter(candidate -> distanceToInteractionSqr(playerPos, candidate) <= maxDistanceSqr)
                .min(Comparator.comparingDouble(candidate -> distanceToInteractionSqr(playerPos, candidate)))
                .map(TargetLookup::target)
                .orElseGet(TargetLookup::empty);
    }

    private List<InteractionTarget> collectCandidates(ServerPlayer player) {
        List<InteractionTarget> candidates = new ArrayList<>();
        for (ZombiesBarrierData barrier : safeCollection(barriersSupplier)) {
            if (barrier != null && player.level().dimension().equals(barrier.dimension())) {
                candidates.add(new InteractionTarget(
                        InteractionType.BARRIER,
                        ZombiesObjectStateStore.objectKey(barrier),
                        barrier.interactionPos(),
                        barrier));
            }
        }
        for (ZombiesWeaponWallData weaponWall : safeCollection(weaponWallsSupplier)) {
            if (weaponWall != null && player.level().dimension().equals(weaponWall.dimension())) {
                candidates.add(new InteractionTarget(
                        InteractionType.WEAPON_WALL,
                        ZombiesObjectStateStore.objectKey(weaponWall),
                        interactionPosition(weaponWall),
                        weaponWall));
            }
        }
        for (ZombiesAmmoBoxData ammoBox : safeCollection(ammoBoxesSupplier)) {
            if (ammoBox != null && player.level().dimension().equals(ammoBox.dimension())) {
                candidates.add(new InteractionTarget(
                        InteractionType.AMMO_BOX,
                        ZombiesObjectStateStore.objectKey(ammoBox),
                        interactionPosition(ammoBox),
                        ammoBox));
            }
        }
        for (ZombiesArmorStationData armorStation : safeCollection(armorStationsSupplier)) {
            if (armorStation != null && player.level().dimension().equals(armorStation.dimension())) {
                candidates.add(new InteractionTarget(
                        InteractionType.ARMOR_STATION,
                        ZombiesObjectStateStore.objectKey(armorStation),
                        interactionPosition(armorStation),
                        armorStation));
            }
        }
        Optional<ZombiesPowerSwitchData> powerSwitch = powerSwitchSupplier.get();
        if (powerSwitch != null && powerSwitch.isPresent() && player.level().dimension().equals(powerSwitch.get().dimension())) {
            candidates.add(new InteractionTarget(
                    InteractionType.POWER_SWITCH,
                    ZombiesObjectStateStore.objectKey(powerSwitch.get()),
                    interactionPosition(powerSwitch.get()),
                    powerSwitch.get()));
        }
        for (ZombiesSodaMachineData sodaMachine : safeCollection(sodaMachinesSupplier)) {
            if (sodaMachine != null && player.level().dimension().equals(sodaMachine.dimension())) {
                candidates.add(new InteractionTarget(
                        InteractionType.SODA_MACHINE,
                        ZombiesObjectStateStore.objectKey(sodaMachine),
                        interactionPosition(sodaMachine),
                        sodaMachine));
            }
        }
        for (ZombiesUltimateMachineData ultimateMachine : safeCollection(ultimateMachinesSupplier)) {
            if (ultimateMachine != null && player.level().dimension().equals(ultimateMachine.dimension())) {
                candidates.add(new InteractionTarget(
                        InteractionType.ULTIMATE_MACHINE,
                        ZombiesObjectStateStore.objectKey(ultimateMachine),
                        interactionPosition(ultimateMachine),
                        ultimateMachine));
            }
        }
        return candidates;
    }

    private Optional<ZombiesBarrierData> barrierByObjectId(String objectId) {
        String normalizedObjectId = Objects.requireNonNullElse(objectId, "").trim();
        if (normalizedObjectId.isEmpty()) {
            return Optional.empty();
        }
        return safeCollection(barriersSupplier).stream()
                .filter(Objects::nonNull)
                .filter(barrier -> normalizedObjectId.equals(ZombiesObjectStateStore.objectKey(barrier)))
                .findFirst();
    }

    private void sendTargetFailure(ServerPlayer player, TargetFailure failure) {
        if (ZombiesErrorCode.OBJECT_OUT_OF_RANGE.equals(failure.code())) {
            sendMessage(
                    player,
                    FAILURE_OBJECT_OUT_OF_RANGE,
                    objectOrPosition(failure.objectId(), failure.position()),
                    formatPosition(failure.position()));
            return;
        }
        sendMessage(player, FAILURE_OBJECT_NOT_FOUND, formatPosition(failure.position()));
    }

    private void sendFailureMessage(
            ServerPlayer player,
            InteractionTarget target,
            ZombiesServiceResult<?> result
    ) {
        ZombiesErrorCode code = result == null ? ZombiesErrorCode.OBJECT_NOT_FOUND : result.code();
        if (ZombiesErrorCode.PLAYER_DEAD.equals(code)) {
            sendMessage(player, FAILURE_DEAD, target.objectId());
            return;
        }
        if (ZombiesErrorCode.PLAYER_LEFT.equals(code)
                || ZombiesErrorCode.PLAYER_OFFLINE.equals(code)
                || ZombiesErrorCode.OBJECT_ROOM_MISMATCH.equals(code)) {
            sendMessage(player, FAILURE_NOT_IN_ROOM, target.objectId(), roomId);
            return;
        }
        if (ZombiesErrorCode.OBJECT_NOT_FOUND.equals(code)) {
            sendMessage(player, FAILURE_OBJECT_NOT_FOUND, objectOrPosition(target.objectId(), target.position()));
            return;
        }
        if (ZombiesErrorCode.OBJECT_OUT_OF_RANGE.equals(code)) {
            sendMessage(
                    player,
                    FAILURE_OBJECT_OUT_OF_RANGE,
                    objectOrPosition(target.objectId(), target.position()),
                    formatPosition(target.position()));
            return;
        }
        if (ZombiesErrorCode.ECONOMY_NOT_ENOUGH_POINTS.equals(code)) {
            sendMessage(
                    player,
                    FAILURE_NOT_ENOUGH_POINTS,
                    param(result, "cost", fallbackCost(target)),
                    target.objectId(),
                    param(result, "points", 0));
            return;
        }
        if (BARRIER_ALREADY_CLEARED.equals(code)) {
            sendMessage(player, FAILURE_BARRIER_ALREADY_CLEARED, target.objectId(), fallbackGroup(target));
            return;
        }
        if (ZombiesErrorCode.WEAPON_ALREADY_OWNED.equals(code)) {
            sendMessage(
                    player,
                    FAILURE_WEAPON_ALREADY_OWNED,
                    param(result, "gunId", fallbackGunId(target)),
                    param(result, "weaponLevel", fallbackWeaponLevel(target)),
                    target.objectId());
            return;
        }
        if (AMMO_ALREADY_FULL.equals(code)) {
            sendMessage(
                    player,
                    FAILURE_AMMO_ALREADY_FULL,
                    param(result, "gunId", fallbackGunId(target)),
                    param(result, "weaponLevel", fallbackWeaponLevel(target)),
                    target.objectId());
            return;
        }
        if (ZombiesArmorService.armorAlreadyOwnedCode().equals(code)) {
            sendMessage(
                    player,
                    FAILURE_ARMOR_ALREADY_OWNED,
                    param(result, "armorLevel", fallbackArmorLevel(target)),
                    target.objectId());
            return;
        }
        if (ZombiesErrorCode.POWER_REQUIRES_POWER.equals(code)) {
            sendMessage(player, FAILURE_REQUIRES_POWER, target.objectId());
            return;
        }
        if (ZombiesErrorCode.POWER_ALREADY_ON.equals(code)) {
            sendMessage(player, NOTICE_POWER_ALREADY_ON, target.objectId());
            return;
        }
        if (ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON.equals(code)) {
            sendMessage(
                    player,
                    FAILURE_INVALID_CURRENT_WEAPON,
                    target.objectId(),
                    param(result, "gunId", fallbackGunId(target)),
                    param(result, "weaponLevel", fallbackWeaponLevel(target)));
            return;
        }
        if (ZombiesErrorCode.WEAPON_MAX_UPGRADE.equals(code)) {
            sendMessage(
                    player,
                    FAILURE_MAX_UPGRADE,
                    target.objectId(),
                    param(result, "upgradeLevel", fallbackUpgradeLevel(target)),
                    fallbackMaxUpgradeLevel(target),
                    param(result, "gunId", fallbackGunId(target)),
                    param(result, "weaponLevel", fallbackWeaponLevel(target)));
            return;
        }
        sendMessage(player, FAILURE_GENERIC, target.objectId(), code.key());
    }

    private Object fallbackCost(InteractionTarget target) {
        Object data = target.data();
        if (data instanceof ZombiesBarrierData barrier) {
            return barrier.cost();
        }
        if (data instanceof ZombiesWeaponWallData weaponWall) {
            return objectStateStore.currentWeaponWallOffer(weaponWall).price();
        }
        if (data instanceof ZombiesAmmoBoxData ammoBox) {
            return displayAmmoCost(ammoBox);
        }
        if (data instanceof ZombiesArmorStationData armorStation) {
            return armorStation.buyCost();
        }
        if (data instanceof ZombiesPowerSwitchData powerSwitch) {
            return powerSwitch.cost();
        }
        if (data instanceof ZombiesSodaMachineData sodaMachine) {
            return sodaMachine.cost();
        }
        if (data instanceof ZombiesUltimateMachineData ultimateMachine) {
            return displayUltimateCost(ultimateMachine);
        }
        return 0;
    }

    private String fallbackGunId(InteractionTarget target) {
        Object data = target.data();
        if (data instanceof ZombiesWeaponWallData weaponWall) {
            return objectStateStore.currentWeaponWallOffer(weaponWall).gunId();
        }
        return "";
    }

    private int fallbackWeaponLevel(InteractionTarget target) {
        Object data = target.data();
        if (data instanceof ZombiesWeaponWallData weaponWall) {
            return objectStateStore.currentWeaponWallOffer(weaponWall).weaponLevel();
        }
        return 0;
    }

    private static int fallbackArmorLevel(InteractionTarget target) {
        Object data = target.data();
        return data instanceof ZombiesArmorStationData armorStation ? armorStation.armorLevel() : 0;
    }

    private static int fallbackGroup(InteractionTarget target) {
        Object data = target.data();
        return data instanceof ZombiesBarrierData barrier ? barrier.group() : 0;
    }

    private static int fallbackUpgradeLevel(InteractionTarget target) {
        return target.data() instanceof ZombiesUltimateMachineData ? 0 : 0;
    }

    private static int fallbackMaxUpgradeLevel(InteractionTarget target) {
        Object data = target.data();
        return data instanceof ZombiesUltimateMachineData ultimateMachine ? ultimateMachine.maxUpgradeLevel() : 0;
    }

    private static Object param(ZombiesServiceResult<?> result, String name, Object fallback) {
        ModePlayerValue value = result == null ? null : result.params().get(name);
        return value == null ? fallback : value.value();
    }

    private static Map<String, ModePlayerValue> weaponParams(ZombiesWeaponInstanceState weapon) {
        Map<String, ModePlayerValue> params = new java.util.LinkedHashMap<>();
        params.put("gunId", ModePlayerValue.ofString(weapon == null ? "" : weapon.gunId()));
        params.put("weaponLevel", ModePlayerValue.ofInt(weapon == null ? 0 : weapon.weaponLevel()));
        return params;
    }

    private static void sendMessage(ServerPlayer player, String key, Object... args) {
        if (player != null) {
            player.sendSystemMessage(Component.translatable(key, args));
        }
    }

    private static String objectOrPosition(String objectId, BlockPos position) {
        String cleanedObjectId = Objects.requireNonNullElse(objectId, "").trim();
        return cleanedObjectId.isBlank() ? formatPosition(position) : cleanedObjectId;
    }

    private static String formatPosition(BlockPos position) {
        BlockPos safePosition = position == null ? BlockPos.ZERO : position;
        return safePosition.getX() + "," + safePosition.getY() + "," + safePosition.getZ();
    }

    private static int displayCost(double cost) {
        if (!Double.isFinite(cost)) {
            return 0;
        }
        return (int) Math.floor(Math.max(0.0D, cost));
    }

    private static int displayAmmoCost(ZombiesAmmoBoxData ammoBox) {
        if (ammoBox == null || ammoBox.pricesByWeaponLevel().isEmpty()) {
            return 0;
        }
        int cost = Integer.MAX_VALUE;
        for (Integer value : ammoBox.pricesByWeaponLevel().values()) {
            if (value != null && value >= 0) {
                cost = Math.min(cost, value);
            }
        }
        return cost == Integer.MAX_VALUE ? 0 : cost;
    }

    private static int displayUltimateCost(ZombiesUltimateMachineData ultimateMachine) {
        if (ultimateMachine == null || ultimateMachine.levels().isEmpty()) {
            return 0;
        }
        int cost = Integer.MAX_VALUE;
        for (ZombiesUltimateMachineData.UpgradeLevelData level : ultimateMachine.levels().values()) {
            if (level != null && level.cost() >= 0) {
                cost = Math.min(cost, level.cost());
            }
        }
        return cost == Integer.MAX_VALUE ? 0 : cost;
    }

    private void cleanupRecentInteractions(long gameTime) {
        if (gameTime % 200L != 0L) {
            return;
        }
        long cutoff = Math.max(0L, gameTime - 20L);
        recentInteractions.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    private static <T> Collection<T> safeCollection(Supplier<Collection<T>> supplier) {
        Collection<T> values = supplier == null ? null : supplier.get();
        return values == null ? List.of() : values;
    }

    private static BlockPos interactionPosition(ZombiesWeaponWallData weaponWall) {
        return weaponWall.interactionPos().orElse(weaponWall.pos());
    }

    private static BlockPos interactionPosition(ZombiesAmmoBoxData ammoBox) {
        return ammoBox.interactionPos().orElse(ammoBox.pos());
    }

    private static BlockPos interactionPosition(ZombiesArmorStationData armorStation) {
        return armorStation.interactionPos().orElse(armorStation.pos());
    }

    private static BlockPos interactionPosition(ZombiesPowerSwitchData powerSwitch) {
        return powerSwitch.interactionPos().orElse(powerSwitch.pos());
    }

    private static BlockPos interactionPosition(ZombiesSodaMachineData sodaMachine) {
        return sodaMachine.interactionPos().orElse(sodaMachine.pos());
    }

    private static BlockPos interactionPosition(ZombiesUltimateMachineData ultimateMachine) {
        return ultimateMachine.interactionPos().orElse(ultimateMachine.pos());
    }

    private static double distanceToInteractionSqr(Vec3 playerPos, InteractionTarget target) {
        Vec3 targetPos = Vec3.atCenterOf(target.position());
        return playerPos.distanceToSqr(targetPos);
    }

    private enum InteractionType {
        BARRIER,
        WEAPON_WALL,
        AMMO_BOX,
        ARMOR_STATION,
        POWER_SWITCH,
        SODA_MACHINE,
        ULTIMATE_MACHINE
    }

    private record InteractionTarget(
            InteractionType type,
            String objectId,
            BlockPos position,
            Object data
    ) {
        private InteractionTarget {
            Objects.requireNonNull(type, "type");
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            position = position == null ? BlockPos.ZERO : position;
            Objects.requireNonNull(data, "data");
        }
    }

    private record TargetLookup(
            Optional<InteractionTarget> target,
            Optional<TargetFailure> failure
    ) {
        private TargetLookup {
            target = target == null ? Optional.empty() : target;
            failure = failure == null ? Optional.empty() : failure;
        }

        private static TargetLookup target(InteractionTarget target) {
            return new TargetLookup(Optional.of(target), Optional.empty());
        }

        private static TargetLookup failure(ZombiesErrorCode code, String objectId, BlockPos position) {
            return new TargetLookup(Optional.empty(), Optional.of(new TargetFailure(code, objectId, position)));
        }

        private static TargetLookup empty() {
            return new TargetLookup(Optional.empty(), Optional.empty());
        }
    }

    private record TargetFailure(
            ZombiesErrorCode code,
            String objectId,
            BlockPos position
    ) {
        private TargetFailure {
            code = code == null ? ZombiesErrorCode.OBJECT_NOT_FOUND : code;
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            position = position == null ? BlockPos.ZERO : position;
        }
    }

    private record InteractionKey(
            UUID playerId,
            String objectId,
            long gameTime
    ) {
    }
}
