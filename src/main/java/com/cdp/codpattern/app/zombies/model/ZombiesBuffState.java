package com.cdp.codpattern.app.zombies.model;

import java.util.Objects;

public record ZombiesBuffState(
        ZombiesBuffType type,
        double multiplier
) {
    public ZombiesBuffState {
        type = Objects.requireNonNull(type, "type");
        multiplier = sanitizeMultiplier(type, multiplier);
    }

    public static ZombiesBuffState defaultFor(ZombiesBuffType type) {
        return new ZombiesBuffState(type, defaultMultiplier(type));
    }

    private static double sanitizeMultiplier(ZombiesBuffType type, double multiplier) {
        if (Double.isFinite(multiplier) && multiplier > 0.0D) {
            return multiplier;
        }
        return defaultMultiplier(type);
    }

    private static double defaultMultiplier(ZombiesBuffType type) {
        return switch (type) {
            case DOUBLE_HEALTH, DOUBLE_AMMO -> 2.0D;
            case SPEED_BOOST, SCORE_MULTIPLIER -> 1.25D;
            case HEADSHOT_DAMAGE -> 1.5D;
            case REACTIVE_EXPLOSION -> 1.0D;
        };
    }
}
