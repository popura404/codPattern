package com.cdp.codpattern.app.zombies.model;

/**
 * Pure per-player zombies armor state. Armor visuals/items are handled outside this state.
 */
public record ZombiesArmorState(
        int armorLevel,
        double damageTakenMultiplier
) {
    public ZombiesArmorState {
        armorLevel = Math.max(0, armorLevel);
        damageTakenMultiplier = sanitizeDamageTakenMultiplier(damageTakenMultiplier);
    }

    public boolean isOwned() {
        return armorLevel > 0;
    }

    public boolean isAtLeast(int armorLevel) {
        return this.armorLevel >= armorLevel;
    }

    public static boolean isValidOwnedLevel(int armorLevel) {
        return armorLevel >= 1 && armorLevel <= 3;
    }

    public static boolean isValidDamageTakenMultiplier(double damageTakenMultiplier) {
        return Double.isFinite(damageTakenMultiplier)
                && damageTakenMultiplier > 0.0D
                && damageTakenMultiplier <= 1.0D;
    }

    private static double sanitizeDamageTakenMultiplier(double damageTakenMultiplier) {
        return isValidDamageTakenMultiplier(damageTakenMultiplier) ? damageTakenMultiplier : 1.0D;
    }
}
