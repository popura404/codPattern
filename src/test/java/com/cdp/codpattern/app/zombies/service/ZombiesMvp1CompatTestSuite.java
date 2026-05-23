package com.cdp.codpattern.app.zombies.service;

import java.util.List;

public final class ZombiesMvp1CompatTestSuite {
    private static final List<ZombiesCompatSuiteRunner.TestEntry> TESTS = List.of(
            new ZombiesCompatSuiteRunner.TestEntry("MVP1 wave validator compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesWaveValidatorCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("MVP1 wave config repository compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesWaveConfigRepositoryCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("MVP1 wave runtime static contract compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesWaveRuntimeStaticContractCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("MVP1 spawn point distance weighting compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesSpawnPointDistanceWeightingCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("MVP1 active mob counter compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesActiveMobCounterCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("MVP1 ready/vote compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesReadyVoteServiceCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("MVP1 economy/connection compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesEconomyConnectionServiceCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("MVP1 spawn assignment compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesSpawnAssignmentServiceCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("MVP1 startup validation compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesStartupValidationServiceCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("MVP1 deep coverage compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesMvp1DeepCoverageCompatTest",
                    false),
            new ZombiesCompatSuiteRunner.TestEntry("MVP1 lifecycle closure compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesMvp1LifecycleClosureCompatTest",
                    true)
    );

    private ZombiesMvp1CompatTestSuite() {
    }

    public static void main(String[] args) throws Throwable {
        int skipped = ZombiesCompatSuiteRunner.runAll(TESTS, args);
        if (skipped > 0) {
            System.err.println("ZombiesMvp1CompatTestSuite skipped " + skipped
                    + " Minecraft/Forge runtime-dependent test entry(s).");
        }
    }
}
