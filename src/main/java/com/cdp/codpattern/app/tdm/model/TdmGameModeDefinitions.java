package com.cdp.codpattern.app.tdm.model;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.match.model.JoinPolicy;
import com.cdp.codpattern.app.match.model.LifecycleKind;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.ModeFamily;
import com.cdp.codpattern.app.match.model.ScoreboardKind;
import com.cdp.codpattern.app.match.model.TeamDescriptor;
import com.cdp.codpattern.app.match.model.TeamPolicy;
import com.cdp.codpattern.compat.fpsmatch.data.CodTacticalTdmMapData;
import com.cdp.codpattern.compat.fpsmatch.data.CodTdmMapData;
import com.cdp.codpattern.compat.fpsmatch.map.CodTacticalTdmRuntimeProvider;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmRuntimeProvider;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class TdmGameModeDefinitions {
    private TdmGameModeDefinitions() {
    }

    public static void registerDefaults() {
        definitions().forEach(GameModeRegistry::registerDefinition);
    }

    public static List<GameModeDefinition> definitions() {
        return List.of(frontline(), teamDeathmatch());
    }

    private static GameModeDefinition frontline() {
        return new GameModeDefinition(
                BuiltInGameModes.FRONTLINE,
                List.of(BuiltInGameModes.LEGACY_CDP_TDM),
                "mode.codpattern.frontline",
                "screen.codpattern.tdm_room.header",
                "/cdp map create frontline <名称> <起点> <终点>",
                teams(),
                ModeFamily.PVP_TEAM,
                TeamPolicy.FIXED_TEAMS,
                JoinPolicy.MODE_DEFINED,
                LifecycleKind.WAITING_START_PLAYING_ENDED,
                ScoreboardKind.TEAM_SCORE,
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
                ),
                Optional.of(CodTdmRuntimeProvider.INSTANCE),
                Optional.of(CodTdmMapData.persistenceProvider()),
                Optional.of(TdmMapEditorSchemas.frontlineSchema()),
                Optional.of(TdmClientModePresentations.frontlinePresentation())
        );
    }

    private static GameModeDefinition teamDeathmatch() {
        return new GameModeDefinition(
                BuiltInGameModes.TEAM_DEATHMATCH,
                List.of(BuiltInGameModes.LEGACY_CDP_TACTICAL_TDM),
                "mode.codpattern.teamdeathmatch",
                "screen.codpattern.tactical_room.header",
                "/cdp map create teamdeathmatch <名称> <起点> <终点>",
                teams(),
                ModeFamily.PVP_TEAM,
                TeamPolicy.FIXED_TEAMS,
                JoinPolicy.MODE_DEFINED,
                LifecycleKind.WAITING_START_PLAYING_ENDED,
                ScoreboardKind.TEAM_SCORE,
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
                ),
                Optional.of(CodTacticalTdmRuntimeProvider.INSTANCE),
                Optional.of(CodTacticalTdmMapData.persistenceProvider()),
                Optional.of(TdmMapEditorSchemas.teamDeathmatchSchema()),
                Optional.of(TdmClientModePresentations.teamDeathmatchPresentation())
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
