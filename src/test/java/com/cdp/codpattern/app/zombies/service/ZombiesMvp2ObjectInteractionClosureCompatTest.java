package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModeObjectState;
import com.cdp.codpattern.app.zombies.map.object.ZombiesAmmoBoxData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesArmorStationData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesWeaponWallData;
import com.cdp.codpattern.app.zombies.model.ZombiesArmorState;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;
import com.cdp.codpattern.client.ClientModeObjectState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class ZombiesMvp2ObjectInteractionClosureCompatTest {
    private ZombiesMvp2ObjectInteractionClosureCompatTest() {
    }

    public static void main(String[] args) throws InterruptedException {
        staleClientObjectRevisionDoesNotOverwriteFreshState();
        crossRoomObjectStatePacketsStayRoomScoped();
        deadSpectatorAndInsufficientPointsFailuresDoNotSpendOrMarkObjects();
        barrierConcurrentOrRepeatPurchaseOnlyClearsAndSpendsOnce();
        weaponWallRefreshAndDuplicatePurchaseAreIdempotent();
        weaponWallCommitFailureDoesNotSpendMutateOrRevise();
        ammoBoxInvalidWeaponAndMissingPriceFailWithoutSpendOrRevision();
        ammoBoxCommitFailureDoesNotSpendMutateOrRevise();
        armorRepeatAndDowngradeFailWithoutSpendOrRevision();
    }

    private static void staleClientObjectRevisionDoesNotOverwriteFreshState() {
        String roomKey = "zombies:stale-object-revision";
        ClientModeObjectState.clear(roomKey);

        ModeObjectState oldState = modeObjectState("wall-1", 1L, "tacz:old");
        ModeObjectState freshState = modeObjectState("wall-1", 2L, "tacz:fresh");
        ClientModeObjectState.replaceRoomStates(roomKey, List.of(oldState), 1L);
        ClientModeObjectState.replaceRoomStates(roomKey, List.of(freshState), 2L);
        ClientModeObjectState.replaceRoomStates(roomKey, List.of(oldState), 1L);

        require(ClientModeObjectState.revision(roomKey).orElseThrow() == 2L,
                "client object room revision should keep newest packet");
        ModeObjectState retained = ClientModeObjectState.roomStates(roomKey).get("wall-1");
        require(retained != null, "client should retain wall state");
        require("tacz:fresh".equals(retained.payload().getString("gunId")),
                "old object state packet should not overwrite fresh payload");
        ClientModeObjectState.clear(roomKey);
    }

    private static void crossRoomObjectStatePacketsStayRoomScoped() {
        String roomA = "zombies:room-a";
        String roomB = "zombies:room-b";
        ClientModeObjectState.clear(roomA);
        ClientModeObjectState.clear(roomB);

        ClientModeObjectState.replaceRoomStates(roomA, List.of(modeObjectState("wall-a", 1L, "tacz:room_a")), 1L);
        ClientModeObjectState.replaceRoomStates(roomB, List.of(modeObjectState("wall-b", 5L, "tacz:room_b")), 5L);
        ClientModeObjectState.replaceRoomStates(roomA, List.of(modeObjectState("wall-a", 2L, "tacz:room_a_new")), 2L);

        require(ClientModeObjectState.roomStates(roomA).containsKey("wall-a"),
                "room A should retain its own object state");
        require(!ClientModeObjectState.roomStates(roomA).containsKey("wall-b"),
                "room A should not receive room B object state");
        require("tacz:room_b".equals(ClientModeObjectState.roomStates(roomB).get("wall-b").payload().getString("gunId")),
                "room B should keep its own object payload");
        require(ClientModeObjectState.revision(roomB).orElseThrow() == 5L,
                "room A update should not lower or replace room B revision");

        ClientModeObjectState.clear(roomA);
        ClientModeObjectState.clear(roomB);
    }

    private static void deadSpectatorAndInsufficientPointsFailuresDoNotSpendOrMarkObjects() {
        Services services = services();
        ZombiesObjectStateStore store = new ZombiesObjectStateStore();
        ZombiesWeaponWallData wall = weaponWall("wall-fail", "tacz:m4a1");
        store.resetObjects(List.of(), List.of(wall), List.of(), List.of(), 1, 5);
        long initialWallRevision = stateRevision(store, List.of(wall), "wall-fail");

        UUID deadPlayer = playerId(1);
        services.economy.addPoints(deadPlayer, 1_000.0D);
        services.players.getOrCreate(deadPlayer).markDeadSpectating();

        ZombiesObjectInteractionService interactionService = interactionService(services, store, List.of(), List.of(wall), List.of(), List.of());
        ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> deadPurchase =
                interactionService.purchaseWeaponWall(deadPlayer, wall);

        requireFailure(deadPurchase, ZombiesErrorCode.PLAYER_DEAD,
                "dead spectator should be rejected by purchase spend eligibility");
        requirePoints(services.players, deadPlayer, 1_000.0D, "dead spectator purchase should not spend");
        require(stateRevision(store, List.of(wall), "wall-fail") == initialWallRevision,
                "dead spectator purchase should not advance wall revision");

        UUID poorPlayer = playerId(2);
        services.economy.addPoints(poorPlayer, 100.0D);
        ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> poorPurchase =
                interactionService.purchaseWeaponWall(poorPlayer, wall);

        requireFailure(poorPurchase, ZombiesErrorCode.ECONOMY_NOT_ENOUGH_POINTS,
                "insufficient points purchase should fail");
        requirePoints(services.players, poorPlayer, 100.0D, "insufficient points purchase should not spend");
        require(stateRevision(store, List.of(wall), "wall-fail") == initialWallRevision,
                "insufficient points purchase should not advance wall revision");
    }

    private static void barrierConcurrentOrRepeatPurchaseOnlyClearsAndSpendsOnce() throws InterruptedException {
        Services services = services();
        ZombiesObjectStateStore store = new ZombiesObjectStateStore();
        List<ZombiesBarrierData> barriers = List.of(
                barrier("barrier-2-a"),
                barrier("barrier-2-b"));
        store.resetObjects(barriers, List.of(), List.of(), List.of());

        UUID firstPlayer = playerId(3);
        UUID secondPlayer = playerId(4);
        services.economy.addPoints(firstPlayer, 1_000.0D);
        services.economy.addPoints(secondPlayer, 1_000.0D);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<ZombiesServiceResult<ZombiesObjectStateStore.BarrierGroupUpdate>> firstResult = new AtomicReference<>();
        AtomicReference<ZombiesServiceResult<ZombiesObjectStateStore.BarrierGroupUpdate>> secondResult = new AtomicReference<>();

        Thread first = barrierPurchaseThread(services, store, barriers, firstPlayer, ready, start, firstResult);
        Thread second = barrierPurchaseThread(services, store, barriers, secondPlayer, ready, start, secondResult);
        first.start();
        second.start();
        ready.await();
        start.countDown();
        first.join();
        second.join();

        List<ZombiesServiceResult<ZombiesObjectStateStore.BarrierGroupUpdate>> results =
                List.of(firstResult.get(), secondResult.get());
        long successCount = results.stream().filter(result -> result.success()).count();
        require(successCount == 1L, "only one concurrent barrier purchase should succeed");
        require(results.stream().filter(result -> !result.success())
                        .allMatch(result -> ZombiesErrorCode.of("barrier.already_cleared").equals(result.code())),
                "losing barrier purchase should see already cleared");
        require(store.isBarrierCleared(barriers.get(0)), "first group barrier should be cleared");
        require(store.isBarrierCleared(barriers.get(1)), "second group barrier should be cleared");
        requireClose(
                services.players.getOrCreate(firstPlayer).points() + services.players.getOrCreate(secondPlayer).points(),
                1_250.0D,
                "concurrent barrier purchases should spend exactly one cost");

        UUID repeatPlayer = playerId(5);
        services.economy.addPoints(repeatPlayer, 1_000.0D);
        ZombiesServiceResult<ZombiesObjectStateStore.BarrierGroupUpdate> repeat =
                purchaseBarrierPure(services, store, barriers, repeatPlayer);
        requireFailure(repeat, ZombiesErrorCode.of("barrier.already_cleared"),
                "repeat barrier purchase should fail after group clear");
        requirePoints(services.players, repeatPlayer, 1_000.0D, "repeat barrier purchase should not spend");
    }

    private static void weaponWallRefreshAndDuplicatePurchaseAreIdempotent() {
        Services services = services();
        ZombiesObjectStateStore store = new ZombiesObjectStateStore();
        ZombiesWeaponWallData wall = weaponWall("wall-idempotent", "tacz:m4a1");
        store.resetObjects(List.of(), List.of(wall), List.of(), List.of(), 1, 5);
        long initialRevision = stateRevision(store, List.of(wall), "wall-idempotent");

        store.refreshWeaponWallOffersForWave(List.of(wall), 1, 5);
        long firstWaveRevision = stateRevision(store, List.of(wall), "wall-idempotent");
        store.refreshWeaponWallOffersForWave(List.of(wall), 1, 5);
        require(stateRevision(store, List.of(wall), "wall-idempotent") == firstWaveRevision,
                "same first-wave wall refresh should be idempotent");
        require(firstWaveRevision >= initialRevision, "first-wave refresh should not reduce wall revision");

        ZombiesObjectInteractionService interactionService = interactionService(services, store, List.of(), List.of(wall), List.of(), List.of());
        UUID playerId = playerId(6);
        services.economy.addPoints(playerId, 2_000.0D);
        requireSuccess(interactionService.purchaseWeaponWall(playerId, wall),
                "first wall weapon purchase should succeed");
        double balanceAfterPurchase = services.players.getOrCreate(playerId).points();
        long purchaseRevision = stateRevision(store, List.of(wall), "wall-idempotent");
        requireFailure(interactionService.purchaseWeaponWall(playerId, wall), ZombiesErrorCode.WEAPON_ALREADY_OWNED,
                "duplicate wall weapon purchase should fail");
        requirePoints(services.players, playerId, balanceAfterPurchase, "duplicate wall weapon purchase should not spend again");
        require(stateRevision(store, List.of(wall), "wall-idempotent") == purchaseRevision,
                "duplicate wall weapon purchase should not advance revision");
    }

    private static void weaponWallCommitFailureDoesNotSpendMutateOrRevise() {
        Services services = services();
        ZombiesObjectStateStore store = new ZombiesObjectStateStore();
        ZombiesWeaponWallData wall = weaponWall("wall-commit-fail", "tacz:m4a1");
        store.resetObjects(List.of(), List.of(wall), List.of(), List.of(), 1, 5);
        long initialRevision = stateRevision(store, List.of(wall), "wall-commit-fail");

        UUID playerId = playerId(60);
        services.economy.addPoints(playerId, 2_000.0D);
        ZombiesObjectInteractionService interactionService =
                interactionService(services, store, List.of(), List.of(wall), List.of(), List.of());

        ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> result =
                interactionService.purchaseWeaponWall(
                        playerId,
                        wall,
                        (currentWeapon, purchasedWeapon) ->
                                ZombiesServiceResult.failure(ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON));

        requireFailure(result, ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                "wall weapon inventory commit failure should fail purchase");
        requirePoints(services.players, playerId, 2_000.0D,
                "wall weapon inventory commit failure should not spend");
        requireNoPrimary(services.players, playerId,
                "wall weapon inventory commit failure should not write runtime primary");
        require(stateRevision(store, List.of(wall), "wall-commit-fail") == initialRevision,
                "wall weapon inventory commit failure should not advance revision");
    }

    private static void ammoBoxInvalidWeaponAndMissingPriceFailWithoutSpendOrRevision() {
        Services services = services();
        ZombiesObjectStateStore store = new ZombiesObjectStateStore();
        ZombiesAmmoBoxData ammoBox = new ZombiesAmmoBoxData(
                "ammo-closure",
                Map.of("2", 350),
                dimension(),
                new BlockPos(3, 64, 1),
                Optional.empty());
        store.resetObjects(List.of(), List.of(), List.of(ammoBox), List.of());
        long initialAmmoRevision = stateRevision(store, List.of(ammoBox), "ammo-closure");

        UUID noWeaponPlayer = playerId(7);
        services.economy.addPoints(noWeaponPlayer, 1_000.0D);
        requireFailure(
                services.ammo.refillPrimaryWeapon(noWeaponPlayer, ammoBox.pricesByWeaponLevel()),
                ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                "ammo box should reject player without valid primary weapon");
        requirePoints(services.players, noWeaponPlayer, 1_000.0D, "invalid ammo-box weapon should not spend");
        require(stateRevision(store, List.of(ammoBox), "ammo-closure") == initialAmmoRevision,
                "invalid ammo-box weapon should not advance revision");

        UUID missingPricePlayer = playerId(8);
        services.economy.addPoints(missingPricePlayer, 1_000.0D);
        services.players.getOrCreate(missingPricePlayer).setPrimaryWeapon(
                new ZombiesWeaponInstanceState("tacz:level3", 3, 0, 1.5D, 12, 240));
        requireFailure(
                services.ammo.refillPrimaryWeapon(missingPricePlayer, ammoBox.pricesByWeaponLevel()),
                ZombiesErrorCode.of("ammo.missing_price"),
                "ammo box should reject primary weapon with missing level price");
        requirePoints(services.players, missingPricePlayer, 1_000.0D, "missing ammo price should not spend");
        require(stateRevision(store, List.of(ammoBox), "ammo-closure") == initialAmmoRevision,
                "missing ammo price should not advance revision");
    }

    private static void ammoBoxCommitFailureDoesNotSpendMutateOrRevise() {
        Services services = services();
        ZombiesObjectStateStore store = new ZombiesObjectStateStore();
        ZombiesAmmoBoxData ammoBox = new ZombiesAmmoBoxData(
                "ammo-commit-fail",
                Map.of("2", 350),
                dimension(),
                new BlockPos(3, 64, 2),
                Optional.empty());
        store.resetObjects(List.of(), List.of(), List.of(ammoBox), List.of());
        long initialRevision = stateRevision(store, List.of(ammoBox), "ammo-commit-fail");

        UUID playerId = playerId(61);
        ZombiesWeaponInstanceState originalWeapon =
                new ZombiesWeaponInstanceState("tacz:m4a1", 2, 0, 1.25D, 40, 210);
        services.economy.addPoints(playerId, 1_000.0D);
        services.players.getOrCreate(playerId).setPrimaryWeapon(originalWeapon);
        ZombiesObjectInteractionService interactionService =
                interactionService(services, store, List.of(), List.of(), List.of(ammoBox), List.of());

        ZombiesServiceResult<ZombiesAmmoBoxService.AmmoRefillResult> result =
                interactionService.refillPrimaryAmmoBox(
                        playerId,
                        ammoBox,
                        (currentWeapon, refilledWeapon) ->
                                ZombiesServiceResult.failure(ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON));

        requireFailure(result, ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                "ammo box inventory commit failure should fail refill");
        requirePoints(services.players, playerId, 1_000.0D,
                "ammo box inventory commit failure should not spend");
        requirePrimary(services.players, playerId, originalWeapon,
                "ammo box inventory commit failure should preserve runtime primary");
        require(stateRevision(store, List.of(ammoBox), "ammo-commit-fail") == initialRevision,
                "ammo box inventory commit failure should not advance revision");
    }

    private static void armorRepeatAndDowngradeFailWithoutSpendOrRevision() {
        Services services = services();
        ZombiesObjectStateStore store = new ZombiesObjectStateStore();
        ZombiesArmorStationData levelTwo = armorStation("armor-2", 2, 750, 0.50D);
        ZombiesArmorStationData levelOne = armorStation("armor-1", 1, 500, 0.75D);
        store.resetObjects(List.of(), List.of(), List.of(), List.of(levelTwo, levelOne));
        long initialLevelOneRevision = stateRevision(store, List.of(levelTwo, levelOne), "armor-1");

        UUID playerId = playerId(9);
        services.economy.addPoints(playerId, 2_000.0D);
        requireSuccess(services.armor.purchaseArmor(playerId, levelTwo.armorLevel(), levelTwo.damageTakenMultiplier(), levelTwo.buyCost()),
                "initial armor purchase should succeed");
        store.markArmorStationPurchased(levelTwo);
        long levelTwoRevision = stateRevision(store, List.of(levelTwo, levelOne), "armor-2");

        requireFailure(
                services.armor.purchaseArmor(playerId, levelTwo.armorLevel(), levelTwo.damageTakenMultiplier(), levelTwo.buyCost()),
                ZombiesArmorService.armorAlreadyOwnedCode(),
                "repeat armor purchase should fail");
        requireFailure(
                services.armor.purchaseArmor(playerId, levelOne.armorLevel(), levelOne.damageTakenMultiplier(), levelOne.buyCost()),
                ZombiesArmorService.armorAlreadyOwnedCode(),
                "armor downgrade purchase should fail");
        requirePoints(services.players, playerId, 1_250.0D, "repeat and downgrade armor should not spend");
        requireArmor(services.players, playerId, 2, 0.50D, "repeat and downgrade should keep higher armor");
        require(stateRevision(store, List.of(levelTwo, levelOne), "armor-2") == levelTwoRevision,
                "failed armor purchases should not advance purchased station revision");
        require(stateRevision(store, List.of(levelTwo, levelOne), "armor-1") == initialLevelOneRevision,
                "failed armor downgrade should not mark lower station revision");
    }

    private static Thread barrierPurchaseThread(
            Services services,
            ZombiesObjectStateStore store,
            List<ZombiesBarrierData> barriers,
            UUID playerId,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicReference<ZombiesServiceResult<ZombiesObjectStateStore.BarrierGroupUpdate>> result
    ) {
        return new Thread(() -> {
            ready.countDown();
            try {
                start.await();
                result.set(purchaseBarrierPure(services, store, barriers, playerId));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                result.set(ZombiesServiceResult.failure(ZombiesErrorCode.OBJECT_BUSY));
            }
        }, "zombies-barrier-closure-" + playerId.getLeastSignificantBits());
    }

    private static ZombiesServiceResult<ZombiesObjectStateStore.BarrierGroupUpdate> purchaseBarrierPure(
            Services services,
            ZombiesObjectStateStore store,
            List<ZombiesBarrierData> barriers,
            UUID playerId
    ) {
        return services.economy.spendAtomically(playerId, 750.0D, ignored ->
                store.clearBarrierGroup(2, barriers));
    }

    private static ZombiesObjectInteractionService interactionService(
            Services services,
            ZombiesObjectStateStore store,
            List<ZombiesBarrierData> barriers,
            List<ZombiesWeaponWallData> weaponWalls,
            List<ZombiesAmmoBoxData> ammoBoxes,
            List<ZombiesArmorStationData> armorStations
    ) {
        return new ZombiesObjectInteractionService(
                com.cdp.codpattern.app.match.model.RoomId.of(com.cdp.codpattern.app.match.BuiltInGameModes.ZOMBIES, "mvp2-closure"),
                () -> barriers,
                () -> weaponWalls,
                () -> ammoBoxes,
                () -> armorStations,
                Optional::empty,
                List::of,
                List::of,
                nullBarrierService(services, store),
                services.weapons,
                services.ammo,
                services.armor,
                services.power,
                services.buffs,
                services.ultimate,
                store);
    }

    private static ZombiesBarrierService nullBarrierService(Services services, ZombiesObjectStateStore store) {
        com.cdp.codpattern.app.match.model.RoomId roomId =
                com.cdp.codpattern.app.match.model.RoomId.of(com.cdp.codpattern.app.match.BuiltInGameModes.ZOMBIES, "mvp2-closure");
        return new ZombiesBarrierService(
                roomId,
                List::of,
                services.economy,
                store,
                new ZombiesActiveSpawnGroupService(),
                ignored -> true,
                () -> com.cdp.codpattern.app.zombies.model.ZombiesGamePhase.WAVE_ACTIVE);
    }

    private static Services services() {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        ZombiesPowerService power = new ZombiesPowerService(economy);
        return new Services(
                players,
                economy,
                power,
                new ZombiesWeaponInstanceService(economy),
                new ZombiesAmmoBoxService(economy),
                new ZombiesArmorService(economy),
                new ZombiesBuffService(economy, power),
                new ZombiesUltimateMachineService(economy, power));
    }

    private static ModeObjectState modeObjectState(String objectKey, long revision, String gunId) {
        CompoundTag payload = new CompoundTag();
        payload.putString("type", "weapon_wall");
        payload.putString("objectId", objectKey);
        payload.putString("gunId", gunId);
        return new ModeObjectState(objectKey, "zombies.status", new BlockPos(1, 64, 1), payload, revision);
    }

    private static ZombiesBarrierData barrier(String objectId) {
        return new ZombiesBarrierData(
                objectId,
                2,
                750,
                true,
                dimension(),
                new BlockPos(5, 64, 5),
                new BlockPos(5, 66, 7),
                new BlockPos(5, 65, 5));
    }

    private static ZombiesWeaponWallData weaponWall(String objectId, String gunId) {
        return new ZombiesWeaponWallData(
                objectId,
                dimension(),
                new BlockPos(1, 64, 1),
                Optional.empty());
    }

    private static ZombiesArmorStationData armorStation(String objectId, int level, int cost, double multiplier) {
        return new ZombiesArmorStationData(
                objectId,
                level,
                cost,
                multiplier,
                dimension(),
                new BlockPos(4, 64, level),
                Optional.empty());
    }

    private static long stateRevision(ZombiesObjectStateStore store, List<?> objects, String objectKey) {
        List<ModeObjectState> states;
        Object first = objects.isEmpty() ? null : objects.get(0);
        if (first instanceof ZombiesWeaponWallData) {
            states = store.objectStates(List.of(), cast(objects), List.of(), List.of());
        } else if (first instanceof ZombiesAmmoBoxData) {
            states = store.objectStates(List.of(), List.of(), cast(objects), List.of());
        } else if (first instanceof ZombiesArmorStationData) {
            states = store.objectStates(List.of(), List.of(), List.of(), cast(objects));
        } else {
            states = new ArrayList<>();
        }
        return states.stream()
                .filter(state -> objectKey.equals(state.objectKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing object state " + objectKey))
                .revision();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> cast(List<?> values) {
        return (List<T>) values;
    }

    private static void requireArmor(
            ZombiesPlayerStateService players,
            UUID playerId,
            int expectedLevel,
            double expectedMultiplier,
            String message
    ) {
        ZombiesArmorState armor = players.get(playerId)
                .flatMap(ZombiesPlayerRuntimeState::armor)
                .orElseThrow(() -> new AssertionError(message + ": expected armor"));
        require(armor.armorLevel() == expectedLevel,
                message + ": expected armor level " + expectedLevel + " but was " + armor.armorLevel());
        requireClose(armor.damageTakenMultiplier(), expectedMultiplier, message + ": damage multiplier");
    }

    private static void requireNoPrimary(ZombiesPlayerStateService players, UUID playerId, String message) {
        require(players.get(playerId).orElseThrow().primaryWeapon().isEmpty(), message);
    }

    private static void requirePrimary(
            ZombiesPlayerStateService players,
            UUID playerId,
            ZombiesWeaponInstanceState expected,
            String message
    ) {
        ZombiesWeaponInstanceState actual = players.get(playerId)
                .flatMap(ZombiesPlayerRuntimeState::primaryWeapon)
                .orElseThrow(() -> new AssertionError(message + ": expected primary weapon"));
        require(actual.equals(expected), message + ": expected " + expected + " but was " + actual);
    }

    private static void requirePoints(ZombiesPlayerStateService players, UUID playerId, double expected, String message) {
        requireClose(players.get(playerId).orElseThrow().points(), expected, message + ": balance");
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

    private record Services(
            ZombiesPlayerStateService players,
            ZombiesEconomyService economy,
            ZombiesPowerService power,
            ZombiesWeaponInstanceService weapons,
            ZombiesAmmoBoxService ammo,
            ZombiesArmorService armor,
            ZombiesBuffService buffs,
            ZombiesUltimateMachineService ultimate
    ) {
    }
}
