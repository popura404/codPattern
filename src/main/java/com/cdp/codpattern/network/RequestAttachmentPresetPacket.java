package com.cdp.codpattern.network;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.refit.service.AttachmentPresetRequestService;
import com.cdp.codpattern.config.ConfigFailureFeedback;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record RequestAttachmentPresetPacket(UUID sessionId, int bagId, String slot) {
    public static void encode(RequestAttachmentPresetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeInt(packet.bagId);
        buffer.writeUtf(packet.slot);
    }

    public static RequestAttachmentPresetPacket decode(FriendlyByteBuf buffer) {
        return new RequestAttachmentPresetPacket(buffer.readUUID(), buffer.readInt(), buffer.readUtf());
    }

    public static void handle(RequestAttachmentPresetPacket packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player == null) return;
            Component failureMessage = Component.translatable("message.codpattern.refit.open_failed");
            try {
                var result = AttachmentPresetRequestService.prepare(player, packet.sessionId, packet.bagId, packet.slot);
                if (result.isPresent()) {
                    ModNetworkChannel.sendToPlayer(result.get().packet(), player);
                    AttachmentEditSessionManager.syncVisibleAttachmentCandidates(player);
                    return;
                }
            } catch (BackpackConfigRepository.RepositoryException | WeaponFilterConfigRepository.RepositoryException failure) {
                failureMessage = ConfigFailureFeedback.message(failure);
                LogUtils.getLogger().error("Unable to read attachment session configuration: player={} bag={} slot={}",
                        player.getUUID(), packet.bagId, packet.slot, failure);
            } catch (RuntimeException failure) {
                LogUtils.getLogger().error("Unable to start attachment session for {}", player.getUUID(), failure);
            }
            var current = AttachmentEditSessionManager.getSession(player.getUUID());
            if (current != null && current.getSessionId().equals(packet.sessionId)) {
                AttachmentEditSessionManager.abortSession(player, "open_failed", false);
            }
            ModNetworkChannel.sendToPlayer(new RefitOperationResultPacket(packet.sessionId, false, true,
                    failureMessage, ItemStack.EMPTY, -1), player);
        });
        ctx.setPacketHandled(true);
    }
}
