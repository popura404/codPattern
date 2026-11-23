package com.cdp.codpattern.app.tdm.service;

/** Presentation shared by Frontline and Team Deathmatch. Durations use 20 game ticks per second. */
public final class TdmSettlementTiming {
    public static final int BACKDROP_TICKS = 10;
    public static final int RESULT_TICKS = 120;
    public static final int SCOREBOARD_TICKS = 120;
    public static final int TOTAL_TICKS = BACKDROP_TICKS + RESULT_TICKS + SCOREBOARD_TICKS;

    private TdmSettlementTiming() {
    }
}
