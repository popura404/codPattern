package com.cdp.codpattern.app.match.model;

public record DeathDecision(
        boolean cancelEvent,
        boolean restoreFullHealth
) {
    public static DeathDecision passThrough() {
        return new DeathDecision(false, false);
    }

    public static DeathDecision cancelAndRestoreHealth() {
        return new DeathDecision(true, true);
    }
}
