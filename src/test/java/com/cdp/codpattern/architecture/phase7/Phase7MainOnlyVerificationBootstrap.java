package com.cdp.codpattern.architecture.phase7;

import com.cdp.codpattern.app.match.GameModeBootstrap;
import com.cdp.codpattern.app.tdm.model.TdmGameModeDefinitions;

/** Test-only logical bootstrap; it is deliberately excluded from the shipping main source tree. */
public final class Phase7MainOnlyVerificationBootstrap {
    private Phase7MainOnlyVerificationBootstrap() {
    }

    public static void install() {
        TdmGameModeDefinitions.registerDefaults();
        GameModeBootstrap.registerCommonProviders();
    }
}
