package com.cdp.codpattern.app.match.extension;

import com.cdp.codpattern.app.match.GameModeRegistry;

import java.util.Objects;

/** Public registration entry point shared by built-in modes and future addon entry points. */
public final class ModeDefinitionContributions {
    private ModeDefinitionContributions() {
    }

    public static void register(ModeDefinitionContributor contributor) {
        Objects.requireNonNull(contributor, "contributor")
                .contribute(GameModeRegistry::registerDefinition);
    }
}
