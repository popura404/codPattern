package com.cdp.codpattern.app.match.persistence;

import com.cdp.codpattern.app.match.GameModeRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ModeMapPersistenceRegistry {
    private static final Map<String, ModeMapPersistenceProvider> PROVIDERS = new LinkedHashMap<>();

    private ModeMapPersistenceRegistry() {
    }

    public static void register(ModeMapPersistenceProvider provider) {
        if (provider == null) {
            return;
        }
        String gameType = GameModeRegistry.canonicalize(provider.gameType());
        if (gameType.isBlank()) {
            return;
        }
        PROVIDERS.put(gameType, provider);
    }

    public static Optional<ModeMapPersistenceProvider> find(String gameType) {
        String canonicalGameType = GameModeRegistry.canonicalize(gameType);
        if (canonicalGameType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(PROVIDERS.get(canonicalGameType));
    }

    public static List<ModeMapPersistenceProvider> providers() {
        return List.copyOf(PROVIDERS.values());
    }
}
