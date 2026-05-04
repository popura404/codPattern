package com.cdp.codpattern.app.zombies.service;

import java.util.List;

public final class ZombiesMvp3CompatTestSuite {
    private static final List<ZombiesCompatSuiteRunner.TestEntry> TESTS = List.of(
            new ZombiesCompatSuiteRunner.TestEntry("MVP2/MVP3 map validator compat",
                    "com.cdp.codpattern.app.zombies.validation.ZombiesMapValidatorMvp2Mvp3CompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP3 growth services compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesMvp3GrowthServicesCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP3 intermission respawn compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesIntermissionRespawnServiceCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP3 equipment snapshot compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesEquipmentSnapshotServicesCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP3 object runtime compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesMvp3ObjectRuntimeCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP3 power switch block sync compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesPowerSwitchBlockSyncCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP3 post-game teleport compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesPostGameTeleportServiceCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP3 crash recovery compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesCrashRecoveryServiceCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP3 runtime closure compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesMvp3RuntimeClosureCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP3 deep coverage compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesMvp3DeepCoverageCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP3 additional growth closure compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesMvp3AdditionalClosureCompatTest",
                    true)
    );
    private static final List<CoverageItem> PURE_JVM_COVERAGE = List.of(
            new CoverageItem(
                    "validator/schema",
                    "MVP3_FULL_INITIAL unique power, requiresPower, soda, ultimate, dimension, and bounds checks"),
            new CoverageItem(
                    "power/soda/ultimate",
                    "power cost/repeat semantics, soda power gates/repeats, and ultimate upgrade/failure paths"),
            new CoverageItem(
                    "buff combat",
                    "score multiplier rewards, armor plus double_health reduction, and reactive explosion cooldown scoping"),
            new CoverageItem(
                    "revive",
                    "intermission candidate selection, member-order spawn planning, successful prep, and teleport failure handling"),
            new CoverageItem(
                    "reconnect cleanup",
                    "offline pending endtp write, rewrite, clear, and one-shot consume behavior"),
            new CoverageItem(
                    "crash cleanup",
                    "server-stop running-room cleanup and startup/stopping occupancy cleanup entry points")
    );
    private static final List<GameTestOnlyCoverage> GAME_TEST_ONLY_COVERAGE = List.of(
            new GameTestOnlyCoverage(
                    "com.cdp.codpattern.app.zombies.gametest.ZombiesRuntimeGameTests",
                    "cleanupServiceRunsClearHooks",
                    "zombiesruntimegametests.cleanupservicerunsclearhooks",
                    "cleanup service runtime clear hooks and map occupancy release"),
            new GameTestOnlyCoverage(
                    "com.cdp.codpattern.app.zombies.gametest.ZombiesRuntimeGameTests",
                    "cleanupServiceRemovesRegisteredRoomEntities",
                    "zombiesruntimegametests.cleanupserviceremovesregisteredroomentities",
                    "Forge entity removal path for same-room cleanup while preserving other-room entities"),
            new GameTestOnlyCoverage(
                    "com.cdp.codpattern.app.zombies.gametest.ZombiesRuntimeGameTests",
                    "weaponItemStackDamageMultiplierIsSameRoomScoped",
                    "zombiesruntimegametests.weaponitemstackdamagemultiplierissameroomscoped",
                    "Forge ItemStack/NBT same-room damage multiplier and tag strip path"),
            new GameTestOnlyCoverage(
                    "com.cdp.codpattern.app.zombies.gametest.ZombiesRuntimeGameTests",
                    "weaponInventoryAppliesPrimaryToRealPlayerInventoryAndReplacesOldPrimary",
                    "zombiesruntimegametests.weaponinventoryappliesprimarytorealplayerinventoryandreplacesoldprimary",
                    "Forge ServerPlayer inventory primary replacement path"),
            new GameTestOnlyCoverage(
                    "com.cdp.codpattern.app.zombies.gametest.ZombiesRuntimeGameTests",
                    "powerSwitchBlockStateServiceSetsAndResetsPoweredState",
                    "zombiesruntimegametests.powerswitchblockstateservicesetsandresetspoweredstate",
                    "Forge BlockState path for powering and resetting zombies power switch blocks"),
            new GameTestOnlyCoverage(
                    "com.cdp.codpattern.app.zombies.gametest.ZombiesRuntimeGameTests",
                    "reactiveExplosionDefaultExecutorDamagesOnlySameRoomMonsters",
                    "zombiesruntimegametests.reactiveexplosiondefaultexecutordamagesonlysameroommonsters",
                    "Forge entity damage path for reactive explosion same-room AOE isolation"),
            new GameTestOnlyCoverage(
                    "com.cdp.codpattern.app.zombies.gametest.ZombiesRuntimeGameTests",
                    "reactiveExplosionDefaultExecutorFailsWithoutServerPlayerContext",
                    "zombiesruntimegametests.reactiveexplosiondefaultexecutorfailswithoutserverplayercontext",
                    "reactive explosion default executor missing ServerPlayer context failure path"),
            new GameTestOnlyCoverage(
                    "com.cdp.codpattern.app.zombies.gametest.ZombiesRuntimeGameTests",
                    "objectInteractionPowerSwitchPowersPlacedBlockAndDeductsPoints",
                    "zombiesruntimegametests.objectinteractionpowerswitchpowersplacedblockanddeductspoints",
                    "Forge ServerPlayer object interaction path for power switch cost, object revision, and powered block sync")
    );

    private ZombiesMvp3CompatTestSuite() {
    }

    public static void main(String[] args) throws Throwable {
        int skipped = ZombiesCompatSuiteRunner.runAll(TESTS, args);
        if (skipped > 0) {
            System.err.println("ZombiesMvp3CompatTestSuite skipped " + skipped
                    + " Minecraft/Forge runtime-dependent test entry(s).");
        }
        printPureJvmCoverage();
        printGameTestOnlyCoverage();
    }

    private static void printPureJvmCoverage() {
        System.out.println("Zombies MVP3 ordinary JVM coverage tracked by this suite:");
        for (CoverageItem item : PURE_JVM_COVERAGE) {
            System.out.println("  - " + item.area() + ": " + item.coverage());
        }
    }

    private static void printGameTestOnlyCoverage() {
        System.err.println("Zombies MVP3 GameTest-only coverage entries not executed by the ordinary JVM suite:");
        for (GameTestOnlyCoverage entry : GAME_TEST_ONLY_COVERAGE) {
            System.err.println("  - " + entry.className() + "#" + entry.methodName()
                    + " (GameTest function: " + entry.gameTestFunctionName() + "): "
                    + entry.coverage());
        }
    }

    private record CoverageItem(String area, String coverage) {
    }

    private record GameTestOnlyCoverage(
            String className,
            String methodName,
            String gameTestFunctionName,
            String coverage
    ) {
    }
}
