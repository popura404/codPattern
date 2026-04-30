package com.cdp.codpattern.app.match.model;

import com.cdp.codpattern.app.match.GameModeRuntimeProvider;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchema;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceProvider;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record GameModeDefinition(
        String gameType,
        List<String> aliases,
        String displayNameKey,
        String roomHeaderKey,
        String createCommand,
        List<TeamDescriptor> teams,
        ModeFamily family,
        TeamPolicy teamPolicy,
        JoinPolicy joinPolicy,
        LifecycleKind lifecycleKind,
        ScoreboardKind scoreboardKind,
        Set<ModeCapability> capabilities,
        Optional<GameModeRuntimeProvider> runtimeProvider,
        Optional<ModeMapPersistenceProvider> persistenceProvider,
        Optional<ModeMapEditorSchema> editorSchema,
        Optional<ClientModePresentation> clientPresentation
) {
    public GameModeDefinition(
            String gameType,
            List<String> aliases,
            String displayNameKey,
            String roomHeaderKey,
            String createCommand,
            List<TeamDescriptor> teams,
            ModeFamily family,
            TeamPolicy teamPolicy,
            JoinPolicy joinPolicy,
            LifecycleKind lifecycleKind,
            ScoreboardKind scoreboardKind,
            Set<ModeCapability> capabilities
    ) {
        this(
                gameType,
                aliases,
                displayNameKey,
                roomHeaderKey,
                createCommand,
                teams,
                family,
                teamPolicy,
                joinPolicy,
                lifecycleKind,
                scoreboardKind,
                capabilities,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public GameModeDefinition {
        Objects.requireNonNull(gameType, "gameType");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(roomHeaderKey, "roomHeaderKey");
        Objects.requireNonNull(createCommand, "createCommand");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(teamPolicy, "teamPolicy");
        Objects.requireNonNull(joinPolicy, "joinPolicy");
        Objects.requireNonNull(lifecycleKind, "lifecycleKind");
        Objects.requireNonNull(scoreboardKind, "scoreboardKind");

        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        teams = teams == null ? List.of() : List.copyOf(teams);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        runtimeProvider = runtimeProvider == null ? Optional.empty() : runtimeProvider;
        persistenceProvider = persistenceProvider == null ? Optional.empty() : persistenceProvider;
        editorSchema = editorSchema == null ? Optional.empty() : editorSchema;
        clientPresentation = clientPresentation == null ? Optional.empty() : clientPresentation;
    }

    public ModeDescriptor descriptor() {
        return new ModeDescriptor(gameType, displayNameKey, roomHeaderKey, createCommand, teams);
    }

    public boolean hasCapability(ModeCapability capability) {
        return capabilities.contains(capability);
    }
}
