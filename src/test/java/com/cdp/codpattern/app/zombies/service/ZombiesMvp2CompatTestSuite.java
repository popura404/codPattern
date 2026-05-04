package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.validation.ZombiesMapValidatorMvp2Mvp3CompatTest;

public final class ZombiesMvp2CompatTestSuite {
    private ZombiesMvp2CompatTestSuite() {
    }

    public static void main(String[] args) {
        ZombiesMapValidatorMvp2Mvp3CompatTest.main(args);
        ZombiesPurchaseStateServicesCompatTest.main(args);
        ZombiesObjectStateStoreMvp2CompatTest.main(args);
    }
}
