package com.cdp.codpattern.app.zombies.model;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Per-match, non-persistent zombies player runtime state.
 */
public class ZombiesPlayerRuntimeState {
    private final UUID playerId;
    private double points;
    private int kills;
    private int assists;
    private int deaths;
    private ZombiesLifeState lifeState;
    private ZombiesConnectionState connectionState;
    private Long offlineSinceTick;
    private BlockPos lastAliveTargetPos;

    public ZombiesPlayerRuntimeState(UUID playerId) {
        this(playerId, 0.0D, 0, 0, 0, ZombiesLifeState.ALIVE, ZombiesConnectionState.ONLINE, null, null);
    }

    public ZombiesPlayerRuntimeState(
            UUID playerId,
            double points,
            int kills,
            int assists,
            int deaths,
            ZombiesLifeState lifeState,
            ZombiesConnectionState connectionState,
            Long offlineSinceTick,
            BlockPos lastAliveTargetPos
    ) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.points = sanitizePoints(points);
        this.kills = Math.max(0, kills);
        this.assists = Math.max(0, assists);
        this.deaths = Math.max(0, deaths);
        this.lifeState = lifeState == null ? ZombiesLifeState.ALIVE : lifeState;
        this.connectionState = connectionState == null ? ZombiesConnectionState.ONLINE : connectionState;
        this.offlineSinceTick = offlineSinceTick;
        this.lastAliveTargetPos = lastAliveTargetPos;
    }

    public UUID playerId() {
        return playerId;
    }

    public synchronized double points() {
        return points;
    }

    public synchronized int displayPoints() {
        return (int) Math.floor(points);
    }

    public synchronized int kills() {
        return kills;
    }

    public synchronized int assists() {
        return assists;
    }

    public synchronized int deaths() {
        return deaths;
    }

    public synchronized ZombiesLifeState lifeState() {
        return lifeState;
    }

    public synchronized ZombiesConnectionState connectionState() {
        return connectionState;
    }

    public synchronized OptionalLong offlineSinceTick() {
        return offlineSinceTick == null ? OptionalLong.empty() : OptionalLong.of(offlineSinceTick);
    }

    public synchronized BlockPos lastAliveTargetPos() {
        return lastAliveTargetPos;
    }

    public synchronized boolean isAlive() {
        return lifeState.isAlive() && !connectionState.isLeft();
    }

    public synchronized boolean isOnlineAlive() {
        return lifeState.isAlive() && connectionState.isOnline();
    }

    public synchronized boolean canInteract() {
        return isOnlineAlive();
    }

    public synchronized void addPoints(double amount) {
        if (Double.isFinite(amount) && amount > 0.0D) {
            points = sanitizePoints(points + amount);
        }
    }

    public synchronized boolean spendPoints(double cost) {
        if (!isValidCost(cost) || points + 0.000001D < cost) {
            return false;
        }
        points = sanitizePoints(points - cost);
        return true;
    }

    public synchronized void refundPoints(double amount) {
        addPoints(amount);
    }

    public synchronized void setPoints(double points) {
        this.points = sanitizePoints(points);
    }

    public synchronized void addKill() {
        kills++;
    }

    public synchronized void addAssist() {
        assists++;
    }

    public synchronized void markAlive() {
        lifeState = ZombiesLifeState.ALIVE;
    }

    public synchronized void markDeadSpectating() {
        if (lifeState != ZombiesLifeState.DEAD_SPECTATING) {
            deaths++;
        }
        lifeState = ZombiesLifeState.DEAD_SPECTATING;
    }

    public synchronized void markOnline() {
        connectionState = ZombiesConnectionState.ONLINE;
        offlineSinceTick = null;
    }

    public synchronized void markOffline(long currentTick) {
        if (!connectionState.isLeft()) {
            connectionState = ZombiesConnectionState.OFFLINE;
            offlineSinceTick = Math.max(0L, currentTick);
        }
    }

    public synchronized void markLeft() {
        connectionState = ZombiesConnectionState.LEFT;
        offlineSinceTick = null;
    }

    public synchronized void updateLastAliveTargetPos(BlockPos pos) {
        if (lifeState.isAlive() && pos != null) {
            lastAliveTargetPos = pos;
        }
    }

    public synchronized Map<String, ModePlayerValue> toPlayerValues() {
        Map<String, ModePlayerValue> values = new LinkedHashMap<>();
        values.put("points", ModePlayerValue.ofInt(displayPoints()));
        values.put("kills", ModePlayerValue.ofInt(kills));
        values.put("assists", ModePlayerValue.ofInt(assists));
        values.put("deaths", ModePlayerValue.ofInt(deaths));
        values.put("life_state", ModePlayerValue.ofString(lifeState.name()));
        values.put("connection_state", ModePlayerValue.ofString(connectionState.name()));
        offlineSinceTick().ifPresent(tick -> values.put("offline_since_tick", ModePlayerValue.ofLong(tick)));
        return values;
    }

    public static boolean isValidCost(double cost) {
        return Double.isFinite(cost) && cost >= 0.0D;
    }

    private static double sanitizePoints(double points) {
        if (!Double.isFinite(points) || points <= 0.0D) {
            return 0.0D;
        }
        return points;
    }
}
