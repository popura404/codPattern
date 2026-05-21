package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.ModeObjectState;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.object.ZombiesPowerSwitchData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesSodaMachineData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesUltimateMachineData;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;
import com.cdp.codpattern.config.zombies.ZombiesRulesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ZombiesMvp3ObjectRuntimeCompatTest {
    private ZombiesMvp3ObjectRuntimeCompatTest() {
    }

    public static void main(String[] args) {
        objectStatesExposePowerSodaAndUltimateRuntimePayload();
        objectInteractionsBridgeMvp3ServicesAndRevisions();
    }

    private static void objectStatesExposePowerSodaAndUltimateRuntimePayload() {
        Services services = services();
        ZombiesRulesConfig rules = ultimateRules();
        ZombiesObjectStateStore store = new ZombiesObjectStateStore(services.power::isPowerOn, null, () -> rules);
        Fixtures fixtures = fixtures();
        store.resetObjects(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Optional.of(fixtures.powerSwitch()),
                List.of(fixtures.sodaMachine()),
                List.of(fixtures.ultimateMachine()),
                1,
                5);

        List<ModeObjectState> offStates = objectStates(store, fixtures);
        requirePayload(state(offStates, "power-1"), "power_switch", true, 500);
        require(!state(offStates, "power-1").payload().getBoolean("powerOn"),
                "power switch should expose powerOff initially");
        requirePayload(state(offStates, "soda-health"), "soda_machine", false, 250);
        require(state(offStates, "soda-health").payload().getBoolean("requiresPower"),
                "soda should expose requiresPower");
        require(!state(offStates, "soda-health").payload().getBoolean("powerOn"),
                "soda should expose powerOff initially");
        require("double_health".equals(state(offStates, "soda-health").payload().getString("buffId")),
                "soda should expose buffId");
        requirePayload(state(offStates, "ultimate-1"), "ultimate_machine", false, 700);
        require(state(offStates, "ultimate-1").payload().getInt("maxUpgradeLevel") == 2,
                "ultimate should expose maxUpgradeLevel");

        services.economy.addPoints(playerId(1), 1_000.0D);
        requireSuccess(services.power.turnOn(playerId(1), 500.0D), "setup power purchase should succeed");
        long powerRevision = store.markPowerSwitchTurnedOn(fixtures.powerSwitch());

        List<ModeObjectState> onStates = objectStates(store, fixtures);
        require(state(onStates, "power-1").revision() > 0L, "power switch revision should advance after power on");
        require(state(onStates, "power-1").revision() <= powerRevision,
                "power mark should return the latest affected revision");
        require(state(onStates, "power-1").payload().getBoolean("powerOn"),
                "power switch should expose powerOn after purchase");
        require(!state(onStates, "power-1").payload().getBoolean("enabled"),
                "power switch should be disabled after power is on");
        require(state(onStates, "soda-health").payload().getBoolean("enabled"),
                "requiresPower soda should become enabled after power is on");
        require(state(onStates, "ultimate-1").payload().getBoolean("enabled"),
                "requiresPower ultimate should become enabled after power is on");
        require(state(onStates, "soda-health").revision() > state(offStates, "soda-health").revision(),
                "power on should advance requiresPower soda revision");
        require(state(onStates, "ultimate-1").revision() > state(offStates, "ultimate-1").revision(),
                "power on should advance requiresPower ultimate revision");
    }

    private static void objectInteractionsBridgeMvp3ServicesAndRevisions() {
        Services services = services();
        ZombiesRulesConfig rules = ultimateRules();
        ZombiesObjectStateStore store = new ZombiesObjectStateStore(services.power::isPowerOn, null, () -> rules);
        ZombiesObjectInteractionService interactionService = interactionService(services, store, rules);
        Fixtures fixtures = fixtures();
        store.resetObjects(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Optional.of(fixtures.powerSwitch()),
                List.of(fixtures.sodaMachine()),
                List.of(fixtures.ultimateMachine()),
                1,
                5);
        long initialSodaRevision = stateRevision(store, fixtures, "soda-health");
        long initialUltimateRevision = stateRevision(store, fixtures, "ultimate-1");
        UUID playerId = playerId(2);
        services.economy.addPoints(playerId, 3_000.0D);
        services.players.getOrCreate(playerId).setPrimaryWeapon(
                new ZombiesWeaponInstanceState("tacz:m4a1", 2, 0, 1.25D, 120, 210));

        ZombiesServiceResult<ZombiesBuffService.BuffPurchaseResult> lockedSoda =
                interactionService.purchaseSodaMachine(playerId, fixtures.sodaMachine());
        requireFailure(lockedSoda, ZombiesErrorCode.POWER_REQUIRES_POWER,
                "requiresPower soda interaction should fail while power is off");
        requirePoints(services.players, playerId, 3_000.0D, "locked soda should not spend");
        require(stateRevision(store, fixtures, "soda-health") == initialSodaRevision,
                "locked soda should not advance revision");

        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> lockedUltimate =
                interactionService.useUltimateMachine(playerId, fixtures.ultimateMachine());
        requireFailure(lockedUltimate, ZombiesErrorCode.POWER_REQUIRES_POWER,
                "requiresPower ultimate interaction should fail while power is off");
        requirePoints(services.players, playerId, 3_000.0D, "locked ultimate should not spend");
        require(stateRevision(store, fixtures, "ultimate-1") == initialUltimateRevision,
                "locked ultimate should not advance revision");

        requireSuccess(interactionService.purchasePowerSwitch(playerId, fixtures.powerSwitch()),
                "power interaction should turn power on");
        require(services.power.isPowerOn(), "power interaction should update shared power state");
        requirePoints(services.players, playerId, 2_500.0D, "power interaction should spend configured cost");
        long powerRevision = stateRevision(store, fixtures, "power-1");
        require(powerRevision > 0L, "power interaction should advance power switch revision");

        requireSuccess(interactionService.purchasePowerSwitch(playerId, fixtures.powerSwitch()),
                "repeat power interaction should be non-destructive success");
        requirePoints(services.players, playerId, 2_500.0D, "repeat power interaction should not spend");
        require(stateRevision(store, fixtures, "power-1") == powerRevision,
                "repeat power interaction should not advance power revision");

        requireSuccess(interactionService.purchaseSodaMachine(playerId, fixtures.sodaMachine()),
                "soda interaction should purchase buff after power is on");
        requirePoints(services.players, playerId, 2_250.0D, "soda interaction should spend configured cost");
        require(stateRevision(store, fixtures, "soda-health") > powerRevision,
                "successful soda interaction should advance soda revision");

        long ultimateBefore = stateRevision(store, fixtures, "ultimate-1");
        requireSuccess(interactionService.useUltimateMachine(playerId, fixtures.ultimateMachine()),
                "ultimate interaction should upgrade current weapon");
        requirePoints(services.players, playerId, 1_550.0D, "ultimate interaction should spend target level cost");
        require(primary(services.players, playerId).upgradeLevel() == 1,
                "ultimate interaction should update primary weapon upgrade");
        requireClose(primary(services.players, playerId).damageMultiplier(), 1.75D,
                "ultimate interaction should use serverconfig level damage");
        require(stateRevision(store, fixtures, "ultimate-1") > ultimateBefore,
                "successful ultimate interaction should advance ultimate revision");

        long ultimateAfter = stateRevision(store, fixtures, "ultimate-1");
        requireSuccess(interactionService.useUltimateMachine(playerId, fixtures.ultimateMachine()),
                "second ultimate interaction should upgrade to max level");
        require(primary(services.players, playerId).upgradeLevel() == 2,
                "second ultimate interaction should update primary weapon to max");
        requireClose(primary(services.players, playerId).damageMultiplier(), 2.25D,
                "second ultimate interaction should use serverconfig max-level damage");

        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> max =
                interactionService.useUltimateMachine(playerId, fixtures.ultimateMachine());
        requireFailure(max, ZombiesErrorCode.WEAPON_MAX_UPGRADE, "max-level ultimate interaction should fail");
        require(stateRevision(store, fixtures, "ultimate-1") > ultimateAfter,
                "second successful ultimate should have advanced revision");
    }

    private static ZombiesObjectInteractionService interactionService(
            Services services,
            ZombiesObjectStateStore store,
            ZombiesRulesConfig rules
    ) {
        RoomId roomId = RoomId.of(BuiltInGameModes.ZOMBIES, "mvp3-runtime");
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
                new ZombiesWeaponInstanceService(services.economy),
                new ZombiesAmmoBoxService(services.economy),
                new ZombiesArmorService(services.economy),
                services.power,
                services.buffs,
                services.ultimate,
                store,
                null,
                () -> rules);
    }

    private static List<ModeObjectState> objectStates(ZombiesObjectStateStore store, Fixtures fixtures) {
        return store.objectStates(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Optional.of(fixtures.powerSwitch()),
                List.of(fixtures.sodaMachine()),
                List.of(fixtures.ultimateMachine()));
    }

    private static long stateRevision(ZombiesObjectStateStore store, Fixtures fixtures, String objectKey) {
        return state(objectStates(store, fixtures), objectKey).revision();
    }

    private static Services services() {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        ZombiesPowerService power = new ZombiesPowerService(economy);
        return new Services(
                players,
                economy,
                power,
                new ZombiesBuffService(economy, power),
                new ZombiesUltimateMachineService(economy, power));
    }

    private static Fixtures fixtures() {
        ZombiesPowerSwitchData powerSwitch = new ZombiesPowerSwitchData(
                "power-1",
                "codpattern:zombies_power_switch",
                500,
                dimension(),
                new BlockPos(1, 64, 1),
                Optional.of(new BlockPos(2, 64, 1)));
        ZombiesSodaMachineData sodaMachine = new ZombiesSodaMachineData(
                "soda-health",
                "double_health",
                250,
                true,
                dimension(),
                new BlockPos(3, 64, 1),
                Optional.empty());
        ZombiesUltimateMachineData ultimateMachine = new ZombiesUltimateMachineData(
                "ultimate-1",
                9,
                Map.of(
                        "1", new ZombiesUltimateMachineData.UpgradeLevelData(1, 9.0D),
                        "2", new ZombiesUltimateMachineData.UpgradeLevelData(2, 9.5D)),
                true,
                dimension(),
                new BlockPos(4, 64, 1),
                Optional.empty());
        return new Fixtures(powerSwitch, sodaMachine, ultimateMachine);
    }

    private static ZombiesRulesConfig ultimateRules() {
        ZombiesRulesConfig config = new ZombiesRulesConfig();
        ZombiesRulesConfig.UltimateMachine rules = new ZombiesRulesConfig.UltimateMachine();
        rules.setMaxUpgradeLevel(2);
        rules.setLevels(Map.of(
                "1", new ZombiesRulesConfig.UpgradeLevel(700, 1.75D),
                "2", new ZombiesRulesConfig.UpgradeLevel(1_100, 2.25D)));
        config.setUltimateMachine(rules);
        config.normalize();
        return config;
    }

    private static ModeObjectState state(List<ModeObjectState> states, String objectKey) {
        return states.stream()
                .filter(state -> objectKey.equals(state.objectKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing object state " + objectKey));
    }

    private static ZombiesWeaponInstanceState primary(ZombiesPlayerStateService players, UUID playerId) {
        return players.get(playerId)
                .flatMap(ZombiesPlayerRuntimeState::primaryWeapon)
                .orElseThrow(() -> new AssertionError("expected primary weapon"));
    }

    private static void requirePayload(
            ModeObjectState state,
            String expectedType,
            boolean expectedEnabled,
            int expectedCost
    ) {
        require(expectedType.equals(state.payload().getString("type")),
                "expected type " + expectedType + " but was " + state.payload().getString("type"));
        require(state.payload().getBoolean("enabled") == expectedEnabled,
                "expected enabled " + expectedEnabled + " for " + state.objectKey());
        require(state.payload().getInt("cost") == expectedCost,
                "expected cost " + expectedCost + " for " + state.objectKey());
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
            ZombiesBuffService buffs,
            ZombiesUltimateMachineService ultimate
    ) {
    }

    private record Fixtures(
            ZombiesPowerSwitchData powerSwitch,
            ZombiesSodaMachineData sodaMachine,
            ZombiesUltimateMachineData ultimateMachine
    ) {
    }
}
