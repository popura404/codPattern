package com.cdp.codpattern.app.tdm.model;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class DeathCamData {
    private final UUID victimId;
    private final UUID killerId;
    private final Vec3 deathPos;
    private final Vec3 killerPos;
    private final Vec3 cameraPos;
    private int remainingTicks;

    public DeathCamData(UUID victimId, UUID killerId, Vec3 deathPos, Vec3 killerPos, Vec3 cameraPos, int remainingTicks) {
        this.victimId = victimId;
        this.killerId = killerId;
        this.deathPos = deathPos;
        this.killerPos = killerPos;
        this.cameraPos = cameraPos;
        this.remainingTicks = remainingTicks;
    }

    public boolean tick() {
        remainingTicks--;
        return remainingTicks <= 0;
    }

    public UUID getVictimId() {
        return victimId;
    }

    public UUID getKillerId() {
        return killerId;
    }

    public Vec3 getDeathPos() {
        return deathPos;
    }

    public Vec3 getKillerPos() {
        return killerPos;
    }

    public Vec3 getCameraPos() {
        return cameraPos;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }
}
