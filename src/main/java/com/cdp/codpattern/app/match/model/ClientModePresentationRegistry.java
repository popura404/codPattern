package com.cdp.codpattern.app.match.model;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.ModeModules;

import java.util.Optional;

/** Read-only client-presentation facade over the frozen mode catalog. */
public final class ClientModePresentationRegistry {
    private ClientModePresentationRegistry() {
    }

    public static Optional<ClientModePresentation> find(String gameType) {
        String canonicalGameType = GameModeRegistry.canonicalize(gameType);
        if (canonicalGameType.isBlank()) {
            return Optional.empty();
        }
        return ModeModules.catalog().clientPresentation(canonicalGameType);
    }
}
