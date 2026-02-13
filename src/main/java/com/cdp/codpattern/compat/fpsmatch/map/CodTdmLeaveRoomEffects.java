package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.tdm.DeathCamPacket;
import com.cdp.codpattern.network.tdm.GamePhasePacket;
import com.cdp.codpattern.network.tdm.ScoreUpdatePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.function.Supplier;

final class CodTdmLeaveRoomEffects {
    private final ServerLevel serverLevel;
    private final Supplier<String> mapNameSupplier;

    CodTdmLeaveRoomEffects(ServerLevel serverLevel, Supplier<String> mapNameSupplier) {
        this.serverLevel = serverLevel;
        this.mapNameSupplier = mapNameSupplier;
    }

    void handleTeleportResult(ServerPlayer player, boolean teleported) {
        if (!teleported) {
            player.sendSystemMessage(Component.translatable(
                    "message.codpattern.game.warning_no_end_teleport",
                    mapNameSupplier.get()));
            return;
        }

        serverLevel.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS,
                0.8f,
                1.0f
        );
    }

    void sendLeaveStatePackets(ServerPlayer player) {
        ModNetworkChannel.sendToPlayer(new GamePhasePacket(TdmGamePhase.WAITING.name(), 0), player);
        ModNetworkChannel.sendToPlayer(DeathCamPacket.clear(), player);
        ModNetworkChannel.sendToPlayer(new ScoreUpdatePacket(0, 0, 0), player);
    }
}
