package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.network.tdm.DeathCamPacket;
import com.cdp.codpattern.network.tdm.PhysicsMobRetainPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class PlayerDeathService {
    public interface Hooks {
        void broadcastPhysicsSnapshot(PhysicsMobRetainPacket packet);

        void clearPlayerInventory(ServerPlayer player);

        void moveToDeathCam(ServerPlayer player, Vec3 cameraPosition, Vec3 lookAtPosition);

        void sendDeathCamPacket(ServerPlayer player, DeathCamPacket packet);

        void registerDeathCam(UUID playerId, UUID killerId, Vec3 deathPosition, Vec3 cameraPosition, int deathCamTicks);

        void scheduleRespawn(ServerPlayer player);
    }

    private PlayerDeathService() {
    }

    public static void onPlayerDead(ServerPlayer player, ServerPlayer killer, CodTdmConfig config, Hooks hooks) {
        Vec3 deathPos = player.position();
        Vec3 deathVelocity = player.getDeltaMovement();

        PhysicsMobRetainPacket packet = new PhysicsMobRetainPacket(
                player.getId(),
                deathPos.x,
                deathPos.y,
                deathPos.z,
                player.getYRot(),
                player.getXRot(),
                player.getYHeadRot(),
                player.yBodyRot,
                deathVelocity.x,
                deathVelocity.y,
                deathVelocity.z
        );
        hooks.broadcastPhysicsSnapshot(packet);

        player.setGameMode(GameType.SPECTATOR);
        hooks.clearPlayerInventory(player);

        Vec3 look = player.getLookAngle();
        Vec3 camPos = deathPos.add(look.scale(-2.6)).add(0, 0.75, 0);
        hooks.moveToDeathCam(player, camPos, deathPos);

        int deathCamTicks = Math.max(0, config.getDeathCamTicks());
        int respawnDelayTicks = Math.max(1, config.getRespawnDelayTicks());
        boolean hasRealKiller = killer != null && !killer.getUUID().equals(player.getUUID());
        UUID killerId = hasRealKiller ? killer.getUUID() : player.getUUID();
        String killerName = hasRealKiller ? killer.getGameProfile().getName() : "";

        hooks.sendDeathCamPacket(player, new DeathCamPacket(killerId, killerName, deathCamTicks, respawnDelayTicks));
        hooks.registerDeathCam(player.getUUID(), killerId, deathPos, camPos, deathCamTicks);
        hooks.scheduleRespawn(player);
    }
}
