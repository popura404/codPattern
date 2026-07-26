package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.result.ModeErrorCode;

import java.util.Objects;

/**
 * Stable service error code used by zombies validation, startup, interaction, and cleanup flows.
 */
public final class ZombiesErrorCode {
    public static final ZombiesErrorCode OK = new ZombiesErrorCode("ok");

    public static final ZombiesErrorCode MAP_MISSING_ENDTP = new ZombiesErrorCode("map.missing_endtp");
    public static final ZombiesErrorCode MAP_MISSING_INITIAL_SPAWN = new ZombiesErrorCode("map.missing_initial_spawn");
    public static final ZombiesErrorCode MAP_OBJECT_OUT_OF_BOUNDS = new ZombiesErrorCode("map.object_out_of_bounds");

    public static final ZombiesErrorCode RULES_NO_VALID_WAVE = new ZombiesErrorCode("rules.no_valid_wave");
    public static final ZombiesErrorCode RULES_WAVE_CONFLICT = new ZombiesErrorCode("rules.wave_conflict");
    public static final ZombiesErrorCode RULES_INVALID_ENTITY = new ZombiesErrorCode("rules.invalid_entity");

    public static final ZombiesErrorCode STARTUP_PREFLIGHT_FAILED = new ZombiesErrorCode("startup.preflight_failed");
    public static final ZombiesErrorCode STARTUP_TELEPORT_FAILED = new ZombiesErrorCode("startup.teleport_failed");
    public static final ZombiesErrorCode STARTUP_STARTER_WEAPON_MISSING = new ZombiesErrorCode("startup.starter_weapon_missing");

    public static final ZombiesErrorCode PLAYER_DEAD = new ZombiesErrorCode("player.dead");
    public static final ZombiesErrorCode PLAYER_LEFT = new ZombiesErrorCode("player.left");
    public static final ZombiesErrorCode PLAYER_OFFLINE = new ZombiesErrorCode("player.offline");

    public static final ZombiesErrorCode WAVE_TIMEOUT = new ZombiesErrorCode("wave.timeout");

    public static final ZombiesErrorCode ECONOMY_NOT_ENOUGH_POINTS = new ZombiesErrorCode("economy.not_enough_points");
    public static final ZombiesErrorCode ECONOMY_INVALID_COST = new ZombiesErrorCode("economy.invalid_cost");

    public static final ZombiesErrorCode OBJECT_NOT_FOUND = new ZombiesErrorCode("object.not_found");
    public static final ZombiesErrorCode OBJECT_STALE_REVISION = new ZombiesErrorCode("object.stale_revision");
    public static final ZombiesErrorCode OBJECT_OUT_OF_RANGE = new ZombiesErrorCode("object.out_of_range");
    public static final ZombiesErrorCode OBJECT_ROOM_MISMATCH = new ZombiesErrorCode("object.room_mismatch");
    public static final ZombiesErrorCode OBJECT_BUSY = new ZombiesErrorCode("object.busy");

    public static final ZombiesErrorCode WEAPON_INVALID_CURRENT_WEAPON = new ZombiesErrorCode("weapon.invalid_current_weapon");
    public static final ZombiesErrorCode WEAPON_ALREADY_OWNED = new ZombiesErrorCode("weapon.already_owned");
    public static final ZombiesErrorCode WEAPON_MAX_UPGRADE = new ZombiesErrorCode("weapon.max_upgrade");

    public static final ZombiesErrorCode POWER_REQUIRES_POWER = new ZombiesErrorCode("power.requires_power");
    public static final ZombiesErrorCode POWER_ALREADY_ON = new ZombiesErrorCode("power.already_on");

    public static final ZombiesErrorCode CLEANUP_ENTITY_REMOVE_FAILED = new ZombiesErrorCode("cleanup.entity_remove_failed");
    public static final ZombiesErrorCode CLEANUP_PENDING_ENDTP_WRITTEN = new ZombiesErrorCode("cleanup.pending_endtp_written");

    private final String key;

    private ZombiesErrorCode(String key) {
        this.key = normalize(key);
    }

    public static ZombiesErrorCode of(String key) {
        String normalized = normalize(key);
        return OK.key.equals(normalized) ? OK : new ZombiesErrorCode(normalized);
    }

    public String key() {
        return key;
    }

    public ModeErrorCode toModeErrorCode() {
        return ModeErrorCode.of(key);
    }

    public static ZombiesErrorCode fromModeErrorCode(ModeErrorCode code) {
        return of(code == null ? null : code.key());
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ZombiesErrorCode that && key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return key;
    }

    private static String normalize(String key) {
        String normalized = Objects.requireNonNullElse(key, "").trim();
        return normalized.isEmpty() ? "ok" : normalized;
    }
}
