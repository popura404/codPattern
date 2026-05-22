package com.cdp.codpattern.app.zombies.model;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.zombies.sync.ZombiesRuntimeStateKeys;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Per-match, non-persistent zombies player runtime state.
 */
public class ZombiesPlayerRuntimeState {
    private final UUID playerId;
    private double points;
    private double totalEarnedPoints;
    private int kills;
    private int assists;
    private int deaths;
    private int barriersOpened;
    private ZombiesLifeState lifeState;
    private ZombiesConnectionState connectionState;
    private Long offlineSinceTick;
    private BlockPos lastAliveTargetPos;
    private ZombiesWeaponInstanceState starterWeapon;
    private ZombiesWeaponInstanceState primaryWeapon;
    private ZombiesArmorState armor;
    private ZombiesEquipmentSnapshot deathEquipmentSnapshot;
    private final Map<ZombiesBuffType, ZombiesBuffState> buffs = new EnumMap<>(ZombiesBuffType.class);

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
        this(
                playerId,
                points,
                kills,
                assists,
                deaths,
                lifeState,
                connectionState,
                offlineSinceTick,
                lastAliveTargetPos,
                null,
                null);
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
            BlockPos lastAliveTargetPos,
            ZombiesWeaponInstanceState primaryWeapon,
            ZombiesArmorState armor
    ) {
        this(
                playerId,
                points,
                points,
                kills,
                assists,
                deaths,
                0,
                lifeState,
                connectionState,
                offlineSinceTick,
                lastAliveTargetPos,
                primaryWeapon,
                armor);
    }

    public ZombiesPlayerRuntimeState(
            UUID playerId,
            double points,
            double totalEarnedPoints,
            int kills,
            int assists,
            int deaths,
            int barriersOpened,
            ZombiesLifeState lifeState,
            ZombiesConnectionState connectionState,
            Long offlineSinceTick,
            BlockPos lastAliveTargetPos,
            ZombiesWeaponInstanceState primaryWeapon,
            ZombiesArmorState armor
    ) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.points = sanitizePoints(points);
        this.totalEarnedPoints = Math.max(this.points, sanitizePoints(totalEarnedPoints));
        this.kills = Math.max(0, kills);
        this.assists = Math.max(0, assists);
        this.deaths = Math.max(0, deaths);
        this.barriersOpened = Math.max(0, barriersOpened);
        this.lifeState = lifeState == null ? ZombiesLifeState.ALIVE : lifeState;
        this.connectionState = connectionState == null ? ZombiesConnectionState.ONLINE : connectionState;
        this.offlineSinceTick = offlineSinceTick;
        this.lastAliveTargetPos = lastAliveTargetPos;
        this.starterWeapon = null;
        this.primaryWeapon = primaryWeapon;
        this.armor = armor;
        this.deathEquipmentSnapshot = null;
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

    public synchronized double totalEarnedPoints() {
        return totalEarnedPoints;
    }

    public synchronized int displayTotalEarnedPoints() {
        return (int) Math.floor(totalEarnedPoints);
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

    public synchronized int barriersOpened() {
        return barriersOpened;
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

    public synchronized Optional<ZombiesWeaponInstanceState> starterWeapon() {
        return Optional.ofNullable(starterWeapon);
    }

    public synchronized Optional<ZombiesWeaponInstanceState> primaryWeapon() {
        return Optional.ofNullable(primaryWeapon);
    }

    public synchronized Optional<ZombiesArmorState> armor() {
        return Optional.ofNullable(armor);
    }

    public synchronized Optional<ZombiesEquipmentSnapshot> deathEquipmentSnapshot() {
        return Optional.ofNullable(deathEquipmentSnapshot);
    }

    public synchronized Map<ZombiesBuffType, ZombiesBuffState> buffs() {
        return Collections.unmodifiableMap(new EnumMap<>(buffs));
    }

    public synchronized Optional<ZombiesBuffState> buff(ZombiesBuffType type) {
        return Optional.ofNullable(type == null ? null : buffs.get(type));
    }

    public synchronized boolean hasBuff(ZombiesBuffType type) {
        return type != null && buffs.containsKey(type);
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
        addPoints(amount, true);
    }

    public synchronized void addPoints(double amount, boolean countAsEarned) {
        if (Double.isFinite(amount) && amount > 0.0D) {
            points = sanitizePoints(points + amount);
            if (countAsEarned) {
                totalEarnedPoints = sanitizePoints(totalEarnedPoints + amount);
            }
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
        addPoints(amount, false);
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

    public synchronized void addBarrierOpened() {
        barriersOpened++;
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

    public synchronized void setStarterWeapon(ZombiesWeaponInstanceState starterWeapon) {
        this.starterWeapon = starterWeapon;
    }

    public synchronized void clearStarterWeapon() {
        starterWeapon = null;
    }

    public synchronized void setPrimaryWeapon(ZombiesWeaponInstanceState primaryWeapon) {
        this.primaryWeapon = primaryWeapon;
    }

    public synchronized void clearPrimaryWeapon() {
        primaryWeapon = null;
    }

    public synchronized void setArmor(ZombiesArmorState armor) {
        this.armor = armor;
    }

    public synchronized void clearArmor() {
        armor = null;
    }

    public synchronized void setDeathEquipmentSnapshot(ZombiesEquipmentSnapshot deathEquipmentSnapshot) {
        this.deathEquipmentSnapshot = deathEquipmentSnapshot == null || deathEquipmentSnapshot.isEmpty()
                ? null
                : deathEquipmentSnapshot;
    }

    public synchronized void clearDeathEquipmentSnapshot() {
        deathEquipmentSnapshot = null;
    }

    public synchronized void addBuff(ZombiesBuffState buff) {
        if (buff != null) {
            buffs.put(buff.type(), buff);
        }
    }

    public synchronized void removeBuff(ZombiesBuffType type) {
        if (type != null) {
            buffs.remove(type);
        }
    }

    public synchronized void clearBuffs() {
        buffs.clear();
    }

    public synchronized Map<String, ModePlayerValue> toPlayerValues() {
        Map<String, ModePlayerValue> values = new LinkedHashMap<>();
        values.put(ZombiesRuntimeStateKeys.PLAYER_POINTS, ModePlayerValue.ofInt(displayPoints()));
        values.put(ZombiesRuntimeStateKeys.PLAYER_TOTAL_EARNED_POINTS,
                ModePlayerValue.ofInt(displayTotalEarnedPoints()));
        values.put(ZombiesRuntimeStateKeys.PLAYER_KILLS, ModePlayerValue.ofInt(kills));
        values.put(ZombiesRuntimeStateKeys.PLAYER_ASSISTS, ModePlayerValue.ofInt(assists));
        values.put(ZombiesRuntimeStateKeys.PLAYER_DEATHS, ModePlayerValue.ofInt(deaths));
        values.put(ZombiesRuntimeStateKeys.PLAYER_BARRIERS_OPENED, ModePlayerValue.ofInt(barriersOpened));
        values.put(ZombiesRuntimeStateKeys.PLAYER_LIFE_STATE, ModePlayerValue.ofString(lifeState.name()));
        values.put(ZombiesRuntimeStateKeys.PLAYER_CONNECTION_STATE, ModePlayerValue.ofString(connectionState.name()));
        values.put(ZombiesRuntimeStateKeys.PLAYER_WEAPON_PRIMARY_LEVEL,
                ModePlayerValue.ofInt(primaryWeapon == null ? 0 : primaryWeapon.weaponLevel()));
        values.put(ZombiesRuntimeStateKeys.PLAYER_WEAPON_PRIMARY_UPGRADE,
                ModePlayerValue.ofInt(primaryWeapon == null ? 0 : primaryWeapon.upgradeLevel()));
        values.put(ZombiesRuntimeStateKeys.PLAYER_ARMOR_LEVEL,
                ModePlayerValue.ofInt(armor == null ? 0 : armor.armorLevel()));
        for (ZombiesBuffType buffType : ZombiesBuffType.values()) {
            values.put(ZombiesRuntimeStateKeys.playerBuff(buffType.id()),
                    ModePlayerValue.ofBoolean(buffs.containsKey(buffType)));
        }
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
