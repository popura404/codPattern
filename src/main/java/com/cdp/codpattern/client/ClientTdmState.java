package com.cdp.codpattern.client;

import com.cdp.codpattern.client.state.KillFeedEntry;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.match.RoomRosterDelta;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Legacy TDM state facade retained for older call sites. New code should use {@link ClientMatchState}.
 */
@Deprecated(forRemoval = false)
public final class ClientTdmState {
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

    private ClientTdmState() {
    }

    public static void updatePhase(String phase, int time) {
        ClientMatchState.updatePhase(phase, time);
    }

    public static void updateScore(int t1, int t2, int time) {
        ClientMatchState.updateScore(t1, t2, time);
    }

    public static void updateScore(Map<String, Integer> scores, int legacyTeam1, int legacyTeam2, int time) {
        ClientMatchState.updateScore(scores, legacyTeam1, legacyTeam2, time);
    }

    public static void updateTeamPlayers(String mapName, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        ClientMatchState.updateTeamPlayers(mapName, rosterVersion, teamPlayers);
    }

    public static RosterDeltaApplyResult applyTeamPlayerDelta(
            String roomKey,
            int rosterVersion,
            List<? extends RoomRosterDelta> updates
    ) {
        return convert(ClientMatchState.applyTeamPlayerDelta(roomKey, rosterVersion, updates));
    }

    public static Map<String, List<PlayerInfo>> teamPlayersSnapshot() {
        return ClientMatchState.teamPlayersSnapshot();
    }

    public static Map<String, Integer> teamScoresSnapshot() {
        return ClientMatchState.teamScoresSnapshot();
    }

    public static boolean hasRoomContext() {
        return ClientMatchState.hasRoomContext();
    }

    public static int endSummaryPageIndex() {
        return ClientMatchState.endSummaryPageIndex();
    }

    public static int endSummaryPageTick() {
        return ClientMatchState.endSummaryPageTick();
    }

    public static int endSummaryPageDurationTicks() {
        return ClientMatchState.endSummaryPageDurationTicks();
    }

    public static int getTeamScore(String teamName, int fallback) {
        return ClientMatchState.getTeamScore(teamName, fallback);
    }

    public static void resetMatchState() {
        ClientMatchState.resetMatchState();
    }

    public static void updateCountdown(int count, boolean black) {
        ClientMatchState.updateCountdown(count, black);
    }

    public static void setDeathCam(String killer, int duration, boolean updateViewLock, float lockedYaw, float lockedPitch) {
        ClientMatchState.setDeathCam(killer, duration, updateViewLock, lockedYaw, lockedPitch);
    }

    public static void clearDeathCam() {
        ClientMatchState.clearDeathCam();
    }

    public static void pushKillFeed(String killerName, String victimName, ItemStack weaponStack, boolean blunder) {
        ClientMatchState.pushKillFeed(killerName, victimName, weaponStack, blunder);
    }

    public static List<KillFeedEntry> killFeedSnapshot() {
        return ClientMatchState.killFeedSnapshot();
    }

    public static void clearKillFeed() {
        ClientMatchState.clearKillFeed();
    }

    public static void setRoomContext(String roomName) {
        ClientMatchState.setRoomContext(roomName);
    }

    public static void clearRoomContext() {
        ClientMatchState.clearRoomContext();
    }

    public static float getBlackoutAlpha() {
        return ClientMatchState.getBlackoutAlpha();
    }

    public static float getBlackoutInfoAlpha() {
        return ClientMatchState.getBlackoutInfoAlpha();
    }

    public static boolean isBlackoutActive() {
        return ClientMatchState.isBlackoutActive();
    }

    public static void clientTick() {
        ClientMatchState.clientTick();
    }

    public static float getAnnouncementAlpha() {
        return ClientMatchState.getAnnouncementAlpha();
    }

    public static float getScorePulseStrength() {
        return ClientMatchState.getScorePulseStrength();
    }

    public static float getPhaseFlashStrength() {
        return ClientMatchState.getPhaseFlashStrength();
    }

    public static String currentPhase() {
        return ClientMatchState.currentPhase();
    }

    public static String roomContextName() {
        return ClientMatchState.roomContextName();
    }

    public static int rosterVersion() {
        return ClientMatchState.rosterVersion();
    }

    public static long lastPhaseSyncAtMs() {
        return ClientMatchState.lastPhaseSyncAtMs();
    }

    public static long lastScoreSyncAtMs() {
        return ClientMatchState.lastScoreSyncAtMs();
    }

    public static long lastRosterSyncAtMs() {
        return ClientMatchState.lastRosterSyncAtMs();
    }

    public static int remainingTimeTicks() {
        return ClientMatchState.remainingTimeTicks();
    }

    public static int team1Score() {
        return ClientMatchState.team1Score();
    }

    public static int team2Score() {
        return ClientMatchState.team2Score();
    }

    public static int gameTimeTicks() {
        return ClientMatchState.gameTimeTicks();
    }

    public static String announcementKey() {
        return ClientMatchState.announcementKey();
    }

    public static int announcementTicks() {
        return ClientMatchState.announcementTicks();
    }

    public static boolean isDead() {
        return ClientMatchState.isDead();
    }

    public static String killerName() {
        return ClientMatchState.killerName();
    }

    public static int deathCamTicks() {
        return ClientMatchState.deathCamTicks();
    }

    public static boolean isDeathCamViewLocked() {
        return ClientMatchState.isDeathCamViewLocked();
    }

    public static float deathCamLockedYaw() {
        return ClientMatchState.deathCamLockedYaw();
    }

    public static float deathCamLockedPitch() {
        return ClientMatchState.deathCamLockedPitch();
    }

    public static void enforceDeathCamViewLock() {
        ClientMatchState.enforceDeathCamViewLock();
    }

    public static BlackoutPhase blackoutPhase() {
        return convert(ClientMatchState.blackoutPhase());
    }

    public static String syncedMapName() {
        return ClientMatchState.syncedMapName();
    }

    private static RosterDeltaApplyResult convert(ClientMatchState.RosterDeltaApplyResult result) {
        return RosterDeltaApplyResult.valueOf(result.name());
    }

    private static BlackoutPhase convert(ClientMatchState.BlackoutPhase phase) {
        return BlackoutPhase.valueOf(phase.name());
    }
}
