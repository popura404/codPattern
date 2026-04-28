package com.cdp.codpattern.app.match.model;

import com.cdp.codpattern.app.match.GameModeRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ClientModePresentationRegistry {
    private static final Map<String, ClientModePresentation> PRESENTATIONS = new LinkedHashMap<>();

    private ClientModePresentationRegistry() {
    }

    public static void register(String gameType, ClientModePresentation presentation) {
        if (presentation == null) {
            return;
        }
        String canonicalGameType = GameModeRegistry.canonicalize(gameType);
        if (canonicalGameType.isBlank()) {
            return;
        }
        PRESENTATIONS.put(canonicalGameType, presentation);
    }

    public static Optional<ClientModePresentation> find(String gameType) {
        String canonicalGameType = GameModeRegistry.canonicalize(gameType);
        if (canonicalGameType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(PRESENTATIONS.get(canonicalGameType));
    }
}
