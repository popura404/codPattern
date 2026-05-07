package com.cdp.codpattern.app.zombies.service;

import java.util.List;

public final class ZombiesMvp2CompatTestSuite {
    private static final List<ZombiesCompatSuiteRunner.TestEntry> TESTS = List.of(
            new ZombiesCompatSuiteRunner.TestEntry("MVP2/MVP3 map validator compat",
                    "com.cdp.codpattern.app.zombies.validation.ZombiesMapValidatorMvp2Mvp3CompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 deploy object editor compat",
                    "com.cdp.codpattern.app.zombies.deploy.ZombiesDeployObjectEditorCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 deploy issue routing packet compat",
                    "com.cdp.codpattern.app.zombies.deploy.ZombiesDeployIssueRoutingCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 deploy issue target service compat",
                    "com.cdp.codpattern.app.zombies.deploy.ZombiesDeployToolServiceIssueTargetCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 purchase state services compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesPurchaseStateServicesCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 weapon inventory compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesWeaponInventoryServiceCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 object state store compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesObjectStateStoreMvp2CompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 barrier visual compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesBarrierVisualServiceCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 deep coverage compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesMvp2DeepCoverageCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP2 object interaction closure compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesMvp2ObjectInteractionClosureCompatTest",
                    true),
            new ZombiesCompatSuiteRunner.TestEntry("MVP1/MVP2 additional closure compat",
                    "com.cdp.codpattern.app.zombies.service.ZombiesMvp12AdditionalClosureCompatTest",
                    true)
    );

    private ZombiesMvp2CompatTestSuite() {
    }

    public static void main(String[] args) throws Throwable {
        int skipped = ZombiesCompatSuiteRunner.runAll(TESTS, args);
        if (skipped > 0) {
            System.err.println("ZombiesMvp2CompatTestSuite skipped " + skipped
                    + " Minecraft/Forge runtime-dependent test entry(s).");
        }
    }
}
