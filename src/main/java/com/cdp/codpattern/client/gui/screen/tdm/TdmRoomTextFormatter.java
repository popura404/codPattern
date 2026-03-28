package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.app.tdm.model.TdmTeamNames;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TdmRoomTextFormatter {
    private TdmRoomTextFormatter() {
    }

    public static String statusIcon(String state) {
        return switch (state) {
            case "WAITING" -> "§a●";
            case "COUNTDOWN", "WARMUP" -> "§e●";
            case "PLAYING" -> "§c●";
            case "ENDED" -> "§7●";
            default -> "§f●";
        };
    }

    public static String roomListStatusText(String state, int remainingTimeTicks, Map<String, Integer> teamScores) {
        String phaseText = translatedPhaseText(state);
        String scoreText = teamScoreText(teamScores);
        if (remainingTimeTicks > 0) {
            return String.format("§7[%s %s]  %s", phaseText, formatTime(remainingTimeTicks), scoreText);
        }
        return String.format("§7[%s]  %s", phaseText, scoreText);
    }

    public static String phaseStatusText(String state, int remainingTimeTicks) {
        String phaseText = translatedPhaseText(state);
        if (remainingTimeTicks > 0) {
            return phaseText + " " + formatTime(remainingTimeTicks);
        }
        return phaseText;
    }

    public static String teamScoreText(Map<String, Integer> teamScores) {
        int kortacScore = teamScores.getOrDefault(TdmTeamNames.KORTAC, 0);
        int specgruScore = teamScores.getOrDefault(TdmTeamNames.SPECGRU, 0);
        return String.format("§cK:%d §7| §9S:%d", kortacScore, specgruScore);
    }

    public static String teamSplitText(Map<String, Integer> teamPlayerCounts) {
        int kortacCount = teamPlayerCounts.getOrDefault(TdmTeamNames.KORTAC, 0);
        int specgruCount = teamPlayerCounts.getOrDefault(TdmTeamNames.SPECGRU, 0);
        return kortacCount + "v" + specgruCount;
    }

    public static int pingBucket(int pingMs) {
        if (pingMs < 0) {
            return 5;
        }
        if (pingMs < 150) {
            return 0;
        }
        if (pingMs < 300) {
            return 1;
        }
        if (pingMs < 600) {
            return 2;
        }
        if (pingMs < 1000) {
            return 3;
        }
        return 4;
    }

    public static String formatKd(int kills, int deaths) {
        if (kills <= 0 && deaths <= 0) {
            return "0.00";
        }
        if (deaths <= 0) {
            return kills + ".00";
        }
        return String.format(Locale.ROOT, "%.2f", (double) kills / (double) deaths);
    }

    public static String shortPlayerId(UUID uuid) {
        String raw = uuid.toString();
        return raw.substring(0, Math.min(raw.length(), 8)).toUpperCase(Locale.ROOT);
    }

    public static String formatTime(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int minutes = seconds / 60;
        seconds %= 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static String translatedPhaseText(String state) {
        String normalized = state == null ? "waiting" : state.toLowerCase(Locale.ROOT);
        return Component.translatable("screen.codpattern.tdm_room.phase." + normalized).getString();
    }
}
