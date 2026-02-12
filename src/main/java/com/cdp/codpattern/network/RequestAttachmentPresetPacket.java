package com.cdp.codpattern.network;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.refit.service.AttachmentPresetRequestService;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

public class RequestAttachmentPresetPacket {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final int bagId;
    private final String slot;

    public RequestAttachmentPresetPacket(int bagId, String slot) {
        this.bagId = bagId;
        this.slot = slot;
    }

    public static void encode(RequestAttachmentPresetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.bagId);
        buffer.writeUtf(packet.slot);
    }

    public static RequestAttachmentPresetPacket decode(FriendlyByteBuf buffer) {
        return new RequestAttachmentPresetPacket(buffer.readInt(), buffer.readUtf());
    }

    public static void handle(RequestAttachmentPresetPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            AttachmentPresetRequestService.prepare(player, packet.bagId, packet.slot).ifPresent(result -> {
                ModNetworkChannel.sendToPlayer(result.packet(), player);
                LOGGER.info("Attachment preset request: player={} bagId={} slot={} presetLoaded={} candidates={} truncated={}",
                        player.getGameProfile().getName(),
                        packet.bagId,
                        packet.slot,
                        result.presetLoaded(),
                        result.sandboxAttachmentCount(),
                        result.truncatedAttachmentCount());
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
