package com.cdp.codpattern.network;

import com.cdp.codpattern.config.AttachmentPreset.AttachmentPresetManager;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfig;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfigManager;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.AttachmentPresetUtil;
import com.cdp.codpattern.network.handler.PacketHandler;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IGun;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
            AttachmentEditSession session = AttachmentEditSessionManager.getSession(player.getUUID());
            if (session == null) {
                player.sendSystemMessage(Component.literal("§c配件保存失败：未找到改装会话"));
                return;
            }
            if (session.getBagId() != packet.bagId || !session.getSlot().equals(packet.slot)) {
                player.sendSystemMessage(Component.literal("§c配件保存失败：改装会话不匹配"));
                return;
            }

            ItemStack gunStack = player.getInventory().getItem(session.getEditHotbarSlot());
            if (!(gunStack.getItem() instanceof IGun)) {
                player.sendSystemMessage(Component.literal("§c配件保存失败：当前槽位不是枪械"));
                AttachmentEditSessionManager.endSession(player);
                return;
            }

            String presetPayload = AttachmentPresetUtil.buildPresetFromGun(gunStack).toString();
            AttachmentPresetManager.writePreset(player.server, player.getUUID(), packet.bagId, packet.slot, presetPayload);

            String itemId = gunStack.getItem().builtInRegistryHolder().key().location().toString();
            String nbtString = gunStack.hasTag() ? gunStack.getTag().toString() : "";

            String uuid = player.getUUID().toString();
            BackpackConfig.PlayerBackpackData playerData =
                    BackpackConfigManager.getConfig().getOrCreatePlayerData(uuid);
            BackpackConfig.Backpack backpack = playerData.getBackpacks_MAP().get(packet.bagId);
            if (backpack != null) {
                backpack.getItem_MAP().put(packet.slot,
                        new BackpackConfig.Backpack.ItemData(itemId, 1, nbtString));
                BackpackConfigManager.save();
                PacketHandler.sendToPlayer(new SyncBackpackConfigPacket(playerData), player);
            }

            AttachmentEditSessionManager.endSession(player);
            LOGGER.info("Attachment preset saved: player={} bagId={} slot={} bytes={}",
                    player.getGameProfile().getName(), packet.bagId, packet.slot, presetPayload.length());
        });
        ctx.get().setPacketHandled(true);
    }
}
