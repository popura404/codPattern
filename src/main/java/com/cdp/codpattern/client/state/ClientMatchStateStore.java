package com.cdp.codpattern.client.state;

import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientMatchStateStore {
    private static final int SCORE_PULSE_DURATION = 12;
    private static final int PHASE_FLASH_DURATION = 20;
    private static final int KILL_FEED_ENTRY_DURATION = 250;
    private static final int KILL_FEED_FADE_TICKS = 10;
    private static final int MAX_KILL_FEED_ENTRIES = 6;
    private static final float FADE_IN_RATIO = 0.4f;
    private static final int FADE_OUT_DURATION = 30;

    private String currentPhase = "WAITING";
    private int remainingTimeTicks = 0;
    private int team1Score = 0;
    private int team2Score = 0;
    private final Map<String, Integer> teamScores = new HashMap<>();
    private int gameTimeTicks = 0;
    private int countdown = 0;
    private boolean blackout = false;
    private ClientTdmState.BlackoutPhase blackoutPhase = ClientTdmState.BlackoutPhase.NONE;
    private int blackoutTicksRemaining = 0;
    private int blackoutTotalTicks = 0;
    private int fadeOutTicksRemaining = 0;
    private int fadeOutTotalTicks = 0;
    private boolean playCountdownTickSound = false;
    private boolean playTeleportSound = false;
    private String previousPhase = "WAITING";
    private String pendingPhaseCue = "";
    private int scorePulseTicks = 0;
    private int phaseFlashTicks = 0;
    private String announcementKey = "";
    private int announcementTicks = 0;
    private int announcementTotalTicks = 0;
    private int endSummaryTicks = 0;
    private boolean dead = false;
    private String killerName = "";
    private int deathCamTicks = 0;
    private String roomContextName = "";
    private String syncedMapName = "";
    private final Map<String, List<PlayerInfo>> teamPlayers = new HashMap<>();
    private final List<ActiveKillFeedEntry> killFeedEntries = new ArrayList<>();

    public String currentPhase() {
        return currentPhase;
    }

    public int remainingTimeTicks() {
        return remainingTimeTicks;
    }

    public int team1Score() {
        return team1Score;
    }

    public int team2Score() {
        return team2Score;
    }

    public int gameTimeTicks() {
        return gameTimeTicks;
    }

    public int countdown() {
        return countdown;
    }

    public boolean blackout() {
        return blackout;
    }

    public ClientTdmState.BlackoutPhase blackoutPhase() {
        return blackoutPhase;
    }

    public String announcementKey() {
        return announcementKey;
    }

    public int announcementTicks() {
        return announcementTicks;
    }

    public boolean isDead() {
        return dead;
    }

    public String killerName() {
        return killerName;
    }

    public int deathCamTicks() {
        return deathCamTicks;
    }

    public int endSummaryPageIndex() {
        if (!"ENDED".equals(currentPhase)) {
            return 0;
        }
        int pageTicks = Math.max(1, PhaseStateMachine.END_SUMMARY_PAGE_TICKS);
        int page = endSummaryTicks / pageTicks;
        return Math.max(0, Math.min(PhaseStateMachine.END_SUMMARY_PAGE_COUNT - 1, page));
    }

    public int endSummaryPageTick() {
        int pageTicks = Math.max(1, PhaseStateMachine.END_SUMMARY_PAGE_TICKS);
        return endSummaryTicks % pageTicks;
    }

    public int endSummaryPageDurationTicks() {
        return Math.max(1, PhaseStateMachine.END_SUMMARY_PAGE_TICKS);
    }

    public String syncedMapName() {
        return syncedMapName;
    }

    public boolean hasRoomContext() {
        return !roomContextName.isBlank() || !syncedMapName.isBlank() || !teamPlayers.isEmpty();
    }

    public Map<String, List<PlayerInfo>> teamPlayersSnapshot() {
        Map<String, List<PlayerInfo>> snapshot = new HashMap<>();
        for (Map.Entry<String, List<PlayerInfo>> entry : teamPlayers.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return snapshot;
    }

    public List<KillFeedEntry> killFeedSnapshot() {
        List<KillFeedEntry> snapshot = new ArrayList<>(killFeedEntries.size());
        for (ActiveKillFeedEntry entry : killFeedEntries) {
            snapshot.add(entry.snapshot());
        }
        return snapshot;
    }

    public void updateTeamPlayers(String mapName, Map<String, List<PlayerInfo>> latestTeamPlayers) {
        syncedMapName = mapName == null ? "" : mapName;
        if (!syncedMapName.isBlank()) {
            roomContextName = syncedMapName;
        }
        teamPlayers.clear();
        if (latestTeamPlayers == null || latestTeamPlayers.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<PlayerInfo>> entry : latestTeamPlayers.entrySet()) {
            List<PlayerInfo> players = entry.getValue() == null
                    ? List.of()
                    : new ArrayList<>(entry.getValue());
            teamPlayers.put(entry.getKey(), players);
        }
    }

    public void updatePhase(String phase, int time) {
        String oldPhase = currentPhase;
        if (!phase.equals(oldPhase)) {
            if (phase.equals("WARMUP") || phase.equals("PLAYING")) {
                startFadeOut();
                playTeleportSound = true;
            }
            if (!"PLAYING".equals(phase)) {
                clearDeathCam();
            }
            if (!isKillFeedPhase(phase)) {
                clearKillFeed();
            }
            phaseFlashTicks = PHASE_FLASH_DURATION;
            triggerPhaseAnnouncement(phase);
            queuePhaseCue(phase);
            if ("ENDED".equals(phase)) {
                endSummaryTicks = 0;
            }
            if ("ENDED".equals(oldPhase) && !"ENDED".equals(phase)) {
                endSummaryTicks = 0;
            }
        }
        previousPhase = oldPhase;
        currentPhase = phase;
        remainingTimeTicks = time;
    }

    public void updateScore(int t1, int t2, int time) {
        updateScore(new HashMap<>(), t1, t2, time);
    }

    public void updateScore(Map<String, Integer> scores, int legacyTeam1, int legacyTeam2, int time) {
        int oldTeam1 = team1Score;
        int oldTeam2 = team2Score;

        teamScores.clear();
        if (scores != null && !scores.isEmpty()) {
            teamScores.putAll(scores);
        }

        team1Score = teamScores.getOrDefault("kortac", legacyTeam1);
        team2Score = teamScores.getOrDefault("specgru", legacyTeam2);

        if (team1Score != oldTeam1 || team2Score != oldTeam2) {
            scorePulseTicks = SCORE_PULSE_DURATION;
        }
        gameTimeTicks = time;
    }

    public int getTeamScore(String teamName, int fallback) {
        return teamScores.getOrDefault(teamName, fallback);
    }

    public void pushKillFeed(String killerName, String victimName, ItemStack weaponStack, boolean blunder) {
        killFeedEntries.add(0, new ActiveKillFeedEntry(killerName, victimName, weaponStack, blunder,
                KILL_FEED_ENTRY_DURATION));
        while (killFeedEntries.size() > MAX_KILL_FEED_ENTRIES) {
            killFeedEntries.remove(killFeedEntries.size() - 1);
        }
    }

    public void clearKillFeed() {
        killFeedEntries.clear();
    }

    public void setRoomContext(String roomName) {
        roomContextName = roomName == null ? "" : roomName;
    }

    public void clearRoomContext() {
        roomContextName = "";
        syncedMapName = "";
        teamPlayers.clear();
        clearKillFeed();
    }

    public void resetMatchState() {
        currentPhase = "WAITING";
        remainingTimeTicks = 0;
        team1Score = 0;
        team2Score = 0;
        teamScores.clear();
        gameTimeTicks = 0;
        countdown = 0;
        blackout = false;
        blackoutPhase = ClientTdmState.BlackoutPhase.NONE;
        blackoutTicksRemaining = 0;
        blackoutTotalTicks = 0;
        fadeOutTicksRemaining = 0;
        fadeOutTotalTicks = 0;
        playCountdownTickSound = false;
        playTeleportSound = false;
        previousPhase = "WAITING";
        pendingPhaseCue = "";
        scorePulseTicks = 0;
        phaseFlashTicks = 0;
        announcementKey = "";
        announcementTicks = 0;
        announcementTotalTicks = 0;
        endSummaryTicks = 0;
        roomContextName = "";
        syncedMapName = "";
        teamPlayers.clear();
        clearKillFeed();
        clearDeathCam();
    }

    public void updateCountdown(int count, boolean black) {
        countdown = count;
        blackout = black;

        if (black && count > 0) {
            blackoutTotalTicks = count;
            blackoutTicksRemaining = count;
            blackoutPhase = ClientTdmState.BlackoutPhase.FADE_IN;
        }

        if (!black && count > 0) {
            playCountdownTickSound = true;
        }
    }

    public void setDeathCam(String killer, int duration) {
        dead = true;
        if (killer != null && !killer.isBlank()) {
            killerName = killer;
        } else if (killerName == null || killerName.isBlank()) {
            killerName = Component.translatable("common.codpattern.unknown_player").getString();
        }
        deathCamTicks = Math.max(0, duration);
    }

    public void clearDeathCam() {
        dead = false;
        killerName = "";
        deathCamTicks = 0;
    }

    public float getBlackoutAlpha() {
        switch (blackoutPhase) {
            case FADE_IN -> {
                if (blackoutTicksRemaining <= 0 || blackoutTotalTicks <= 0) {
                    return 0.0f;
                }
                float progress = 1.0f - ((float) blackoutTicksRemaining / blackoutTotalTicks);
                if (progress < FADE_IN_RATIO) {
                    float t = progress / FADE_IN_RATIO;
                    return smoothstep(t);
                } else {
                    return 1.0f;
                }
            }
            case HOLD -> {
                return 1.0f;
            }
            case FADE_OUT -> {
                if (fadeOutTicksRemaining <= 0 || fadeOutTotalTicks <= 0) {
                    return 0.0f;
                }
                float t = (float) fadeOutTicksRemaining / fadeOutTotalTicks;
                return smoothstep(t);
            }
            default -> {
                return 0.0f;
            }
        }
    }

    public boolean isBlackoutActive() {
        return blackoutPhase != ClientTdmState.BlackoutPhase.NONE;
    }

    public float getAnnouncementAlpha() {
        if (announcementTicks <= 0 || announcementTotalTicks <= 0) {
            return 0.0f;
        }
        int fadeTicks = Math.min(10, announcementTotalTicks / 3);
        int elapsed = announcementTotalTicks - announcementTicks;
        if (elapsed < fadeTicks) {
            return (float) elapsed / Math.max(1, fadeTicks);
        }
        if (announcementTicks < fadeTicks) {
            return (float) announcementTicks / Math.max(1, fadeTicks);
        }
        return 1.0f;
    }

    public float getScorePulseStrength() {
        if (scorePulseTicks <= 0) {
            return 0.0f;
        }
        return (float) scorePulseTicks / SCORE_PULSE_DURATION;
    }

    public float getPhaseFlashStrength() {
        if (phaseFlashTicks <= 0) {
            return 0.0f;
        }
        return (float) phaseFlashTicks / PHASE_FLASH_DURATION;
    }

    public void clientTick() {
        if (remainingTimeTicks > 0) {
            remainingTimeTicks--;
        }
        if (scorePulseTicks > 0) {
            scorePulseTicks--;
        }
        if (phaseFlashTicks > 0) {
            phaseFlashTicks--;
        }
        if (announcementTicks > 0) {
            announcementTicks--;
        } else if (!announcementKey.isEmpty()) {
            announcementKey = "";
            announcementTotalTicks = 0;
        }
        if ("ENDED".equals(currentPhase)
                && endSummaryTicks < PhaseStateMachine.END_PHASE_TOTAL_TICKS) {
            endSummaryTicks++;
        }
        if (deathCamTicks > 0) {
            deathCamTicks--;
        }
        tickKillFeed();

        switch (blackoutPhase) {
            case FADE_IN -> {
                if (blackoutTicksRemaining > 0) {
                    blackoutTicksRemaining--;
                } else {
                    blackoutPhase = ClientTdmState.BlackoutPhase.HOLD;
                }
            }
            case FADE_OUT -> {
                if (fadeOutTicksRemaining > 0) {
                    fadeOutTicksRemaining--;
                } else {
                    blackoutPhase = ClientTdmState.BlackoutPhase.NONE;
                    fadeOutTotalTicks = 0;
                }
            }
            default -> {
            }
        }

        playPendingSounds();

        if ("COUNTDOWN".equals(currentPhase) && remainingTimeTicks > 0 && remainingTimeTicks % 20 == 0) {
            playCountdownTickSound = true;
        }
    }

    private void triggerPhaseAnnouncement(String phase) {
        switch (phase) {
            case "COUNTDOWN" -> showAnnouncement("hud.codpattern.tdm.announce.countdown", 52);
            case "WARMUP" -> showAnnouncement("hud.codpattern.tdm.announce.warmup", 46);
            case "PLAYING" -> showAnnouncement("hud.codpattern.tdm.announce.playing", 58);
            case "ENDED" -> showAnnouncement("hud.codpattern.tdm.announce.ended", 90);
            default -> {
            }
        }
    }

    private void showAnnouncement(String key, int durationTicks) {
        announcementKey = key;
        announcementTicks = Math.max(0, durationTicks);
        announcementTotalTicks = announcementTicks;
    }

    private void queuePhaseCue(String phase) {
        pendingPhaseCue = switch (phase) {
            case "COUNTDOWN" -> "countdown";
            case "WARMUP" -> "warmup";
            case "PLAYING" -> "playing";
            case "ENDED" -> "ended";
            default -> "";
        };
    }

    private void startFadeOut() {
        blackoutPhase = ClientTdmState.BlackoutPhase.FADE_OUT;
        fadeOutTotalTicks = FADE_OUT_DURATION;
        fadeOutTicksRemaining = FADE_OUT_DURATION;
        blackoutTicksRemaining = 0;
        blackoutTotalTicks = 0;
    }

    private void tickKillFeed() {
        for (int i = killFeedEntries.size() - 1; i >= 0; i--) {
            ActiveKillFeedEntry entry = killFeedEntries.get(i);
            entry.tick();
            if (entry.expired()) {
                killFeedEntries.remove(i);
            }
        }
    }

    private void playPendingSounds() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        if (playCountdownTickSound) {
            playCountdownTickSound = false;
            int secondsLeft = remainingTimeTicks / 20;
            if (secondsLeft <= 5 && secondsLeft > 0) {
                float pitch = 1.0f + (5 - secondsLeft) * 0.15f;
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, pitch);
            } else {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.get(), SoundSource.PLAYERS, 0.5f, 1.2f);
            }
        }

        if (!pendingPhaseCue.isEmpty()) {
            switch (pendingPhaseCue) {
                case "countdown" -> player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.get(), SoundSource.PLAYERS, 0.7f,
                        0.9f);
                case "warmup" -> player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, 0.75f,
                        0.95f);
                case "playing" -> player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.PLAYERS, 0.9f,
                        1.15f);
                case "ended" -> {
                    player.playNotifySound(SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.45f, 1.3f);
                    player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.PLAYERS, 0.8f, 0.7f);
                }
                default -> {
                }
            }
            pendingPhaseCue = "";
        }

        if (playTeleportSound) {
            playTeleportSound = false;
            player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.0f);
        }
    }

    private float smoothstep(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return t * t * (3.0f - 2.0f * t);
    }

    private boolean isKillFeedPhase(String phase) {
        return "WARMUP".equals(phase) || "PLAYING".equals(phase);
    }

    private static final class ActiveKillFeedEntry {
        private final String killerName;
        private final String victimName;
        private final ItemStack weaponStack;
        private final boolean blunder;
        private final int totalTicks;
        private int ticksRemaining;

        private ActiveKillFeedEntry(String killerName, String victimName, ItemStack weaponStack, boolean blunder, int totalTicks) {
            this.killerName = killerName;
            this.victimName = victimName;
            this.weaponStack = weaponStack == null ? ItemStack.EMPTY : weaponStack.copy();
            this.blunder = blunder;
            this.totalTicks = Math.max(1, totalTicks);
            this.ticksRemaining = this.totalTicks;
        }

        private void tick() {
            if (ticksRemaining > 0) {
                ticksRemaining--;
            }
        }

        private boolean expired() {
            return ticksRemaining <= 0;
        }

        private KillFeedEntry snapshot() {
            int fadeTicks = Math.min(KILL_FEED_FADE_TICKS, totalTicks);
            float alpha = ticksRemaining >= fadeTicks
                    ? 1.0f
                    : (float) ticksRemaining / Math.max(1, fadeTicks);
            return new KillFeedEntry(killerName, victimName, weaponStack, blunder, alpha);
        }
    }
}
