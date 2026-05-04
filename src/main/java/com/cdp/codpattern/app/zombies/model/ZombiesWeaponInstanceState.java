package com.cdp.codpattern.app.zombies.model;

import java.util.Objects;

/**
 * Pure zombies weapon instance state; no Minecraft item representation is stored here.
 */
public record ZombiesWeaponInstanceState(
        String gunId,
        int weaponLevel,
        int upgradeLevel,
        double damageMultiplier,
        int reserveAmmo,
        int maxReserveAmmo
) {
    public ZombiesWeaponInstanceState {
        gunId = Objects.requireNonNullElse(gunId, "").trim();
        weaponLevel = Math.max(0, weaponLevel);
        upgradeLevel = Math.max(0, upgradeLevel);
        damageMultiplier = sanitizePositive(damageMultiplier, 1.0D);
        maxReserveAmmo = Math.max(0, maxReserveAmmo);
        reserveAmmo = clampReserve(reserveAmmo, maxReserveAmmo);
    }

    public static ZombiesWeaponInstanceState primary(
            String gunId,
            int weaponLevel,
            double damageMultiplier,
            int maxReserveAmmo
    ) {
        return new ZombiesWeaponInstanceState(gunId, weaponLevel, 0, damageMultiplier, maxReserveAmmo, maxReserveAmmo);
    }

    public boolean sameGunAndLevel(String gunId, int weaponLevel) {
        String normalizedGunId = Objects.requireNonNullElse(gunId, "").trim();
        return this.gunId.equals(normalizedGunId) && this.weaponLevel == weaponLevel;
    }

    public boolean isReserveFull() {
        return reserveAmmo >= maxReserveAmmo;
    }

    public ZombiesWeaponInstanceState refillReserveAmmo() {
        return withReserveAmmo(maxReserveAmmo);
    }

    public ZombiesWeaponInstanceState withReserveAmmo(int reserveAmmo) {
        return new ZombiesWeaponInstanceState(
                gunId,
                weaponLevel,
                upgradeLevel,
                damageMultiplier,
                reserveAmmo,
                maxReserveAmmo);
    }

    public ZombiesWeaponInstanceState withMaxReserveAmmo(int maxReserveAmmo, boolean refill) {
        int sanitizedMaxReserveAmmo = Math.max(0, maxReserveAmmo);
        int nextReserveAmmo = refill ? sanitizedMaxReserveAmmo : Math.min(reserveAmmo, sanitizedMaxReserveAmmo);
        return new ZombiesWeaponInstanceState(
                gunId,
                weaponLevel,
                upgradeLevel,
                damageMultiplier,
                nextReserveAmmo,
                sanitizedMaxReserveAmmo);
    }

    public ZombiesWeaponInstanceState withUpgrade(int upgradeLevel, double damageMultiplier) {
        return new ZombiesWeaponInstanceState(
                gunId,
                weaponLevel,
                upgradeLevel,
                damageMultiplier,
                reserveAmmo,
                maxReserveAmmo);
    }

    public static boolean isValidGunId(String gunId) {
        return gunId != null && !gunId.trim().isEmpty();
    }

    public static boolean isValidWeaponLevel(int weaponLevel) {
        return weaponLevel > 0;
    }

    public static boolean isValidDamageMultiplier(double damageMultiplier) {
        return Double.isFinite(damageMultiplier) && damageMultiplier > 0.0D;
    }

    private static int clampReserve(int reserveAmmo, int maxReserveAmmo) {
        if (reserveAmmo <= 0 || maxReserveAmmo <= 0) {
            return 0;
        }
        return Math.min(reserveAmmo, maxReserveAmmo);
    }

    private static double sanitizePositive(double value, double fallback) {
        return isValidDamageMultiplier(value) ? value : fallback;
    }
}
