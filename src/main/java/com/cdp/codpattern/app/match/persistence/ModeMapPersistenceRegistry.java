package com.cdp.codpattern.app.match.persistence;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.ModeModules;

import java.util.List;
import java.util.Optional;

/** Read-only persistence-provider facade over the frozen mode catalog. */
public final class ModeMapPersistenceRegistry {
    private ModeMapPersistenceRegistry() {
    }

    public static Optional<ModeMapPersistenceProvider> find(String gameType) {
        String canonicalGameType = GameModeRegistry.canonicalize(gameType);
        if (canonicalGameType.isBlank()) {
            return Optional.empty();
        }
        return ModeModules.catalog().persistenceProviders().stream()
                .filter(provider -> GameModeRegistry.canonicalize(provider.gameType()).equals(canonicalGameType))
                .findFirst();
    }

    public static List<ModeMapPersistenceProvider> providers() {
        return ModeModules.catalog().persistenceProviders();
    }
}
