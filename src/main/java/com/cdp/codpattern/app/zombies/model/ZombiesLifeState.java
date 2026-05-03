package com.cdp.codpattern.app.zombies.model;

/**
 * Runtime-only zombies player life state.
 */
public enum ZombiesLifeState {
    ALIVE,
    DEAD_SPECTATING;

    public boolean isAlive() {
        return this == ALIVE;
    }

    public boolean isDeadSpectating() {
        return this == DEAD_SPECTATING;
    }
}
