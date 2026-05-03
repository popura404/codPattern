package com.cdp.codpattern.app.zombies.model;

public enum ZombiesGamePhase {
    WAITING,
    START_VOTE,
    OPENING_COUNTDOWN,
    INTERMISSION,
    WAVE_ACTIVE,
    VICTORY,
    FAILED,
    ENDING;

    public String key() {
        return name();
    }

    public boolean isJoinLocked() {
        return this != WAITING;
    }

    public boolean allowsPurchases() {
        return this == INTERMISSION || this == WAVE_ACTIVE;
    }

    public boolean isRoundRunning() {
        return this == OPENING_COUNTDOWN || this == INTERMISSION || this == WAVE_ACTIVE;
    }
}
