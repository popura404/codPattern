package com.cdp.codpattern.config.weaponfilter;

public final class WeaponFilterClientCache {
    private static WeaponFilterConfig config;

    private WeaponFilterClientCache() {
    }

    public static void set(WeaponFilterConfig value) {
        config = value;
    }

    public static WeaponFilterConfig get() {
        return config;
    }

    public static void clear() {
        config = null;
    }
}
