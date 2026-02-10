package com.cdp.codpattern.fpsmatch.map;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 死亡视角数据
 * 用于追踪玩家死亡后的死亡视角状态
 */
public class DeathCamData {
    private final UUID victimId;
    private final UUID killerId;
    private final Vec3 deathPos;
    private final Vec3 killerPos;
    private final Vec3 cameraPos; // 摄像机位置 (死亡点后两格)
    private int remainingTicks;

    public DeathCamData(UUID victim, UUID killer, Vec3 deathPos, Vec3 killerPos, Vec3 cameraPos, int ticks) {
        this.victimId = victim;
        this.killerId = killer;
        this.deathPos = deathPos;
        this.killerPos = killerPos;
        this.cameraPos = cameraPos;
        this.remainingTicks = ticks;
    }

    /**
     * 每tick调用一次
     * 
     * @return true 表示死亡视角结束
     */
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
