package com.cdp.codpattern.app.zombies.sync;

/**
 * First frozen set of zombies runtime metric and player value keys.
 */
public final class ZombiesRuntimeStateKeys {
    public static final String METRIC_WAVE = "wave";
    public static final String METRIC_ZOMBIES_LEFT = "zombies_left";
    public static final String METRIC_ALIVE_PLAYERS = "alive_players";
    public static final String METRIC_MAX_PLAYERS = "max_players";
    public static final String METRIC_ACTIVE_ZOMBIES = "active_zombies";

    public static final String PLAYER_POINTS = "points";
    public static final String PLAYER_KILLS = "kills";
    public static final String PLAYER_ASSISTS = "assists";
    public static final String PLAYER_DEATHS = "deaths";
    public static final String PLAYER_LIFE_STATE = "life_state";
    public static final String PLAYER_CONNECTION_STATE = "connection_state";
    public static final String PLAYER_ARMOR_LEVEL = "armor.level";
    public static final String PLAYER_WEAPON_PRIMARY_LEVEL = "weapon.primary.level";
    public static final String PLAYER_WEAPON_PRIMARY_UPGRADE = "weapon.primary.upgrade";
    public static final String PLAYER_POWER_ENABLED = "power.enabled";
    public static final String PLAYER_BUFF_PREFIX = "buff.";
    public static final String ACTIVE_ZOMBIE_ENTITY_IDS = "entities.active_zombie_ids";

    private ZombiesRuntimeStateKeys() {
    }

    public static String survivorLifeState(String playerId) {
        return survivorKey(playerId, "life_state");
    }

    public static String survivorConnectionState(String playerId) {
        return survivorKey(playerId, "connection_state");
    }

    public static String survivorPoints(String playerId) {
        return survivorKey(playerId, "points");
    }

    public static String prompt(String objectType, String reason) {
        return "prompt." + clean(objectType) + "." + clean(reason);
    }

    public static String playerBuff(String buffId) {
        return PLAYER_BUFF_PREFIX + clean(buffId);
    }

    private static String survivorKey(String playerId, String suffix) {
        return "survivor." + clean(playerId) + "." + suffix;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
