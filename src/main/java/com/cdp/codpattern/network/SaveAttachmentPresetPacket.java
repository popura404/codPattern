package com.cdp.codpattern.network;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.refit.service.AttachmentPresetSaveService;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

public class SaveAttachmentPresetPacket {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final int bagId;
    private final String slot;
    private final String presetPayload;
    private final String updatedGunNbtString;

    public SaveAttachmentPresetPacket(int bagId, String slot, String presetPayload, String updatedGunNbtString) {
        this.bagId = bagId;
        this.slot = slot;
        this.presetPayload = presetPayload;
        this.updatedGunNbtString = updatedGunNbtString;
    }

    public static void encode(SaveAttachmentPresetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.bagId);
        buffer.writeUtf(packet.slot);
        buffer.writeUtf(packet.presetPayload == null ? "" : packet.presetPayload);
        buffer.writeUtf(packet.updatedGunNbtString == null ? "" : packet.updatedGunNbtString);
    }

    public static SaveAttachmentPresetPacket decode(FriendlyByteBuf buffer) {
        return new SaveAttachmentPresetPacket(
                buffer.readInt(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf()
        );
    }

    public static void handle(SaveAttachmentPresetPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.server == null) {
                return;
            }

            AttachmentPresetSaveService.Result result = AttachmentPresetSaveService.save(
                    player,
                    packet.bagId,
                    packet.slot,
                    packet.presetPayload,
                    packet.updatedGunNbtString
            );

            if (result.playerData() != null) {
                ModNetworkChannel.sendToPlayer(new SyncBackpackConfigPacket(result.playerData()), player);
            }
            if (result.success()) {
                LOGGER.info("Attachment preset saved: player={} bagId={} slot={} bytes={}",
                        player.getGameProfile().getName(),
                        packet.bagId,
                        packet.slot,
                        result.payloadLength());
            } else if (result.userMessage() != null && !result.userMessage().getString().isBlank()) {
                player.sendSystemMessage(result.userMessage());
                LOGGER.warn("Attachment preset save failed: player={} bagId={} slot={} message={}",
                        player.getGameProfile().getName(),
                        packet.bagId,
                        packet.slot,
                        result.userMessage().getString());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
