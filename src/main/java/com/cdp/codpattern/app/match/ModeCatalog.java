package com.cdp.codpattern.app.match;

import com.cdp.codpattern.app.match.editor.ModeMapEditorSchema;
import com.cdp.codpattern.app.match.extension.ModeAreaProtectionContributor;
import com.cdp.codpattern.app.match.extension.ModeDebugSnapshotContributor;
import com.cdp.codpattern.app.match.extension.ModeEntityReconciliationContributor;
import com.cdp.codpattern.app.match.extension.ModeHeldToolPreviewContributor;
import com.cdp.codpattern.app.match.extension.ModeModule;
import com.cdp.codpattern.app.match.extension.ModeObjectInteractionBypassContributor;
import com.cdp.codpattern.app.match.extension.ModePlayerLoginContributor;
import com.cdp.codpattern.app.match.model.ClientModePresentation;
import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Immutable, validated view of every installed mode module. */
public final class ModeCatalog {
    private static final Comparator<ModeModule> MODULE_ORDER = Comparator
            .comparingInt(ModeModule::order)
            .thenComparing(module -> module.id().toString());

    private final List<ModeModule> modules;
    private final List<GameModeDefinition> definitions;
    private final Map<String, GameModeDefinition> definitionsByType;
    private final Map<String, String> aliases;
    private final List<GameModeRuntimeProvider> runtimeProviders;
    private final List<ModeMapPersistenceProvider> persistenceProviders;
    private final List<ModePlayerLoginContributor> playerLoginContributors;
    private final List<ModeEntityReconciliationContributor> entityReconciliationContributors;
    private final List<ModeObjectInteractionBypassContributor> objectInteractionBypassContributors;
    private final List<ModeAreaProtectionContributor> areaProtectionContributors;
    private final List<ModeDebugSnapshotContributor> debugSnapshotContributors;
    private final List<ModeHeldToolPreviewContributor> heldToolPreviewContributors;

    private ModeCatalog(
            List<ModeModule> modules,
            List<GameModeDefinition> definitions,
            Map<String, GameModeDefinition> definitionsByType,
            Map<String, String> aliases,
            List<GameModeRuntimeProvider> runtimeProviders,
            List<ModeMapPersistenceProvider> persistenceProviders,
            List<ModePlayerLoginContributor> playerLoginContributors,
            List<ModeEntityReconciliationContributor> entityReconciliationContributors,
            List<ModeObjectInteractionBypassContributor> objectInteractionBypassContributors,
            List<ModeAreaProtectionContributor> areaProtectionContributors,
            List<ModeDebugSnapshotContributor> debugSnapshotContributors,
            List<ModeHeldToolPreviewContributor> heldToolPreviewContributors
    ) {
        this.modules = modules;
        this.definitions = definitions;
        this.definitionsByType = definitionsByType;
        this.aliases = aliases;
        this.runtimeProviders = runtimeProviders;
        this.persistenceProviders = persistenceProviders;
        this.playerLoginContributors = playerLoginContributors;
        this.entityReconciliationContributors = entityReconciliationContributors;
        this.objectInteractionBypassContributors = objectInteractionBypassContributors;
        this.areaProtectionContributors = areaProtectionContributors;
        this.debugSnapshotContributors = debugSnapshotContributors;
        this.heldToolPreviewContributors = heldToolPreviewContributors;
    }

    public static ModeCatalog create(Iterable<? extends ModeModule> contributedModules) {
        Objects.requireNonNull(contributedModules, "contributedModules");
        List<ModeModule> modules = new ArrayList<>();
        for (ModeModule module : contributedModules) {
            ModeModule value = Objects.requireNonNull(module, "mode module");
            Objects.requireNonNull(value.id(), "mode module id");
            modules.add(value);
        }
        modules.sort(MODULE_ORDER);

        Set<ResourceLocation> moduleIds = new LinkedHashSet<>();
        for (ModeModule module : modules) {
            ResourceLocation moduleId = Objects.requireNonNull(module.id(), "mode module id");
            if (!moduleIds.add(moduleId)) {
                throw new IllegalStateException("Duplicate mode module id: " + moduleId);
            }
        }

        List<GameModeDefinition> definitions = new ArrayList<>();
        Map<String, GameModeDefinition> byType = new LinkedHashMap<>();
        for (ModeModule module : modules) {
            List<GameModeDefinition> declared = requireList(module.definitions(), module.id(), "definitions");
            for (GameModeDefinition definition : declared) {
                GameModeDefinition normalized = normalizeDefinition(
                        Objects.requireNonNull(definition, "definition in " + module.id()));
                GameModeDefinition previous = byType.putIfAbsent(normalized.gameType(), normalized);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate game mode: " + normalized.gameType());
                }
                definitions.add(normalized);
            }
        }

        Map<String, String> aliases = new LinkedHashMap<>();
        byType.keySet().forEach(gameType -> aliases.put(gameType, gameType));
        for (GameModeDefinition definition : definitions) {
            for (String rawAlias : definition.aliases()) {
                String alias = normalize(rawAlias);
                if (alias.isBlank()) {
                    throw new IllegalStateException("Blank alias for game mode: " + definition.gameType());
                }
                if (byType.containsKey(alias)) {
                    throw new IllegalStateException("Game mode alias occupies a mode name: " + alias);
                }
                String previous = aliases.putIfAbsent(alias, definition.gameType());
                if (previous != null) {
                    throw new IllegalStateException("Duplicate game mode alias: " + alias);
                }
            }
        }

        List<GameModeRuntimeProvider> runtimeProviders = new ArrayList<>();
        List<ModeMapPersistenceProvider> persistenceProviders = new ArrayList<>();
        for (GameModeDefinition definition : definitions) {
            com.cdp.codpattern.app.match.editor.ModeEndTeleportSupport.requireDefinition(definition);
            definition.runtimeProvider().ifPresent(provider -> {
                requireProviderMode("runtime", definition.gameType(), provider.gameType());
                runtimeProviders.add(provider);
            });
            definition.persistenceProvider().ifPresent(provider -> {
                requireProviderMode("persistence", definition.gameType(), provider.gameType());
                persistenceProviders.add(provider);
            });
        }

        return new ModeCatalog(
                List.copyOf(modules),
                List.copyOf(definitions),
                Map.copyOf(byType),
                Map.copyOf(aliases),
                List.copyOf(runtimeProviders),
                List.copyOf(persistenceProviders),
                collectExtensions(modules, ModeModule::playerLoginContributors,
                        ModePlayerLoginContributor::id, ModePlayerLoginContributor::order, "player-login"),
                collectExtensions(modules, ModeModule::entityReconciliationContributors,
                        ModeEntityReconciliationContributor::id, ModeEntityReconciliationContributor::order,
                        "entity-reconciliation"),
                collectExtensions(modules, ModeModule::objectInteractionBypassContributors,
                        ModeObjectInteractionBypassContributor::id, ModeObjectInteractionBypassContributor::order,
                        "object-interaction-bypass"),
                collectExtensions(modules, ModeModule::areaProtectionContributors,
                        ModeAreaProtectionContributor::id, ModeAreaProtectionContributor::order, "area-protection"),
                collectExtensions(modules, ModeModule::debugSnapshotContributors,
                        ModeDebugSnapshotContributor::id, ModeDebugSnapshotContributor::order, "debug-snapshot"),
                collectExtensions(modules, ModeModule::heldToolPreviewContributors,
                        ModeHeldToolPreviewContributor::id, ModeHeldToolPreviewContributor::order,
                        "held-tool-preview"));
    }

    public List<ModeModule> modules() {
        return modules;
    }

    public List<GameModeDefinition> definitions() {
        return definitions;
    }

    public Optional<GameModeDefinition> findDefinition(String gameType) {
        return Optional.ofNullable(definitionsByType.get(canonicalize(gameType)));
    }

    public String canonicalize(String gameType) {
        String normalized = normalize(gameType);
        return aliases.getOrDefault(normalized, normalized);
    }

    public List<GameModeRuntimeProvider> runtimeProviders() {
        return runtimeProviders;
    }

    public List<ModeMapPersistenceProvider> persistenceProviders() {
        return persistenceProviders;
    }

    public List<ModePlayerLoginContributor> playerLoginContributors() {
        return playerLoginContributors;
    }

    public List<ModeEntityReconciliationContributor> entityReconciliationContributors() {
        return entityReconciliationContributors;
    }

    public List<ModeObjectInteractionBypassContributor> objectInteractionBypassContributors() {
        return objectInteractionBypassContributors;
    }

    public List<ModeAreaProtectionContributor> areaProtectionContributors() {
        return areaProtectionContributors;
    }

    public List<ModeDebugSnapshotContributor> debugSnapshotContributors() {
        return debugSnapshotContributors;
    }

    public List<ModeHeldToolPreviewContributor> heldToolPreviewContributors() {
        return heldToolPreviewContributors;
    }

    public Optional<ModeMapEditorSchema> editorSchema(String gameType) {
        return findDefinition(gameType).flatMap(GameModeDefinition::editorSchema);
    }

    public Optional<ClientModePresentation> clientPresentation(String gameType) {
        return findDefinition(gameType).flatMap(GameModeDefinition::clientPresentation);
    }

    private static GameModeDefinition normalizeDefinition(GameModeDefinition definition) {
        String gameType = normalize(definition.gameType());
        if (gameType.isBlank()) {
            throw new IllegalStateException("Game mode id must not be blank");
        }
        return new GameModeDefinition(
                gameType,
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
                definition.clientPresentation());
    }

    private static void requireProviderMode(String kind, String definitionMode, String providerMode) {
        String normalizedProviderMode = normalize(providerMode);
        if (!definitionMode.equals(normalizedProviderMode)) {
            throw new IllegalStateException(kind + " provider for " + definitionMode
                    + " declares mode " + normalizedProviderMode);
        }
    }

    private static <T> List<T> collectExtensions(
            List<ModeModule> modules,
            Function<ModeModule, List<T>> extractor,
            Function<T, String> idExtractor,
            java.util.function.ToIntFunction<T> orderExtractor,
            String kind
    ) {
        Map<String, T> byId = new LinkedHashMap<>();
        for (ModeModule module : modules) {
            List<T> declared = requireList(extractor.apply(module), module.id(), kind + " contributors");
            for (T extension : declared) {
                T value = Objects.requireNonNull(extension, kind + " contributor in " + module.id());
                String id = Objects.requireNonNullElse(idExtractor.apply(value), "").trim();
                if (id.isEmpty()) {
                    throw new IllegalStateException(kind + " contributor id must not be blank");
                }
                if (byId.putIfAbsent(id, value) != null) {
                    throw new IllegalStateException("Duplicate " + kind + " contributor id: " + id);
                }
            }
        }
        List<T> ordered = new ArrayList<>(byId.values());
        ordered.sort(Comparator.comparingInt(orderExtractor).thenComparing(idExtractor));
        return List.copyOf(ordered);
    }

    private static <T> List<T> requireList(List<T> values, ResourceLocation moduleId, String label) {
        if (values == null) {
            throw new IllegalStateException("Mode module " + moduleId + " returned null " + label);
        }
        return values;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
