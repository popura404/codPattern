package com.cdp.codpattern.app.tdm.model;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.extension.ModeDefinitionContributions;
import com.cdp.codpattern.app.match.extension.ModeDefinitionContributor;
import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.match.model.JoinPolicy;
import com.cdp.codpattern.app.match.model.LifecycleKind;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.ModeFamily;
import com.cdp.codpattern.app.match.model.ScoreboardKind;
import com.cdp.codpattern.app.match.model.TeamDescriptor;
import com.cdp.codpattern.app.match.model.TeamPolicy;
import com.cdp.codpattern.app.teammatch.TeamMatchPolicy;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class TdmGameModeDefinitions {
    private TdmGameModeDefinitions() {
    }

    public static void registerDefaults() {
        ModeDefinitionContributions.register(contributor());
    }

    public static ModeDefinitionContributor contributor() {
        return registrar -> definitions().forEach(registrar::register);
    }

    public static List<GameModeDefinition> definitions() {
        return List.of(frontline(), teamDeathmatch());
    }

    private static GameModeDefinition frontline() {
        return definition(
                TdmTeamMatchPolicies.frontline(),
                Set.of(
                        ModeCapability.TEAM_SELECTION,
                        ModeCapability.TEAM_BALANCE,
                        ModeCapability.READY_STATE,
                        ModeCapability.START_VOTE,
                        ModeCapability.END_VOTE,
                        ModeCapability.MATCH_END_TELEPORT,
                        ModeCapability.ROUND_START_SPAWNS,
                        ModeCapability.KILL_FEED,
                        ModeCapability.MATCH_RECORD_EXPORT,
                        ModeCapability.MODE_SPECIFIC_MAP_FEATURES
                ));
    }

    private static GameModeDefinition teamDeathmatch() {
        return definition(
                TdmTeamMatchPolicies.teamDeathmatch(),
                Set.of(
                        ModeCapability.TEAM_SELECTION,
                        ModeCapability.TEAM_BALANCE,
                        ModeCapability.READY_STATE,
                        ModeCapability.START_VOTE,
                        ModeCapability.END_VOTE,
                        ModeCapability.MATCH_END_TELEPORT,
                        ModeCapability.ROUND_START_SPAWNS,
                        ModeCapability.DYNAMIC_RESPAWN_POINTS,
                        ModeCapability.KILL_FEED,
                        ModeCapability.MATCH_RECORD_EXPORT,
                        ModeCapability.MODE_SPECIFIC_MAP_FEATURES
                ));
    }

    private static GameModeDefinition definition(
            TeamMatchPolicy policy,
            Set<ModeCapability> capabilities
    ) {
        return new GameModeDefinition(
                policy.gameType(),
                policy.aliases(),
                policy.displayNameKey(),
                policy.roomHeaderKey(),
                policy.createCommand(),
                teams(),
                ModeFamily.PVP_TEAM,
                TeamPolicy.FIXED_TEAMS,
                JoinPolicy.MODE_DEFINED,
                LifecycleKind.WAITING_START_PLAYING_ENDED,
                ScoreboardKind.TEAM_SCORE,
                capabilities,
                Optional.of(policy.runtimeProvider()),
                Optional.of(policy.persistenceProvider()),
                Optional.of(policy.editorSchema()),
                Optional.of(policy.clientPresentation())
        );
    }

    private static List<TeamDescriptor> teams() {
        return List.of(
                new TeamDescriptor(TdmTeamNames.KORTAC,
                        "screen.codpattern.tdm_room.team.kortac",
                        "hud.codpattern.tdm.team.kortac_short",
                        0xFFE35A5A),
                new TeamDescriptor(TdmTeamNames.SPECGRU,
                        "screen.codpattern.tdm_room.team.specgru",
                        "hud.codpattern.tdm.team.specgru_short",
                        0xFF66A6FF)
        );
    }
}
