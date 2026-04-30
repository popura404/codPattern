package com.cdp.codpattern.app.match;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GameModeRuntimeRegistry {
    private static final Map<String, GameModeRuntimeProvider> PROVIDERS = new LinkedHashMap<>();

    private GameModeRuntimeRegistry() {
    }

    public static void register(GameModeRuntimeProvider provider) {
        if (provider == null) {
            return;
        }
        String canonicalGameType = GameModeRegistry.canonicalize(provider.gameType());
        if (canonicalGameType.isBlank()) {
            return;
        }
        PROVIDERS.put(canonicalGameType, provider);
    }

    public static Optional<GameModeRuntimeProvider> find(String gameType) {
        String canonicalGameType = GameModeRegistry.canonicalize(gameType);
        if (canonicalGameType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(PROVIDERS.get(canonicalGameType));
    }

    public static List<GameModeRuntimeProvider> providers() {
        return List.copyOf(PROVIDERS.values());
    }
}
