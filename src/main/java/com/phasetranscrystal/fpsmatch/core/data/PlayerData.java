package com.phasetranscrystal.fpsmatch.core.data;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PlayerData {
    private final UUID owner;
    private final Component name;
    private final Map<UUID, Float> damageData = new HashMap<>();
    private boolean living = true;
    private SpawnPointData spawnPointsData;
    private SpawnPointData lastSpawnPoint;

    public PlayerData(Player owner) {
        this(owner.getUUID(), owner.getDisplayName());
    }

    public PlayerData(UUID owner, Component name) {
        this.owner = owner;
        this.name = name;
    }

    public Component name() {
        return name;
    }

    public Optional<ServerPlayer> getPlayer() {
        return FPSMCore.initialized()
                ? FPSMCore.getInstance().getPlayerByUUID(owner)
                : Optional.empty();
    }

    public boolean isOnline() {
        return getPlayer().isPresent();
    }

    public UUID getOwner() {
        return owner;
    }

    public void setSpawnPointsData(SpawnPointData spawnPointsData) {
        this.spawnPointsData = spawnPointsData;
    }

    public SpawnPointData getSpawnPointsData() {
        return spawnPointsData;
    }

    public void setLastSpawnPoint(SpawnPointData lastSpawnPoint) {
        this.lastSpawnPoint = lastSpawnPoint;
    }

    public SpawnPointData getLastSpawnPoint() {
        return lastSpawnPoint;
    }

    public void setLiving(boolean living) {
        this.living = living;
    }

    public boolean isLiving() {
        return living && isOnline();
    }

    public boolean isLivingNoOnlineCheck() {
        return living;
    }

    public Map<UUID, Float> getDamageData() {
        return damageData;
    }

    public void addDamageData(UUID hurt, float value) {
        damageData.merge(hurt, value, Float::sum);
    }

    public void clearDamageData() {
        damageData.clear();
    }

    public void save() {
        clearDamageData();
    }
}
