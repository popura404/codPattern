package com.cdp.codpattern.network;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.refit.service.AttachmentPresetRequestService;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestAttachmentPresetPacket {
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
                ModNetworkChannel.sendToPlayer(new SyncAttachmentCandidatesPacket(
                        packet.bagId,
                        packet.slot,
                        AttachmentEditSessionManager.snapshotAttachmentCandidates(player)), player);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
