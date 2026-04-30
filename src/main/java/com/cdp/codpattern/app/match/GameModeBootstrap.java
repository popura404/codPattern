package com.cdp.codpattern.app.match;

import com.cdp.codpattern.app.match.editor.ModeMapEditorSchemaRegistry;
import com.cdp.codpattern.app.match.model.ClientModePresentationRegistry;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceRegistry;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;

public final class GameModeBootstrap {
    private GameModeBootstrap() {
    }

    public static void registerEditorSchemas() {
        GameModeRegistry.orderedDefinitions().forEach(definition ->
                definition.editorSchema().ifPresent(schema ->
                        ModeMapEditorSchemaRegistry.register(definition.gameType(), schema)));
    }

    public static void registerClientPresentations() {
        GameModeRegistry.orderedDefinitions().forEach(definition ->
                definition.clientPresentation().ifPresent(presentation ->
                        ClientModePresentationRegistry.register(definition.gameType(), presentation)));
    }

    public static void registerPersistenceProviders() {
        GameModeRegistry.orderedDefinitions().forEach(definition ->
                definition.persistenceProvider().ifPresent(provider ->
                        ModeMapPersistenceRegistry.register(provider)));
    }

    public static void registerRuntimeProviders() {
        GameModeRegistry.orderedDefinitions().forEach(definition ->
                definition.runtimeProvider().ifPresent(GameModeRuntimeRegistry::register));
    }

    public static void registerRuntimeGameTypes(RegisterFPSMapEvent event) {
        registerRuntimeProviders();
        if (event == null) {
            return;
        }
        GameModeRuntimeRegistry.providers().forEach(provider ->
                event.registerGameType(provider.gameType(), provider::createMap));
    }

    public static void registerCommonProviders() {
        registerEditorSchemas();
        registerClientPresentations();
        registerPersistenceProviders();
        registerRuntimeProviders();
    }

    public static void registerCommonProviders(RegisterFPSMapEvent event) {
        registerEditorSchemas();
        registerClientPresentations();
        registerPersistenceProviders();
        registerRuntimeGameTypes(event);
    }
}
