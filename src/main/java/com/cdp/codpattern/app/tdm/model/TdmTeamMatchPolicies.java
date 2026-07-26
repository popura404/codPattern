package com.cdp.codpattern.app.tdm.model;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.teammatch.TeamMatchPolicy;
import com.cdp.codpattern.compat.fpsmatch.data.CodTacticalTdmMapData;
import com.cdp.codpattern.compat.fpsmatch.data.CodTdmMapData;
import com.cdp.codpattern.compat.fpsmatch.map.CodTacticalTdmRuntimeProvider;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmRuntimeProvider;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.tdm.CodTdmConfig;

import java.util.List;

/** Installed Frontline and Team Deathmatch policies for the shared team-match runtime. */
public final class TdmTeamMatchPolicies {
    private static final TeamMatchPolicy.Configuration SHARED_CONFIGURATION =
            new TeamMatchPolicy.Configuration() {
                @Override
                public int maxTeamDifference() {
                    return CodTdmConfig.getConfig().getMaxTeamDiff();
                }

                @Override
                public int respawnDelayTicks() {
                    return CodTdmConfig.getConfig().getRespawnDelayTicks();
                }

                @Override
                public int invincibilityTicks() {
                    return CodTdmConfig.getConfig().getInvincibilityTicks();
                }
            };

    private static final TeamMatchPolicy FRONTLINE = new TeamMatchPolicy(
            BuiltInGameModes.FRONTLINE,
            List.of(BuiltInGameModes.LEGACY_CDP_TDM),
            "mode.codpattern.frontline",
            "screen.codpattern.tdm_room.header",
            "/cdp map create frontline <名称> <起点> <终点>",
            false,
            false,
            SHARED_CONFIGURATION,
            CodTdmRuntimeProvider.INSTANCE,
            CodTdmMapData.persistenceProvider(),
            TdmMapEditorSchemas.frontlineSchema(),
            TdmClientModePresentations.frontlinePresentation(),
            server -> ConfigPath.SERVER_TDM_MATCH_RECORDS.getPath(server),
            "hud.codpattern.tdm.intro.frontline.objective",
            "frontline"
    );

    private static final TeamMatchPolicy TEAM_DEATHMATCH = new TeamMatchPolicy(
            BuiltInGameModes.TEAM_DEATHMATCH,
            List.of(BuiltInGameModes.LEGACY_CDP_TACTICAL_TDM),
            "mode.codpattern.teamdeathmatch",
            "screen.codpattern.tactical_room.header",
            "/cdp map create teamdeathmatch <名称> <起点> <终点>",
            true,
            true,
            SHARED_CONFIGURATION,
            CodTacticalTdmRuntimeProvider.INSTANCE,
            CodTacticalTdmMapData.persistenceProvider(),
            TdmMapEditorSchemas.teamDeathmatchSchema(),
            TdmClientModePresentations.teamDeathmatchPresentation(),
            server -> ConfigPath.SERVER_TACTICAL_TDM_MATCH_RECORDS.getPath(server),
            "hud.codpattern.tdm.intro.teamdeathmatch.objective",
            "teamdeathmatch"
    );

    private TdmTeamMatchPolicies() {
    }

    public static TeamMatchPolicy frontline() {
        return FRONTLINE;
    }

    public static TeamMatchPolicy teamDeathmatch() {
        return TEAM_DEATHMATCH;
    }

    public static TeamMatchPolicy forGameType(String gameType) {
        return BuiltInGameModes.isTeamDeathMatch(gameType) ? TEAM_DEATHMATCH : FRONTLINE;
    }
}
