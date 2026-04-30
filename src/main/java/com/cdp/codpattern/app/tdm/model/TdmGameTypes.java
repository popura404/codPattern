package com.cdp.codpattern.app.tdm.model;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.GameModeRegistry;

public final class TdmGameTypes {
    private TdmGameTypes() {
    }

    public static final String FRONTLINE = BuiltInGameModes.FRONTLINE;
    public static final String TEAM_DEATHMATCH = BuiltInGameModes.TEAM_DEATHMATCH;
    public static final String LEGACY_CDP_TDM = BuiltInGameModes.LEGACY_CDP_TDM;
    public static final String LEGACY_CDP_TACTICAL_TDM = BuiltInGameModes.LEGACY_CDP_TACTICAL_TDM;

    public static final String CDP_TDM = FRONTLINE;
    public static final String CDP_TACTICAL_TDM = TEAM_DEATHMATCH;

    public static String canonicalize(String gameType) {
        return GameModeRegistry.canonicalize(gameType);
    }

    public static boolean isFrontline(String gameType) {
        return BuiltInGameModes.isFrontline(gameType);
    }

    public static boolean isTeamDeathMatch(String gameType) {
        return BuiltInGameModes.isTeamDeathMatch(gameType);
    }

    public static boolean supportsDynamicRespawnPoints(String gameType) {
        return BuiltInGameModes.supportsDynamicRespawnPoints(gameType);
    }
}
