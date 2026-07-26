package com.cdp.codpattern.architecture;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.GameModeRuntimeProvider;
import com.cdp.codpattern.app.match.GameModeRuntimeRegistry;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchema;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchemaRegistry;
import com.cdp.codpattern.app.match.model.ClientModePresentation;
import com.cdp.codpattern.app.match.model.ClientModePresentationRegistry;
import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.match.model.JoinPolicy;
import com.cdp.codpattern.app.match.model.LifecycleKind;
import com.cdp.codpattern.app.match.model.ModeFamily;
import com.cdp.codpattern.app.match.model.ScoreboardKind;
import com.cdp.codpattern.app.match.model.TeamPolicy;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceProvider;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Characterizes the current process-global mode-registry conflict semantics. */
public final class ModeRegistryConflictBaselineCompatTest {
    private static final List<Class<?>> REGISTRIES = List.of(
            GameModeRegistry.class,
            GameModeRuntimeRegistry.class,
            ModeMapPersistenceRegistry.class,
            ModeMapEditorSchemaRegistry.class,
            ClientModePresentationRegistry.class);

    private ModeRegistryConflictBaselineCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        bootstrapRegistriesForProviderInterfaces();
        clearRegistriesForIsolatedCharacterization();
        try {
            characterizeDefinitionAndAliasConflicts();
            characterizeProviderReplacementAndCanonicalization();
            characterizeMissingPublicIsolationApi();
            System.out.println("PASS mode registry conflict baseline compat");
        } finally {
            clearRegistriesForIsolatedCharacterization();
        }
    }

    private static void bootstrapRegistriesForProviderInterfaces() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Field bootstrapFlag = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapFlag.setAccessible(true);
        bootstrapFlag.setBoolean(null, true);
        require(!BuiltInRegistries.REGISTRY.keySet().isEmpty(),
                "built-in registries must load before proxying provider interfaces");
    }

    private static void characterizeDefinitionAndAliasConflicts() {
        GameModeRegistry.registerDefinition(definition(" Alpha ", List.of("OldAlias", "SharedAlias"), "first"));
        GameModeRegistry.registerDefinition(definition("Beta", List.of("SharedAlias"), "beta"));

        require("alpha".equals(GameModeRegistry.canonicalize(" oldalias ")),
                "aliases must trim/lowercase and resolve to the original canonical mode");
        require("beta".equals(GameModeRegistry.canonicalize("SHAREDALIAS")),
                "later alias collision must silently redirect the alias to the later mode");
        require(GameModeRegistry.orderedDefinitions().stream().map(GameModeDefinition::gameType).toList()
                        .equals(List.of("alpha", "beta")),
                "first canonical insertion order must be retained");

        GameModeRegistry.registerDefinition(definition("ALPHA", List.of("NewAlias"), "replacement"));

        require("replacement".equals(GameModeRegistry.findDefinition("alpha").orElseThrow().displayNameKey()),
                "duplicate canonical registration must silently replace the definition");
        require(GameModeRegistry.orderedDefinitions().stream().map(GameModeDefinition::gameType).toList()
                        .equals(List.of("alpha", "beta")),
                "duplicate canonical replacement must preserve the original insertion position");
        require("alpha".equals(GameModeRegistry.canonicalize("OldAlias")),
                "aliases from the replaced definition currently remain registered");
        require("alpha".equals(GameModeRegistry.canonicalize("NewAlias")),
                "aliases from the replacement definition must be added");
        require("beta".equals(GameModeRegistry.canonicalize("SharedAlias")),
                "replacing alpha without the colliding alias must not reclaim the alias from beta");
    }

    private static void characterizeProviderReplacementAndCanonicalization() {
        GameModeRuntimeProvider runtimeOne = proxy(GameModeRuntimeProvider.class, "SharedAlias");
        GameModeRuntimeProvider runtimeTwo = proxy(GameModeRuntimeProvider.class, "beta");
        GameModeRuntimeRegistry.register(runtimeOne);
        GameModeRuntimeRegistry.register(runtimeTwo);
        require(GameModeRuntimeRegistry.find("sharedalias").orElseThrow() == runtimeTwo,
                "runtime providers must canonicalize aliases and silently replace duplicates");
        require(GameModeRuntimeRegistry.providers().equals(List.of(runtimeTwo)),
                "runtime provider replacement must preserve one canonical insertion slot");

        ModeMapPersistenceProvider persistenceOne = proxy(ModeMapPersistenceProvider.class, "SharedAlias");
        ModeMapPersistenceProvider persistenceTwo = proxy(ModeMapPersistenceProvider.class, "BETA");
        ModeMapPersistenceRegistry.register(persistenceOne);
        ModeMapPersistenceRegistry.register(persistenceTwo);
        require(ModeMapPersistenceRegistry.find("sharedalias").orElseThrow() == persistenceTwo,
                "persistence providers must canonicalize aliases and silently replace duplicates");
        require(ModeMapPersistenceRegistry.providers().equals(List.of(persistenceTwo)),
                "persistence replacement must preserve one canonical insertion slot");

        ModeMapEditorSchema schemaOne = emptySchema();
        ModeMapEditorSchema schemaTwo = emptySchema();
        ModeMapEditorSchemaRegistry.register("SharedAlias", schemaOne);
        ModeMapEditorSchemaRegistry.register("beta", schemaTwo);
        require(ModeMapEditorSchemaRegistry.find("sharedalias").orElseThrow() == schemaTwo,
                "editor schemas must canonicalize aliases and silently replace duplicates");

        ClientModePresentation presentationOne = new ClientModePresentation("first", 1, 1, 1, "first", "first");
        ClientModePresentation presentationTwo = new ClientModePresentation("second", 2, 2, 2, "second", "second");
        ClientModePresentationRegistry.register("SharedAlias", presentationOne);
        ClientModePresentationRegistry.register("beta", presentationTwo);
        require(ClientModePresentationRegistry.find("sharedalias").orElseThrow() == presentationTwo,
                "client presentations must canonicalize aliases and silently replace duplicates");
    }

    private static void characterizeMissingPublicIsolationApi() {
        for (Class<?> registry : REGISTRIES) {
            boolean hasPublicReset = Arrays.stream(registry.getDeclaredMethods())
                    .filter(method -> Modifier.isPublic(method.getModifiers()))
                    .map(Method::getName)
                    .anyMatch(name -> name.equals("clear") || name.equals("reset") || name.equals("clearAll"));
            require(!hasPublicReset, registry.getSimpleName() + " must remain characterized as lacking a public reset API");
        }
    }

    private static GameModeDefinition definition(String gameType, List<String> aliases, String displayKey) {
        return new GameModeDefinition(
                gameType,
                aliases,
                displayKey,
                "room." + displayKey,
                "command." + displayKey,
                List.of(),
                ModeFamily.CUSTOM,
                TeamPolicy.NONE,
                JoinPolicy.MODE_DEFINED,
                LifecycleKind.MODE_DEFINED,
                ScoreboardKind.MODE_DEFINED,
                Set.of());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, String gameType) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (instance, method, args) -> {
                    if (method.getName().equals("gameType")) {
                        return gameType;
                    }
                    if (method.getName().equals("toString")) {
                        return type.getSimpleName() + "[" + gameType + "]";
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    if (method.getReturnType().equals(int.class)) {
                        return 0;
                    }
                    return null;
                });
    }

    private static ModeMapEditorSchema emptySchema() {
        return new ModeMapEditorSchema() {
            @Override
            public List<com.cdp.codpattern.app.match.editor.PointLayerDefinition> pointLayers() {
                return List.of();
            }

            @Override
            public List<com.cdp.codpattern.app.match.editor.AreaLayerDefinition> areaLayers() {
                return List.of();
            }

            @Override
            public List<com.cdp.codpattern.app.match.editor.ObjectFeatureDefinition> objectFeatures() {
                return List.of();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static void clearRegistriesForIsolatedCharacterization() throws ReflectiveOperationException {
        for (Class<?> registry : REGISTRIES) {
            for (Field field : registry.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                ((Map<Object, Object>) field.get(null)).clear();
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
