package com.cdp.codpattern.client.state;

import com.cdp.codpattern.app.tdm.service.TdmSettlementTiming;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The final roster and pause-aware monotonic clock for a single match presentation. */
public final class TdmSettlementState {
    private final int kortacScore;
    private final int specgruScore;
    private final Map<String, List<PlayerInfo>> rosters;
    private final Map<UUID, String> teamByPlayer = new HashMap<>();
    private long startNanos;
    private long pausedAtNanos;
    private boolean paused;

    TdmSettlementState(int kortacScore, int specgruScore, Map<String, List<PlayerInfo>> players,
                       int remainingTicks, long nowNanos) {
        this.kortacScore = kortacScore;
        this.specgruScore = specgruScore;
        Map<String, List<PlayerInfo>> snapshot = new HashMap<>();
        players.forEach((team, members) -> {
            snapshot.put(team, List.copyOf(members));
            members.forEach(member -> teamByPlayer.put(member.uuid(), team));
        });
        rosters = Map.copyOf(snapshot);
        // Older servers did not send a remaining time for ENDED.
        int elapsedTicks = remainingTicks > 0 ? Math.max(0, TdmSettlementTiming.TOTAL_TICKS - remainingTicks) : 0;
        startNanos = nowNanos - elapsedTicks * 50_000_000L;
    }

    public Map<String, List<PlayerInfo>> rosters() {
        return rosters;
    }

    public int kortacScore() {
        return kortacScore;
    }

    public int specgruScore() {
        return specgruScore;
    }

    public String teamOf(UUID player) {
        return teamByPlayer.getOrDefault(player, "");
    }

    public double elapsedSeconds(long nowNanos, boolean gamePaused) {
        if (gamePaused != paused) {
            if (gamePaused) {
                pausedAtNanos = nowNanos;
            } else {
                startNanos += nowNanos - pausedAtNanos;
            }
            paused = gamePaused;
        }
        long elapsedNanos = (paused ? pausedAtNanos : nowNanos) - startNanos;
        return Math.min(TdmSettlementTiming.TOTAL_TICKS / 20.0, Math.max(0L, elapsedNanos) / 1_000_000_000.0);
    }
}
