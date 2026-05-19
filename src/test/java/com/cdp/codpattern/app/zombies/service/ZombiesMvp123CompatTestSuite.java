package com.cdp.codpattern.app.zombies.service;

import java.util.List;

public final class ZombiesMvp123CompatTestSuite {
    private static final List<ZombiesCompatSuiteRunner.TestEntry> TESTS = List.of(
            new ZombiesCompatSuiteRunner.TestEntry("MVP1 compat suite",
                    "com.cdp.codpattern.app.zombies.service.ZombiesMvp1CompatTestSuite",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("MVP1-MVP3 pure automation smoke",
                    "com.cdp.codpattern.app.zombies.service.ZombiesMvp123AutomationCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("Zombies starter kit compensation compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesStarterKitDistributorCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2/MVP3 map validator compat",
                    "com.cdp.codpattern.app.zombies.validation.ZombiesMapValidatorMvp2Mvp3CompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 deploy object editor compat",
                    "com.cdp.codpattern.app.zombies.deploy.ZombiesDeployObjectEditorCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("Zombies deploy object editor static contract compat",
                    "com.cdp.codpattern.app.zombies.deploy.ZombiesDeployObjectEditorStaticContractCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 deploy issue routing packet compat",
                    "com.cdp.codpattern.app.zombies.deploy.ZombiesDeployIssueRoutingCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 deploy issue target service compat",
                    "com.cdp.codpattern.app.zombies.deploy.ZombiesDeployToolServiceIssueTargetCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("Zombies deploy GUI static contract compat",
                    "com.cdp.codpattern.app.zombies.deploy.ZombiesDeployGuiStaticContractCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("Zombies HUD overlay static contract compat",
                    "com.cdp.codpattern.client.gui.overlay.ZombiesHudOverlayStaticContractCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("Zombies box block entry static contract compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesBoxBlockEntryStaticContractCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("Zombies client survivor status compat",
                    "com.cdp.codpattern.client.zombies.ClientZombiesStateGrowthCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("Zombies survivor runtime state sync compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesSurvivorRuntimeStateSyncCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("Zombies deploy plan static contract compat",
                    "com.cdp.codpattern.app.zombies.deploy.ZombiesDeployPlanStaticContractCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 purchase state services compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesPurchaseStateServicesCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 weapon inventory compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesWeaponInventoryServiceCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 object state store compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesObjectStateStoreMvp2CompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 player barrier block runtime compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesBarrierBlockRuntimeServiceCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 deep coverage compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesMvp2DeepCoverageCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 object interaction closure compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesMvp2ObjectInteractionClosureCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP1/MVP2 additional closure compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesMvp12AdditionalClosureCompatTest",
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
                    "MVP1",
                    "ready/vote/preflight failure",
                    "unanimous vote reaches preflight; invalid MVP1 map returns room to WAITING without player/wave runtime side effects"),
            new CoverageItem(
                    "MVP1",
                    "ready/vote/preflight/phase/reset",
                    "valid vote opens the round, advances countdown/intermission/wave/victory/ending, then clears ready/player runtime on reset"),
            new CoverageItem(
                    "MVP1",
                    "starter kit compensation",
                    "starter kit validation fails before mutation; runtime exceptions restore already-cleared or already-equipped targets"),
            new CoverageItem(
                    "MVP1",
                    "lifecycle closure cleanup",
                    "victory, all-dead failure, and same-tick failure-priority paths run cleanup and leave no JVM runtime residue"),
            new CoverageItem(
                    "MVP1",
                    "deep cleanup side effects",
                    "preflight failure leaves occupancy/HUD/object state untouched; victory/failure cleanup clears client state and stays idempotent"),
            new CoverageItem(
                    "MVP2",
                    "purchase failure and active spawn group",
                    "duplicate weapon purchase and insufficient unlock do not spend points; successful unlock activates group 2 atomically"),
            new CoverageItem(
                    "MVP2",
                    "deep object/deploy coverage",
                    "barrier group clear isolation, deploy save rollback, and weapon-wall LIST edit round trips preserve object consistency"),
            new CoverageItem(
                    "MVP2",
                    "deploy issue routing packet compat",
                    "structured issue target snapshot payload and JUMP_TO_ISSUE_TARGET packet round trip stay backward compatible with legacy jump flow"),
            new CoverageItem(
                    "MVP2",
                    "deploy issue target service fallback compat",
                    "invalid structured target step/type falls back to legacy resolver and structured index clamps to existing object bounds"),
            new CoverageItem(
                    "MVP2",
                    "object interaction closure",
                    "stale object packets, room-scoped object state, barrier concurrency, wall/ammo/armor failures, and idempotent revisions"),
            new CoverageItem(
                    "MVP2",
                    "box block entry contract",
                    "weapon wall, ammo box, armor station, soda machine, and ultimate machine block use routes through the existing service while RightClickBlock skips double dispatch"),
            new CoverageItem(
                    "MVP2",
                    "full-runtime cleanup/object reset compat",
                    "additional closure compat covers participant failure ordering, idempotent cleanup, and object-store reset under a Minecraft classpath"),
            new CoverageItem(
                    "MVP3",
                    "failure priority and pending endtp",
                    "FAILED wins over completed-wave victory and offline members receive consumable pending end teleport cleanup work"),
            new CoverageItem(
                    "MVP3",
                    "intermission respawn prep",
                    "online dead teammate is selected at intermission; prep clears transient buffs and halves double-ammo reserve"),
            new CoverageItem(
                    "MVP3",
                    "runtime closure",
                    "requiresPower failures, repeat power, score multiplier, damage reduction, reactive explosion scoping, respawn ordering, and pending endtp cleanup"),
            new CoverageItem(
                    "MVP3",
                    "deep growth cleanup coverage",
                    "requiresPower buff/ultimate failures, repeat purchases, double-ammo idempotent trimming, invalid ultimate, and pending endtp rewrite/consume"),
            new CoverageItem(
                    "MVP3",
                    "additional growth closure",
                    "non-requiresPower growth, double-ammo reserve trimming, and invalid ultimate level failures")
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
                    "weaponInventoryTagsPrimaryStack",
                    "zombiesruntimegametests.weaponinventorytagsprimarystack",
                    "Forge ItemStack/NBT path for purchased zombies primary weapons"),
            new GameTestOnlyCoverage(
                    "com.cdp.codpattern.app.zombies.gametest.ZombiesRuntimeGameTests",
                    "weaponInventoryRejectsWrongSlotSync",
                    "zombiesruntimegametests.weaponinventoryrejectswrongslotsync",
                    "Forge ItemStack/NBT failure path for wrong-slot reserve ammo sync"),
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
                    "barrierMovementFallbackPushesOutsideActiveArea",
                    "zombiesruntimegametests.barriermovementfallbackpushesoutsideactivearea",
                    "barrier movement fallback target calculation"),
            new GameTestOnlyCoverage(
                    "com.cdp.codpattern.app.zombies.gametest.ZombiesRuntimeGameTests",
                    "objectInteractionPowerSwitchPowersPlacedBlockAndDeductsPoints",
                    "zombiesruntimegametests.objectinteractionpowerswitchpowersplacedblockanddeductspoints",
                    "Forge ServerPlayer object interaction path for power switch cost, object revision, and powered block sync"),
            new GameTestOnlyCoverage(
                    "com.cdp.codpattern.app.zombies.gametest.ZombiesRuntimeGameTests",
                    "objectInteractionAmmoBoxSyncsTaggedPrimaryStackInPlayerInventory",
                    "zombiesruntimegametests.objectinteractionammoboxsyncstaggedprimarystackinplayerinventory",
                    "Forge ServerPlayer inventory path for ammo box refill, NBT reserve sync, cost, and object revision"),
            new GameTestOnlyCoverage(
                    "com.cdp.codpattern.app.zombies.gametest.ZombiesRuntimeGameTests",
                    "barrierMovementEnforceTeleportsRealPlayerToLastLegalPosition",
                    "zombiesruntimegametests.barriermovementenforcesteleportsrealplayertolastlegalposition",
                    "Forge ServerPlayer teleport restore path for barrier movement enforcement")
    );

    private ZombiesMvp123CompatTestSuite() {
    }

    public static void main(String[] args) throws Throwable {
        int skipped = ZombiesCompatSuiteRunner.runAll(TESTS, args);
        if (skipped > 0) {
            System.err.println("ZombiesMvp123CompatTestSuite skipped " + skipped
                    + " Minecraft/Forge runtime-dependent test entry(s).");
        }
        printPureJvmCoverage();
        printGameTestOnlyCoverage();
    }

    private static void printPureJvmCoverage() {
        System.out.println("Zombies staged compat coverage tracked by this suite:");
        for (CoverageItem item : PURE_JVM_COVERAGE) {
            System.out.println("  - " + item.stage() + " " + item.area() + ": " + item.coverage());
        }
    }

    private static void printGameTestOnlyCoverage() {
        System.err.println("Zombies GameTest-only coverage entries not executed by the ordinary JVM suite:");
        for (GameTestOnlyCoverage entry : GAME_TEST_ONLY_COVERAGE) {
            System.err.println("  - " + entry.className() + "#" + entry.methodName()
                    + " (GameTest function: " + entry.gameTestFunctionName() + "): "
                    + entry.coverage());
        }
    }

    private record CoverageItem(String stage, String area, String coverage) {
    }

    private record GameTestOnlyCoverage(
            String className,
            String methodName,
            String gameTestFunctionName,
            String coverage
    ) {
    }

}
