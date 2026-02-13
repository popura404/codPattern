package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.app.tdm.model.DeathCamData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DeathCamService {
    private DeathCamService() {
    }

    public static void tickDeathCam(Map<UUID, DeathCamData> deathCamPlayers, ServerLevel serverLevel) {
        Iterator<Map.Entry<UUID, DeathCamData>> iterator = deathCamPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DeathCamData> entry = iterator.next();
            if (entry.getValue().tick()) {
                iterator.remove();
            } else {
                UUID uuid = entry.getKey();
                DeathCamData data = entry.getValue();
                Player player = serverLevel.getPlayerByUUID(uuid);

                if (player instanceof ServerPlayer sp && sp.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                    Vec3 camPos = data.getCameraPos();
                    Vec3 deathPos = data.getDeathPos();

                    if (sp.position().distanceToSqr(camPos) > 0.01) {
                        sp.teleportTo(serverLevel, camPos.x, camPos.y, camPos.z, Set.of(), sp.getYRot(), sp.getXRot());
                    }
                    sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, deathPos);
                }
            }
        }
    }

    public static void registerDeathCam(Map<UUID, DeathCamData> deathCamPlayers, UUID playerId, UUID killerId,
            Vec3 deathPos, Vec3 cameraPos, int deathCamTicks) {
        if (deathCamTicks <= 0) {
            return;
        }
        DeathCamData camData = new DeathCamData(
                playerId,
                killerId,
                deathPos,
                deathPos,
                cameraPos,
                deathCamTicks
        );
        deathCamPlayers.put(playerId, camData);
    }
}
