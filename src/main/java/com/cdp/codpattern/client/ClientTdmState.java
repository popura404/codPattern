package com.cdp.codpattern.client;

import com.cdp.codpattern.client.state.ClientMatchStateStore;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;

import java.util.List;
import java.util.Map;

/**
 * 客户端 TDM 状态静态门面
 * 实际状态存储在实例化的 {@link ClientMatchStateStore}。
 */
public final class ClientTdmState {
    public enum BlackoutPhase {
        NONE,
        FADE_IN,
        HOLD,
        FADE_OUT
    }

    private static final ClientMatchStateStore STORE = new ClientMatchStateStore();

    private ClientTdmState() {
    }

    public static void updatePhase(String phase, int time) {
        STORE.updatePhase(phase, time);
    }

    public static void updateScore(int t1, int t2, int time) {
        STORE.updateScore(t1, t2, time);
    }

    public static void updateScore(Map<String, Integer> scores, int legacyTeam1, int legacyTeam2, int time) {
        STORE.updateScore(scores, legacyTeam1, legacyTeam2, time);
    }

    public static void updateTeamPlayers(String mapName, Map<String, List<PlayerInfo>> teamPlayers) {
        STORE.updateTeamPlayers(mapName, teamPlayers);
    }

    public static Map<String, List<PlayerInfo>> teamPlayersSnapshot() {
        return STORE.teamPlayersSnapshot();
    }

    public static int endSummaryPageIndex() {
        return STORE.endSummaryPageIndex();
    }

    public static int endSummaryPageTick() {
        return STORE.endSummaryPageTick();
    }

    public static int endSummaryPageDurationTicks() {
        return STORE.endSummaryPageDurationTicks();
    }

    public static int getTeamScore(String teamName, int fallback) {
        return STORE.getTeamScore(teamName, fallback);
    }

    public static void resetMatchState() {
        STORE.resetMatchState();
    }

    public static void updateCountdown(int count, boolean black) {
        STORE.updateCountdown(count, black);
    }

    public static void setDeathCam(String killer, int duration) {
        STORE.setDeathCam(killer, duration);
    }

    public static void clearDeathCam() {
        STORE.clearDeathCam();
    }

    public static float getBlackoutAlpha() {
        return STORE.getBlackoutAlpha();
    }

    public static boolean isBlackoutActive() {
        return STORE.isBlackoutActive();
    }

    public static void clientTick() {
        STORE.clientTick();
    }

    public static float getAnnouncementAlpha() {
        return STORE.getAnnouncementAlpha();
    }

    public static float getScorePulseStrength() {
        return STORE.getScorePulseStrength();
    }

    public static float getPhaseFlashStrength() {
        return STORE.getPhaseFlashStrength();
    }

    public static String currentPhase() {
        return STORE.currentPhase();
    }

    public static int remainingTimeTicks() {
        return STORE.remainingTimeTicks();
    }

    public static int team1Score() {
        return STORE.team1Score();
    }

    public static int team2Score() {
        return STORE.team2Score();
    }

    public static String announcementKey() {
        return STORE.announcementKey();
    }

    public static int announcementTicks() {
        return STORE.announcementTicks();
    }

    public static boolean isDead() {
        return STORE.isDead();
    }

    public static String killerName() {
        return STORE.killerName();
    }

    public static int deathCamTicks() {
        return STORE.deathCamTicks();
    }

    public static BlackoutPhase blackoutPhase() {
        return STORE.blackoutPhase();
    }
}
