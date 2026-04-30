package com.cdp.codpattern.client;

import com.cdp.codpattern.client.state.ClientMatchStateStore;
import com.cdp.codpattern.client.state.KillFeedEntry;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.match.RoomRosterDelta;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

public final class ClientMatchState {
    public enum RosterDeltaApplyResult {
        APPLIED,
        VERSION_GAP,
        PLAYER_MISSING,
        ROOM_MISMATCH
    }

    public enum BlackoutPhase {
        NONE,
        FADE_IN,
        HOLD,
        FADE_OUT
    }

    private static final ClientMatchStateStore STORE = new ClientMatchStateStore();

    private ClientMatchState() {
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

    public static void updateTeamPlayers(String mapName, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        STORE.updateTeamPlayers(mapName, rosterVersion, teamPlayers);
    }

    public static RosterDeltaApplyResult applyTeamPlayerDelta(
            String roomKey,
            int rosterVersion,
            List<? extends RoomRosterDelta> updates
    ) {
        return STORE.applyTeamPlayerDelta(roomKey, rosterVersion, updates);
    }

    public static Map<String, List<PlayerInfo>> teamPlayersSnapshot() {
        return STORE.teamPlayersSnapshot();
    }

    public static Map<String, Integer> teamScoresSnapshot() {
        return STORE.teamScoresSnapshot();
    }

    public static boolean hasRoomContext() {
        return STORE.hasRoomContext();
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
        TdmCombatMarkerTracker.INSTANCE.clear();
    }

    public static void updateCountdown(int count, boolean black) {
        STORE.updateCountdown(count, black);
    }

    public static void setDeathCam(String killer, int duration, boolean updateViewLock, float lockedYaw, float lockedPitch) {
        STORE.setDeathCam(killer, duration, updateViewLock, lockedYaw, lockedPitch);
    }

    public static void clearDeathCam() {
        STORE.clearDeathCam();
    }

    public static void pushKillFeed(String killerName, String victimName, ItemStack weaponStack, boolean blunder) {
        STORE.pushKillFeed(killerName, victimName, weaponStack, blunder);
    }

    public static List<KillFeedEntry> killFeedSnapshot() {
        return STORE.killFeedSnapshot();
    }

    public static void clearKillFeed() {
        STORE.clearKillFeed();
    }

    public static void setRoomContext(String roomName) {
        STORE.setRoomContext(roomName);
    }

    public static void clearRoomContext() {
        STORE.clearRoomContext();
    }

    public static float getBlackoutAlpha() {
        return STORE.getBlackoutAlpha();
    }

    public static float getBlackoutInfoAlpha() {
        return STORE.getBlackoutInfoAlpha();
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

    public static String roomContextName() {
        return STORE.roomContextName();
    }

    public static int rosterVersion() {
        return STORE.rosterVersion();
    }

    public static long lastPhaseSyncAtMs() {
        return STORE.lastPhaseSyncAtMs();
    }

    public static long lastScoreSyncAtMs() {
        return STORE.lastScoreSyncAtMs();
    }

    public static long lastRosterSyncAtMs() {
        return STORE.lastRosterSyncAtMs();
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

    public static int gameTimeTicks() {
        return STORE.gameTimeTicks();
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

    public static boolean isDeathCamViewLocked() {
        return STORE.isDeathCamViewLocked();
    }

    public static float deathCamLockedYaw() {
        return STORE.deathCamLockedYaw();
    }

    public static float deathCamLockedPitch() {
        return STORE.deathCamLockedPitch();
    }

    public static void enforceDeathCamViewLock() {
        STORE.enforceDeathCamViewLock();
    }

    public static BlackoutPhase blackoutPhase() {
        return STORE.blackoutPhase();
    }

    public static String syncedMapName() {
        return STORE.syncedMapName();
    }
}
