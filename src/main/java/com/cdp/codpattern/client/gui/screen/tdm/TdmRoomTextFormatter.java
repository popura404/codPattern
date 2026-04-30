package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.app.match.model.RoomSummaryMetric;
import com.cdp.codpattern.client.gui.screen.match.ModeRoomTextFormatter;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Legacy utility name retained for older callers. New code should use {@link ModeRoomTextFormatter}.
 */
@Deprecated(forRemoval = false)
public final class TdmRoomTextFormatter {
    private TdmRoomTextFormatter() {
    }

    public static String statusIcon(String state) {
        return ModeRoomTextFormatter.statusIcon(state);
    }

    public static String roomListStatusText(String state, int remainingTimeTicks, Map<String, Integer> teamScores) {
        return ModeRoomTextFormatter.roomListStatusText(state, remainingTimeTicks, teamScores);
    }

    public static String phaseStatusText(String state, int remainingTimeTicks) {
        return ModeRoomTextFormatter.phaseStatusText(state, remainingTimeTicks);
    }

    public static String teamScoreText(Map<String, Integer> teamScores) {
        return ModeRoomTextFormatter.teamScoreText(teamScores);
    }

    public static String teamSplitText(Map<String, Integer> teamPlayerCounts) {
        return ModeRoomTextFormatter.teamSplitText(teamPlayerCounts);
    }

    public static Component metricText(RoomSummaryMetric metric) {
        return ModeRoomTextFormatter.metricText(metric);
    }

    public static int pingBucket(int pingMs) {
        return ModeRoomTextFormatter.pingBucket(pingMs);
    }

    public static String formatKd(int kills, int deaths) {
        return ModeRoomTextFormatter.formatKd(kills, deaths);
    }

    public static String shortPlayerId(UUID uuid) {
        return ModeRoomTextFormatter.shortPlayerId(uuid);
    }

    public static String formatTime(int ticks) {
        return ModeRoomTextFormatter.formatTime(ticks);
    }
}
