package com.cdp.codpattern.architecture.phase7;

import com.cdp.codpattern.app.match.ModeModules;
import com.cdp.codpattern.app.tdm.TdmModeModule;

/** Test-only logical bootstrap; it is deliberately excluded from the shipping main source tree. */
public final class Phase7MainOnlyVerificationBootstrap {
    private Phase7MainOnlyVerificationBootstrap() {
    }

    public static void install() {
        ModeModules.contribute(TdmModeModule.INSTANCE);
        ModeModules.freeze();
    }
}
