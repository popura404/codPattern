package com.cdp.codpattern.app.tdm.model;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModeCapability;

public final class TdmGameTypes {
    private TdmGameTypes() {
    }

    public static final String FRONTLINE = "frontline";
    public static final String TEAM_DEATHMATCH = "teamdeathmatch";
    public static final String LEGACY_CDP_TDM = "cdptdm";
    public static final String LEGACY_CDP_TACTICAL_TDM = "cdptacticaltdm";

    public static final String CDP_TDM = FRONTLINE;
    public static final String CDP_TACTICAL_TDM = TEAM_DEATHMATCH;

    public static String canonicalize(String gameType) {
        return GameModeRegistry.canonicalize(gameType);
    }

    public static boolean isFrontline(String gameType) {
        return FRONTLINE.equals(canonicalize(gameType));
    }

    public static boolean isTeamDeathMatch(String gameType) {
        return TEAM_DEATHMATCH.equals(canonicalize(gameType));
    }

    public static boolean supportsDynamicRespawnPoints(String gameType) {
        return GameModeRegistry.hasCapability(gameType, ModeCapability.DYNAMIC_RESPAWN_POINTS);
    }
}
