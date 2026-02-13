package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.tdm.service.DeathCamService;
import com.cdp.codpattern.app.tdm.service.PlayerDeathService;
import com.cdp.codpattern.network.tdm.DeathCamPacket;
import com.cdp.codpattern.network.tdm.PhysicsMobRetainPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

final class CodTdmPlayerDeathHooks implements PlayerDeathService.Hooks {
    private final CodTdmPlayerDeathHooksPort port;
    private final CodTdmPlayerRuntimeState playerState;
    private final CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster;

    CodTdmPlayerDeathHooks(
            CodTdmPlayerDeathHooksPort port,
            CodTdmPlayerRuntimeState playerState,
            CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster
    ) {
        this.port = port;
        this.playerState = playerState;
        this.joinedPlayerBroadcaster = joinedPlayerBroadcaster;
    }

    @Override
    public void broadcastPhysicsSnapshot(PhysicsMobRetainPacket packet) {
        joinedPlayerBroadcaster.broadcastPacketToJoinedPlayers(packet);
    }

    @Override
    public void clearPlayerInventory(ServerPlayer player) {
        port.clearPlayerInventory(player);
    }

    @Override
    public void moveToDeathCam(ServerPlayer player, Vec3 cameraPosition, Vec3 lookAtPosition) {
        player.teleportTo(
                port.serverLevel(),
                cameraPosition.x,
                cameraPosition.y,
                cameraPosition.z,
                Set.of(),
                player.getYRot(),
                player.getXRot()
        );
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, lookAtPosition);
    }

    @Override
    public void sendDeathCamPacket(ServerPlayer player, DeathCamPacket packet) {
        ModNetworkChannel.sendToPlayer(packet, player);
    }

    @Override
    public void registerDeathCam(UUID playerId, UUID killerId, Vec3 deathPosition, Vec3 cameraPosition,
            int deathCamTicks) {
        DeathCamService.registerDeathCam(
                playerState.deathCamPlayers(),
                playerId,
                killerId,
                deathPosition,
                cameraPosition,
                deathCamTicks
        );
    }

    @Override
    public void scheduleRespawn(ServerPlayer player) {
        port.scheduleRespawn(player);
    }
}
