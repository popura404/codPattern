package com.cdp.codpattern.verification.phase7;

import com.cdp.codpattern.app.match.GameModeBootstrap;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.tdm.model.TdmGameModeDefinitions;

import java.util.List;

/** Test-only logical bootstrap for the future-main definition/runtime lane. */
public final class MainOnlyVerificationBootstrap {
    private MainOnlyVerificationBootstrap() {
    }

    public static List<String> install() {
        TdmGameModeDefinitions.registerDefaults();
        GameModeBootstrap.registerCommonProviders();
        return GameModeRegistry.orderedDefinitions().stream()
                .map(definition -> definition.gameType())
                .toList();
    }
}
