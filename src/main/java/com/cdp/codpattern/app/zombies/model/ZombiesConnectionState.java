package com.cdp.codpattern.app.zombies.model;

/**
 * Runtime-only zombies player connection state.
 */
public enum ZombiesConnectionState {
    ONLINE,
    OFFLINE,
    LEFT;

    public boolean isOnline() {
        return this == ONLINE;
    }

    public boolean isOffline() {
        return this == OFFLINE;
    }

    public boolean isLeft() {
        return this == LEFT;
    }
}
