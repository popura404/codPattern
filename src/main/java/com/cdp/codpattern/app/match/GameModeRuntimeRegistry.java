package com.cdp.codpattern.app.match;

import java.util.List;
import java.util.Optional;

/** Read-only runtime-provider facade over the frozen mode catalog. */
public final class GameModeRuntimeRegistry {
    private GameModeRuntimeRegistry() {
    }

    public static Optional<GameModeRuntimeProvider> find(String gameType) {
        String canonicalGameType = GameModeRegistry.canonicalize(gameType);
        if (canonicalGameType.isBlank()) {
            return Optional.empty();
        }
        return ModeModules.catalog().runtimeProviders().stream()
                .filter(provider -> GameModeRegistry.canonicalize(provider.gameType()).equals(canonicalGameType))
                .findFirst();
    }

    public static List<GameModeRuntimeProvider> providers() {
        return ModeModules.catalog().runtimeProviders();
    }
}
