package com.cdp.codpattern.app.match;

import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.ModeDescriptor;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Read-only compatibility facade over the frozen mode catalog. */
public final class GameModeRegistry {
    private GameModeRegistry() {
    }

    public static Optional<ModeDescriptor> find(String gameType) {
        return findDefinition(gameType).map(GameModeDefinition::descriptor);
    }

    public static Optional<GameModeDefinition> findDefinition(String gameType) {
        return ModeModules.catalog().findDefinition(gameType);
    }

    public static ModeDescriptor getOrDefault(String gameType) {
        return find(gameType).orElseGet(() -> new ModeDescriptor(
                gameType,
                "mode.codpattern.unknown",
                "screen.codpattern.tdm_room.header",
                "",
                List.of()
        ));
    }

    public static List<ModeDescriptor> orderedModes() {
        return ModeModules.catalog().definitions().stream()
                .map(GameModeDefinition::descriptor)
                .toList();
    }

    public static List<GameModeDefinition> orderedDefinitions() {
        return ModeModules.catalog().definitions();
    }

    public static String canonicalize(String gameType) {
        return ModeModules.catalog().canonicalize(gameType);
    }

    public static Set<ModeCapability> capabilities(String gameType) {
        return findDefinition(gameType)
                .map(GameModeDefinition::capabilities)
                .orElseGet(Set::of);
    }

    public static boolean hasCapability(String gameType, ModeCapability capability) {
        return capability != null && capabilities(gameType).contains(capability);
    }
}
