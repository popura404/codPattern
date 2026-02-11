package com.cdp.codpattern.network;

import com.cdp.codpattern.config.AttachmentPreset.AttachmentPresetManager;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfig;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfigManager;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.AttachmentPresetUtil;
import com.cdp.codpattern.network.handler.PacketHandler;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
            if (player == null || player.server == null) {
                return;
            }
            if (player.isSpectator()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c旁观模式下无法进入改装"));
                return;
            }
            if (!"primary".equals(packet.slot) && !"secondary".equals(packet.slot)) {
                return;
            }
            String uuid = player.getUUID().toString();
            BackpackConfig.PlayerBackpackData playerData =
                    BackpackConfigManager.getConfig().getOrCreatePlayerData(uuid);
            BackpackConfig.Backpack backpack = playerData.getBackpacks_MAP().get(packet.bagId);
            if (backpack == null) {
                return;
            }
            BackpackConfig.Backpack.ItemData itemData = backpack.getItem_MAP().get(packet.slot);
            if (itemData == null) {
                return;
            }

            ItemStack gunStack = buildItemStack(itemData);
            IGun iGun = IGun.getIGunOrNull(gunStack);
            if (iGun == null) {
                return;
            }
            if (TimelessAPI.getCommonGunIndex(iGun.getGunId(gunStack)).isEmpty()) {
                return;
            }

            UUID playerId = player.getUUID();
            Optional<String> presetPayload = AttachmentPresetManager.readPreset(player.server, playerId, packet.bagId, packet.slot);
            CompoundTag presetTag = presetPayload.map(AttachmentPresetUtil::parsePresetString).orElseGet(CompoundTag::new);
            if (!presetTag.isEmpty()) {
                AttachmentPresetUtil.applyPresetToGun(gunStack, presetTag);
                AttachmentPropertyManager.postChangeEvent(player, gunStack);
            }

            List<ItemStack> playerOwnedAttachments = collectPlayerOwnedAttachments(player, gunStack, iGun);
            AttachmentEditSession session = AttachmentEditSessionManager.startSession(
                    player, packet.bagId, packet.slot, gunStack, playerOwnedAttachments);
            String expectedGunId = iGun.getGunId(gunStack).toString();
            PacketHandler.sendToPlayer(new SyncAttachmentPresetPacket(packet.bagId, packet.slot,
                    presetPayload.orElse(""), expectedGunId), player);
            LOGGER.info("Attachment preset request: player={} bagId={} slot={} presetLoaded={} candidates={} truncated={}",
                    player.getGameProfile().getName(), packet.bagId, packet.slot, presetPayload.isPresent(),
                    session.getSandboxAttachmentCount(), session.getTruncatedAttachmentCount());
        });
        ctx.get().setPacketHandled(true);
    }

    private static List<ItemStack> collectPlayerOwnedAttachments(ServerPlayer player, ItemStack gunStack, IGun iGun) {
        List<ItemStack> attachments = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (iGun.allowAttachment(gunStack, stack)) {
                attachments.add(stack.copy());
            }
        }
        return attachments;
    }

    private static ItemStack buildItemStack(BackpackConfig.Backpack.ItemData itemData) {
        try {
            ResourceLocation itemId = new ResourceLocation(itemData.getItem());
            Item item = BuiltInRegistries.ITEM.get(itemId);
            ItemStack stack = new ItemStack(item, itemData.getCount());
            String nbt = itemData.getNbt();
            if (nbt != null && !nbt.isEmpty()) {
                stack.setTag(TagParser.parseTag(nbt));
            }
            return stack;
        } catch (Exception e) {
            LOGGER.warn("Failed to build ItemStack for {}", itemData.getItem(), e);
            return ItemStack.EMPTY;
        }
    }
}
