package com.cdp.codpattern.architecture.phase7;

import com.cdp.codpattern.CodPatternConstants;
import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.GameModeRuntimeRegistry;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchemaRegistry;
import com.cdp.codpattern.app.match.model.ClientModePresentationRegistry;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceRegistry;

import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class MainOnlyFreshProcessCompatTest {
    private static final String ZOMBIES_BOOTSTRAP_RESOURCE =
            "com/cdp/codpattern/app/zombies/bootstrap/ZombiesBootstrap.class";
    private static final String COMPOSITION_SHIM_RESOURCE =
            "com/cdp/codpattern/CodPattern.class";

    private MainOnlyFreshProcessCompatTest() {
    }

    public static void main(String[] args) {
        require("codpattern".equals(CodPatternConstants.MOD_ID),
                "main-owned mod identity must retain the existing namespace");
        requireResourceAbsent(ZOMBIES_BOOTSTRAP_RESOURCE);
        requireResourceAbsent(COMPOSITION_SHIM_RESOURCE);
        requireIsolatedClasspath();

        Phase7MainOnlyVerificationBootstrap.install();

        List<String> definitions = GameModeRegistry.orderedDefinitions().stream()
                .map(definition -> definition.gameType())
                .toList();
        require(definitions.equals(List.of(BuiltInGameModes.FRONTLINE, BuiltInGameModes.TEAM_DEATHMATCH)),
                "fresh main-only bootstrap must install exactly Frontline then Team Deathmatch: " + definitions);
        require(BuiltInGameModes.FRONTLINE.equals(
                        GameModeRegistry.canonicalize(BuiltInGameModes.LEGACY_CDP_TDM)),
                "Frontline legacy alias must remain available in the main-only bootstrap");
        require(BuiltInGameModes.TEAM_DEATHMATCH.equals(
                        GameModeRegistry.canonicalize(BuiltInGameModes.LEGACY_CDP_TACTICAL_TDM)),
                "Team Deathmatch legacy alias must remain available in the main-only bootstrap");

        List<String> runtimeProviders = GameModeRuntimeRegistry.providers().stream()
                .map(provider -> provider.gameType())
                .toList();
        require(runtimeProviders.equals(definitions),
                "main-only runtime providers must match the two installed definitions: " + runtimeProviders);
        List<String> persistenceProviders = ModeMapPersistenceRegistry.providers().stream()
                .map(provider -> provider.gameType())
                .toList();
        require(persistenceProviders.equals(definitions),
                "main-only persistence providers must match the two installed definitions: "
                        + persistenceProviders);

        for (String gameType : definitions) {
            require(ModeMapEditorSchemaRegistry.find(gameType).isPresent(),
                    "missing main-only editor schema for " + gameType);
            require(ClientModePresentationRegistry.find(gameType).isPresent(),
                    "missing main-only client presentation for " + gameType);
        }

        require(GameModeRegistry.findDefinition(BuiltInGameModes.ZOMBIES).isEmpty(),
                "fresh main-only bootstrap must not inherit a Zombies definition");
        require(GameModeRuntimeRegistry.find(BuiltInGameModes.ZOMBIES).isEmpty(),
                "fresh main-only bootstrap must not inherit a Zombies runtime provider");
        require(ModeMapPersistenceRegistry.find(BuiltInGameModes.ZOMBIES).isEmpty(),
                "fresh main-only bootstrap must not inherit a Zombies persistence provider");
        require(ModeMapEditorSchemaRegistry.find(BuiltInGameModes.ZOMBIES).isEmpty(),
                "fresh main-only bootstrap must not inherit a Zombies editor schema");
        require(ClientModePresentationRegistry.find(BuiltInGameModes.ZOMBIES).isEmpty(),
                "fresh main-only bootstrap must not inherit a Zombies client presentation");

        System.out.println("PASS Phase 7 fresh-process main-only logical bootstrap");
    }

    private static void requireIsolatedClasspath() {
        String expectedMainOnly = System.getProperty("modeSplit.phase7.mainOnlyClasses", "");
        String forbiddenCombined = System.getProperty("modeSplit.phase7.combinedMainClasses", "");
        require(!expectedMainOnly.isBlank(), "main-only classes path was not supplied by the Gradle fence");

        List<Path> entries = Arrays.stream(System.getProperty("java.class.path", "")
                        .split(java.io.File.pathSeparator))
                .filter(entry -> !entry.isBlank())
                .map(entry -> Path.of(entry).toAbsolutePath().normalize())
                .toList();
        Path expected = Path.of(expectedMainOnly).toAbsolutePath().normalize();
        require(entries.contains(expected), "fresh JVM classpath does not contain the filtered main-only output");
        if (!forbiddenCombined.isBlank()) {
            Path forbidden = Path.of(forbiddenCombined).toAbsolutePath().normalize();
            require(!entries.contains(forbidden),
                    "fresh JVM classpath accidentally contains the combined production output: " + forbidden);
        }
    }

    private static void requireResourceAbsent(String resourceName) {
        ClassLoader loader = MainOnlyFreshProcessCompatTest.class.getClassLoader();
        URL resource = loader.getResource(resourceName);
        require(resource == null, "addon/shim implementation leaked into the main-only classpath: " + resource);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
