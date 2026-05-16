package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.map.object.ZombiesPowerSwitchData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class ZombiesPowerSwitchBlockSyncCompatTest {
    private ZombiesPowerSwitchBlockSyncCompatTest() {
    }

    public static void main(String[] args) {
        successfulPowerPurchaseSyncsPoweredAndRepeatDoesNotSpend();
        resetSyncsUnpoweredEvenWhenAlreadyOff();
        blockSyncFailureDoesNotAffectPurePowerState();
        blockStateServiceIgnoresUnsupportedOrMissingSwitch();
    }

    private static void successfulPowerPurchaseSyncsPoweredAndRepeatDoesNotSpend() {
        List<Boolean> syncedStates = new ArrayList<>();
        Services services = services(syncedStates::add);
        UUID playerId = playerId(1);
        services.economy.addPoints(playerId, 1_000.0D);

        requireSuccess(services.power.turnOn(playerId, 500.0D),
                "successful power purchase should turn power on");

        require(services.power.isPowerOn(), "successful power purchase should update pure state");
        requirePoints(services.players, playerId, 500.0D, "successful power purchase should deduct cost");
        require(syncedStates.equals(List.of(Boolean.TRUE)),
                "successful power purchase should sync the switch block to powered exactly once");

        ZombiesServiceResult<ZombiesPowerService.PowerPurchaseResult> repeat =
                services.power.turnOn(playerId, 500.0D);

        requireFailure(repeat, ZombiesErrorCode.POWER_ALREADY_ON,
                "repeat power purchase should fail as already on");
        requirePoints(services.players, playerId, 500.0D, "repeat power purchase should not deduct");
        require(syncedStates.equals(List.of(Boolean.TRUE)),
                "repeat power purchase should not resync or double-toggle the switch block");
    }

    private static void resetSyncsUnpoweredEvenWhenAlreadyOff() {
        List<Boolean> syncedStates = new ArrayList<>();
        Services services = services(syncedStates::add);
        UUID playerId = playerId(2);
        services.economy.addPoints(playerId, 200.0D);

        requireSuccess(services.power.turnOn(playerId, 100.0D), "setup power purchase should succeed");
        services.power.reset();
        services.power.reset();

        require(!services.power.isPowerOn(), "reset should clear pure power state");
        require(syncedStates.equals(List.of(Boolean.TRUE, Boolean.FALSE, Boolean.FALSE)),
                "cleanup reset should always sync the switch block back to unpowered");
    }

    private static void blockSyncFailureDoesNotAffectPurePowerState() {
        Services services = services(ignored -> {
            throw new IllegalStateException("missing power switch block");
        });
        UUID playerId = playerId(3);
        services.economy.addPoints(playerId, 300.0D);

        requireSuccess(services.power.turnOn(playerId, 200.0D),
                "missing block sync should not fail power purchase");
        require(services.power.isPowerOn(), "missing block sync should not revert pure power state");
        requirePoints(services.players, playerId, 100.0D, "missing block sync should not change economy semantics");

        services.power.reset();
        require(!services.power.isPowerOn(), "missing block sync should not fail cleanup reset");
    }

    private static void blockStateServiceIgnoresUnsupportedOrMissingSwitch() {
        AtomicInteger levelResolutions = new AtomicInteger();
        ZombiesPowerSwitchBlockStateService blockStateService =
                new ZombiesPowerSwitchBlockStateService(ignored -> {
                    levelResolutions.incrementAndGet();
                    return null;
                });

        ZombiesPowerSwitchData unsupported = new ZombiesPowerSwitchData(
                "not-power",
                "minecraft:lever",
                0,
                dimension(),
                new BlockPos(1, 64, 1),
                Optional.empty());

        require(!blockStateService.setPowered(unsupported, true),
                "unsupported block id should not be updated");
        require(levelResolutions.get() == 0,
                "unsupported block id should not resolve a level");

        ZombiesPowerSwitchData missing = new ZombiesPowerSwitchData(
                "power",
                "codpattern:zombies_power_switch",
                0,
                dimension(),
                new BlockPos(2, 64, 2),
                Optional.empty());

        require(!blockStateService.setPowered(Optional.of(missing), true),
                "missing level or block should be a no-op failure");
        require(levelResolutions.get() == 1,
                "supported switch should resolve its configured dimension once");
        require(!blockStateService.setPowered(Optional.empty(), false),
                "missing switch data should be a no-op failure");

        ZombiesPowerSwitchBlockStateService throwingBlockStateService =
                new ZombiesPowerSwitchBlockStateService(ignored -> {
                    throw new IllegalStateException("level unavailable");
                });
        require(!throwingBlockStateService.setPowered(missing, false),
                "level resolver failure should be a no-op failure");
    }

    private static Services services(ZombiesPowerService.PowerSwitchStateSink powerSwitchStateSink) {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        ZombiesPowerService power = new ZombiesPowerService(economy, powerSwitchStateSink);
        return new Services(players, economy, power);
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
            ZombiesPowerService power
    ) {
    }
}
