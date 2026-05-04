package com.cdp.codpattern.app.zombies.model;

import java.util.Locale;
import java.util.Optional;

public enum ZombiesBuffType {
    DOUBLE_HEALTH("double_health"),
    SPEED_BOOST("speed_boost"),
    REACTIVE_EXPLOSION("reactive_explosion"),
    DOUBLE_AMMO("double_ammo"),
    SCORE_MULTIPLIER("score_multiplier"),
    HEADSHOT_DAMAGE("headshot_damage");

    private final String id;

    ZombiesBuffType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<ZombiesBuffType> fromId(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (ZombiesBuffType type : values()) {
            if (type.id.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
