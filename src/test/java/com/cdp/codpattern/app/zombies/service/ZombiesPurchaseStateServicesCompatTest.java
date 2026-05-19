package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.object.ZombiesWeaponWallData;
import com.cdp.codpattern.app.zombies.model.ZombiesArmorState;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;
import com.cdp.codpattern.app.zombies.sync.ZombiesRuntimeStateKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ZombiesPurchaseStateServicesCompatTest {
    private ZombiesPurchaseStateServicesCompatTest() {
    }

    public static void main(String[] args) {
        wallWeaponPurchaseSetsUniquePrimaryAndDuplicateFailsWithoutSpend();
        wallWeaponInteractionPurchaseUsesRuntimeCurrentOffer();
        ammoBoxRefillsHeldWeaponsByWeaponLevelAndStarterIsCharged();
        armorUpgradeReplacesLowerLevelAndRepeatOrDowngradeFails();
    }

    private static void wallWeaponPurchaseSetsUniquePrimaryAndDuplicateFailsWithoutSpend() {
        Services services = services();
        UUID playerId = playerId(1);
        services.economy.addPoints(playerId, 1_000.0D);

        ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> purchase =
                services.weapons.purchaseWallWeapon(playerId, "tacz:m4a1", 2, 1.25D, 210, 600.0D);

        requireSuccess(purchase, "first wall weapon purchase should succeed");
        ZombiesWeaponInstanceState primary = primary(services.players, playerId);
        require("tacz:m4a1".equals(primary.gunId()), "purchase should set primary gun id");
        require(primary.weaponLevel() == 2, "purchase should set weapon level");
        requireClose(primary.damageMultiplier(), 1.25D, "purchase should set damage multiplier");
        require(primary.reserveAmmo() == 210, "purchase should fill reserve ammo");
        requirePoints(services.players, playerId, 400.0D, "purchase should deduct wall weapon price");
        requireIntPlayerValue(services.players, playerId, ZombiesRuntimeStateKeys.PLAYER_WEAPON_PRIMARY_LEVEL, 2,
                "player values should expose primary weapon level");
        requireIntPlayerValue(services.players, playerId, ZombiesRuntimeStateKeys.PLAYER_WEAPON_PRIMARY_UPGRADE, 0,
                "player values should expose primary weapon upgrade");
        requireIntPlayerValue(services.players, playerId, ZombiesRuntimeStateKeys.PLAYER_ARMOR_LEVEL, 0,
                "player values should expose missing armor as zero");

        ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> duplicate =
                services.weapons.purchaseWallWeapon(playerId, "tacz:m4a1", 2, 1.25D, 210, 700.0D);

        requireFailure(duplicate, ZombiesErrorCode.WEAPON_ALREADY_OWNED,
                "same gun id and level should fail as already owned");
        requirePoints(services.players, playerId, 400.0D, "duplicate wall weapon purchase should not deduct");
        require("tacz:m4a1".equals(primary(services.players, playerId).gunId()),
                "duplicate wall weapon purchase should keep current primary");

        ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> replacement =
                services.weapons.purchaseWallWeapon(playerId, "tacz:ak47", 2, 1.30D, 180, 250.0D);

        requireSuccess(replacement, "different gun id should replace the unique primary weapon");
        require("tacz:ak47".equals(primary(services.players, playerId).gunId()),
                "replacement should become the unique primary weapon");
        requirePoints(services.players, playerId, 150.0D, "replacement should deduct its price");
    }

    private static void wallWeaponInteractionPurchaseUsesRuntimeCurrentOffer() {
        Services services = services();
        ZombiesObjectStateStore store = new ZombiesObjectStateStore(
                () -> false,
                fixedOfferService("legendary", "tacz:runtime_offer", 900, 300, 1.75D, 5));
        ZombiesObjectInteractionService interactionService = interactionService(services, store);
        ZombiesWeaponWallData wall = new ZombiesWeaponWallData(
                "wall-runtime-offer",
                dimension(),
                new BlockPos(1, 64, 1),
                Optional.empty());
        UUID playerId = playerId(4);
        services.economy.addPoints(playerId, 2_000.0D);
        store.resetObjects(List.of(), List.of(wall), List.of(), List.of(), 1, 5);
        store.refreshWeaponWallOffersForWave(List.of(wall), 5, 5);
        long refreshRevision = stateRevision(store, wall);

        ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> purchase =
                interactionService.purchaseWeaponWall(playerId, wall);

        requireSuccess(purchase, "wall interaction purchase should succeed");
        require("tacz:runtime_offer".equals(primary(services.players, playerId).gunId()),
                "wall interaction purchase should use runtime current offer from rules service");
        require("legendary".equals(primary(services.players, playerId).rarityId()),
                "runtime offer purchase should write rarity id");
        require(primary(services.players, playerId).weaponLevel() == 1,
                "runtime offer purchase should use internal compatibility weapon level");
        requireClose(primary(services.players, playerId).damageMultiplier(), 1.75D,
                "runtime offer purchase should use configured damage multiplier");
        require(primary(services.players, playerId).reserveAmmo() == 300,
                "runtime offer purchase should use configured reserve ammo");
        long purchaseRevision = stateRevision(store, wall);
        require(purchaseRevision > refreshRevision, "successful runtime offer purchase should advance wall revision");

        ZombiesServiceResult<ZombiesWeaponInstanceService.WallWeaponPurchaseResult> duplicate =
                interactionService.purchaseWeaponWall(playerId, wall);

        requireFailure(duplicate, ZombiesErrorCode.WEAPON_ALREADY_OWNED,
                "duplicate runtime offer purchase should fail");
        require(stateRevision(store, wall) == purchaseRevision,
                "failed runtime offer purchase should not advance wall revision");
    }

    private static void ammoBoxRefillsHeldWeaponsByWeaponLevelAndStarterIsCharged() {
        Services services = services();
        UUID playerId = playerId(2);
        services.economy.addPoints(playerId, 1_000.0D);
        requireSuccess(
                services.weapons.purchaseWallWeapon(playerId, "tacz:m4a1", 2, 1.25D, 210, 100.0D),
                "setup wall weapon purchase should succeed");
        ZombiesPlayerRuntimeState state = services.players.getOrCreate(playerId);
        state.setPrimaryWeapon(primary(services.players, playerId).withReserveAmmo(12));

        ZombiesServiceResult<ZombiesAmmoBoxService.AmmoRefillResult> refill =
                services.ammo.refillPrimaryWeapon(playerId, Map.of("1", 200, "2", 350, "3", 500));

        requireSuccess(refill, "ammo box should refill primary weapon using weapon level price");
        require(primary(services.players, playerId).reserveAmmo() == 210, "ammo box should refill reserve to max");
        requirePoints(services.players, playerId, 550.0D, "ammo box should deduct level 2 price");

        ZombiesWeaponInstanceState starterWeapon = ZombiesWeaponInstanceState.primary("tacz:starter", 1, 1.0D, 84)
                .withReserveAmmo(12);
        state.setStarterWeapon(starterWeapon);
        ZombiesServiceResult<ZombiesAmmoBoxService.AmmoRefillResult> starterRefill =
                services.ammo.refillHeldWeapon(
                        playerId,
                        starterWeapon,
                        Map.of("1", 200, "2", 350, "3", 500),
                        (currentWeapon, refilledWeapon) -> ZombiesServiceResult.ok());

        requireSuccess(starterRefill, "starter weapon refill should use priced held-weapon path");
        require(starterRefill.value().orElseThrow().weapon().reserveAmmo() == 84,
                "starter weapon refill result should expose full reserve");
        requirePoints(services.players, playerId, 350.0D, "starter weapon refill should deduct level 1 price");
    }

    private static void armorUpgradeReplacesLowerLevelAndRepeatOrDowngradeFails() {
        Services services = services();
        UUID playerId = playerId(3);
        services.economy.addPoints(playerId, 3_000.0D);

        requireSuccess(services.armor.purchaseArmor(playerId, 1, 0.75D, 500.0D),
                "level 1 armor purchase should succeed");
        requireArmor(services.players, playerId, 1, 0.75D, "level 1 armor should be owned");
        requirePoints(services.players, playerId, 2_500.0D, "level 1 armor should deduct cost");

        requireSuccess(services.armor.purchaseArmor(playerId, 3, 0.35D, 1_200.0D),
                "higher armor level should replace lower armor level");
        requireArmor(services.players, playerId, 3, 0.35D, "level 3 armor should replace level 1");
        requirePoints(services.players, playerId, 1_300.0D, "level 3 armor should deduct cost");
        requireIntPlayerValue(services.players, playerId, ZombiesRuntimeStateKeys.PLAYER_ARMOR_LEVEL, 3,
                "player values should expose armor level");

        requireFailure(
                services.armor.purchaseArmor(playerId, 3, 0.35D, 2_000.0D),
                ZombiesArmorService.armorAlreadyOwnedCode(),
                "repeat armor level should fail");
        requirePoints(services.players, playerId, 1_300.0D, "repeat armor purchase should not deduct");

        requireFailure(
                services.armor.purchaseArmor(playerId, 2, 0.50D, 1_400.0D),
                ZombiesArmorService.armorAlreadyOwnedCode(),
                "lower armor level should fail when higher level is owned");
        requireArmor(services.players, playerId, 3, 0.35D, "downgrade attempt should keep higher armor");
        requirePoints(services.players, playerId, 1_300.0D, "downgrade armor purchase should not deduct");
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

    private static ZombiesObjectInteractionService interactionService(
            Services services,
            ZombiesObjectStateStore store
    ) {
        RoomId roomId = RoomId.of(BuiltInGameModes.ZOMBIES, "compat");
        return new ZombiesObjectInteractionService(
                roomId,
                List::of,
                List::of,
                List::of,
                List::of,
                Optional::empty,
                List::of,
                List::of,
                new ZombiesBarrierService(
                        roomId,
                        List::of,
                        services.economy,
                        store,
                        new ZombiesActiveSpawnGroupService(),
                        ignored -> true,
                        () -> ZombiesGamePhase.WAVE_ACTIVE),
                services.weapons,
                services.ammo,
                services.armor,
                services.power,
                services.buffs,
                services.ultimate,
                store);
    }

    private static long stateRevision(ZombiesObjectStateStore store, ZombiesWeaponWallData wall) {
        return store.objectStates(List.of(), List.of(wall), List.of(), List.of()).stream()
                .filter(state -> wall.objectId().equals(state.objectKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing wall state " + wall.objectId()))
                .revision();
    }

    private static ZombiesWeaponWallOfferService fixedOfferService(
            String rarityId,
            String gunId,
            int price,
            int maxReserveAmmo,
            double damageMultiplier,
            int refreshIntervalWaves
    ) {
        com.cdp.codpattern.config.zombies.ZombiesRulesConfig config =
                new com.cdp.codpattern.config.zombies.ZombiesRulesConfig();
        config.getWeaponWall().setRefreshIntervalWaves(refreshIntervalWaves);
        return new ZombiesWeaponWallOfferService(
                () -> config,
                new java.util.Random(0L),
                ignored -> net.minecraft.world.item.ItemStack.EMPTY) {
            @Override
            public ZombiesObjectStateStore.WeaponWallOffer createOffer(
                    ZombiesWeaponWallData weaponWall,
                    int currentWave
            ) {
                return new ZombiesObjectStateStore.WeaponWallOffer(
                        weaponWall.objectId(),
                        rarityId,
                        gunId,
                        price,
                        maxReserveAmmo,
                        damageMultiplier);
            }
        };
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

    private static ZombiesWeaponInstanceState primary(ZombiesPlayerStateService players, UUID playerId) {
        return players.get(playerId)
                .flatMap(ZombiesPlayerRuntimeState::primaryWeapon)
                .orElseThrow(() -> new AssertionError("expected primary weapon"));
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

    private static void requirePoints(ZombiesPlayerStateService players, UUID playerId, double expected, String message) {
        requireClose(players.get(playerId).orElseThrow().points(), expected, message + ": balance");
    }

    private static void requireIntPlayerValue(
            ZombiesPlayerStateService players,
            UUID playerId,
            String key,
            int expected,
            String message
    ) {
        String value = players.playerValues(playerId).get(key).value();
        require(Integer.toString(expected).equals(value), message + ": expected " + expected + " but was " + value);
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
