package com.cdp.codpattern.config.backpack;

/** Loadout display order and the keys shared by the save file and selection packets. */
public enum BackpackSlot {
    PRIMARY("primary"),
    SECONDARY("secondary"),
    MELEE("melee"),
    ITEM1("item1"),
    ITEM2("item2");

    private final String key;

    BackpackSlot(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean isGunSlot() {
        return this == PRIMARY || this == SECONDARY;
    }

    public boolean isEquipmentSlot() {
        return this == ITEM1 || this == ITEM2;
    }

    public static BackpackSlot fromKey(String key) {
        for (BackpackSlot slot : values()) {
            if (slot.key.equals(key)) {
                return slot;
            }
        }
        return null;
    }

    /** Existing menus and addons name the two equipment buttons tactical and lethal. */
    public static BackpackSlot fromMenuKey(String key) {
        if ("tactical".equals(key)) return ITEM1;
        if ("lethal".equals(key)) return ITEM2;
        return fromKey(key);
    }
}
