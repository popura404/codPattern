package com.cdp.codpattern.app.match;

import com.cdp.codpattern.app.match.model.ModeCapability;

public final class BuiltInGameModes {
    public static final String FRONTLINE = "frontline";
    public static final String TEAM_DEATHMATCH = "teamdeathmatch";
    public static final String ZOMBIES = "zombies";
    public static final String LEGACY_CDP_TDM = "cdptdm";
    public static final String LEGACY_CDP_TACTICAL_TDM = "cdptacticaltdm";

    private BuiltInGameModes() {
    }

    public static boolean isFrontline(String gameType) {
        return FRONTLINE.equals(GameModeRegistry.canonicalize(gameType));
    }

    public static boolean isTeamDeathMatch(String gameType) {
        return TEAM_DEATHMATCH.equals(GameModeRegistry.canonicalize(gameType));
    }

    public static boolean isZombies(String gameType) {
        return ZOMBIES.equals(GameModeRegistry.canonicalize(gameType));
    }

    public static boolean supportsDynamicRespawnPoints(String gameType) {
        return GameModeRegistry.hasCapability(gameType, ModeCapability.DYNAMIC_RESPAWN_POINTS);
    }
}
