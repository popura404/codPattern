package com.cdp.codpattern.app.zombies.service;

public final class ZombiesMvp1CompatTestSuite {
    private ZombiesMvp1CompatTestSuite() {
    }

    public static void main(String[] args) throws Exception {
        ZombiesWaveValidatorCompatTest.main(args);
        ZombiesWaveConfigRepositoryCompatTest.main(args);
        ZombiesReadyVoteServiceCompatTest.main(args);
        ZombiesEconomyConnectionServiceCompatTest.main(args);
        ZombiesSpawnAssignmentServiceCompatTest.main(args);
        ZombiesStartupValidationServiceCompatTest.main(args);
    }
}
