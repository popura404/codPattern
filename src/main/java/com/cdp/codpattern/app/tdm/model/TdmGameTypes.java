package com.cdp.codpattern.app.tdm.model;

import java.util.Locale;

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
        String normalized = normalize(gameType);
        if (LEGACY_CDP_TDM.equals(normalized)) {
            return FRONTLINE;
        }
        if (LEGACY_CDP_TACTICAL_TDM.equals(normalized)) {
            return TEAM_DEATHMATCH;
        }
        return normalized;
    }

    public static boolean isFrontline(String gameType) {
        return FRONTLINE.equals(canonicalize(gameType));
    }

    public static boolean isTeamDeathMatch(String gameType) {
        return TEAM_DEATHMATCH.equals(canonicalize(gameType));
    }

    private static String normalize(String gameType) {
        return gameType == null ? "" : gameType.trim().toLowerCase(Locale.ROOT);
    }
}
