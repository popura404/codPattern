package com.cdp.codpattern.app.teammatch;

import com.cdp.codpattern.app.match.GameModeRuntimeProvider;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchema;
import com.cdp.codpattern.app.match.model.ClientModePresentation;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceProvider;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.data.SpawnSelectionReason;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Explicit per-mode policy for the runtime shared by Frontline and Team Deathmatch. */
public record TeamMatchPolicy(
        String gameType,
        List<String> aliases,
        String displayNameKey,
        String roomHeaderKey,
        String createCommand,
        boolean dynamicRespawnEnabled,
        boolean tacticalCompatibilityPorts,
        Configuration configuration,
        GameModeRuntimeProvider runtimeProvider,
        ModeMapPersistenceProvider persistenceProvider,
        ModeMapEditorSchema editorSchema,
        ClientModePresentation clientPresentation,
        Function<MinecraftServer, Path> matchRecordDirectory,
        String introObjectiveKey,
        String recordModeLabel
) {
    public TeamMatchPolicy {
        Objects.requireNonNull(gameType, "gameType");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(roomHeaderKey, "roomHeaderKey");
        Objects.requireNonNull(createCommand, "createCommand");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(runtimeProvider, "runtimeProvider");
        Objects.requireNonNull(persistenceProvider, "persistenceProvider");
        Objects.requireNonNull(editorSchema, "editorSchema");
        Objects.requireNonNull(clientPresentation, "clientPresentation");
        Objects.requireNonNull(matchRecordDirectory, "matchRecordDirectory");
        Objects.requireNonNull(introObjectiveKey, "introObjectiveKey");
        Objects.requireNonNull(recordModeLabel, "recordModeLabel");
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    public List<SpawnPointKind> spawnSelectionOrder(SpawnSelectionReason reason) {
        if (!dynamicRespawnEnabled || reason == SpawnSelectionReason.ROUND_START) {
            return List.of(SpawnPointKind.INITIAL);
        }
        return List.of(SpawnPointKind.DYNAMIC_CANDIDATE, SpawnPointKind.INITIAL);
    }

    public Path resolveMatchRecordDirectory(MinecraftServer server) {
        return matchRecordDirectory.apply(server);
    }

    public interface Configuration {
        int maxTeamDifference();

        int respawnDelayTicks();

        int invincibilityTicks();
    }
}
