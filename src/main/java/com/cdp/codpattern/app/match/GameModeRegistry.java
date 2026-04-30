package com.cdp.codpattern.app.match;

import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.app.match.model.ModeFamily;
import com.cdp.codpattern.app.match.model.ScoreboardKind;
import com.cdp.codpattern.app.match.model.JoinPolicy;
import com.cdp.codpattern.app.match.model.LifecycleKind;
import com.cdp.codpattern.app.match.model.TeamPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;

public final class GameModeRegistry {
    private static final Map<String, GameModeDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    private GameModeRegistry() {
    }

    public static void register(ModeDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        registerDefinition(new GameModeDefinition(
                descriptor.gameType(),
                List.of(),
                descriptor.displayNameKey(),
                descriptor.roomHeaderKey(),
                descriptor.createCommand(),
                descriptor.teams(),
                ModeFamily.CUSTOM,
                TeamPolicy.NONE,
                JoinPolicy.MODE_DEFINED,
                LifecycleKind.MODE_DEFINED,
                ScoreboardKind.MODE_DEFINED,
                Set.of()
        ));
    }

    public static void registerDefinition(GameModeDefinition definition) {
        if (definition == null) {
            return;
        }
        String canonical = normalize(definition.gameType());
        GameModeDefinition normalizedDefinition = new GameModeDefinition(
                canonical,
                definition.aliases(),
                definition.displayNameKey(),
                definition.roomHeaderKey(),
                definition.createCommand(),
                definition.teams(),
                definition.family(),
                definition.teamPolicy(),
                definition.joinPolicy(),
                definition.lifecycleKind(),
                definition.scoreboardKind(),
                definition.capabilities(),
                definition.runtimeProvider(),
                definition.persistenceProvider(),
                definition.editorSchema(),
                definition.clientPresentation()
        );
        DEFINITIONS.put(canonical, normalizedDefinition);
        ALIASES.put(canonical, canonical);
        for (String alias : normalizedDefinition.aliases()) {
            String normalizedAlias = normalize(alias);
            if (!normalizedAlias.isBlank()) {
                ALIASES.put(normalizedAlias, canonical);
            }
        }
    }

    public static Optional<ModeDescriptor> find(String gameType) {
        return findDefinition(gameType).map(GameModeDefinition::descriptor);
    }

    public static Optional<GameModeDefinition> findDefinition(String gameType) {
        String canonical = canonicalize(gameType);
        if (canonical.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(DEFINITIONS.get(canonical));
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
        return DEFINITIONS.values().stream()
                .map(GameModeDefinition::descriptor)
                .toList();
    }

    public static List<GameModeDefinition> orderedDefinitions() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static String canonicalize(String gameType) {
        String normalized = normalize(gameType);
        if (normalized.isBlank()) {
            return normalized;
        }
        return ALIASES.getOrDefault(normalized, normalized);
    }

    public static Set<ModeCapability> capabilities(String gameType) {
        return findDefinition(gameType)
                .map(GameModeDefinition::capabilities)
                .orElseGet(Set::of);
    }

    public static boolean hasCapability(String gameType, ModeCapability capability) {
        return capability != null && capabilities(gameType).contains(capability);
    }

    private static String normalize(String gameType) {
        return gameType == null ? "" : gameType.trim().toLowerCase(Locale.ROOT);
    }
}
