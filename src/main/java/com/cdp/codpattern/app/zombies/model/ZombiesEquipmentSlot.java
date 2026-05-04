package com.cdp.codpattern.app.zombies.model;

import java.util.Locale;
import java.util.Optional;

public enum ZombiesEquipmentSlot {
    STARTER("starter", 0),
    PRIMARY("primary", 1);

    private final String key;
    private final int defaultInventorySlot;

    ZombiesEquipmentSlot(String key, int defaultInventorySlot) {
        this.key = key;
        this.defaultInventorySlot = defaultInventorySlot;
    }

    public String key() {
        return key;
    }

    public int defaultInventorySlot() {
        return defaultInventorySlot;
    }

    public static Optional<ZombiesEquipmentSlot> fromKey(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        for (ZombiesEquipmentSlot slot : values()) {
            if (slot.key.equals(normalized)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }
}
