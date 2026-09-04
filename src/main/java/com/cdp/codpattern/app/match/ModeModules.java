package com.cdp.codpattern.app.match;

import com.cdp.codpattern.app.match.extension.ModeModule;

/** Global public lifecycle entry point for installed mode modules. */
public final class ModeModules {
    private static final ModeModuleCollector COLLECTOR = new ModeModuleCollector();

    private ModeModules() {
    }

    public static void contribute(ModeModule module) {
        COLLECTOR.contribute(module);
    }

    public static ModeCatalog freeze() {
        return COLLECTOR.freeze();
    }

    public static ModeCatalog catalog() {
        return COLLECTOR.catalog();
    }
}
