package com.cdp.codpattern.app.refit.service;

import com.cdp.codpattern.config.AttachmentPreset.AttachmentPresetManager;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.AttachmentPresetUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;

public final class AttachmentPresetSaveService {
    public record Result(boolean success,
            String userMessage,
            BackpackConfig.PlayerBackpackData playerData,
            int payloadLength) {
    }

    private AttachmentPresetSaveService() {
    }

    public static Result save(ServerPlayer player, int bagId, String slot, String presetPayload, String updatedGunNbtString) {
        AttachmentEditSession session = AttachmentEditSessionManager.getSession(player.getUUID());
        if (session == null) {
            return new Result(false, "§c配件保存失败：未找到改装会话", null, 0);
        }
        if (session.getBagId() != bagId || !session.getSlot().equals(slot)) {
            AttachmentEditSessionManager.abortSession(player, "session_mismatch");
            return new Result(false, "§c配件保存失败：改装会话不匹配", null, 0);
        }

        boolean saved = false;
        BackpackConfig.Backpack mutatedBackpack = null;
        BackpackConfig.Backpack.ItemData previousItem = null;
        BackpackConfig.PlayerBackpackData playerData = null;
        int finalPayloadLength = 0;

        try {
            ItemStack gunStack = player.getInventory().getItem(session.getEditHotbarSlot());
            if (!TaczGatewayProvider.gateway().isGun(gunStack)) {
                throw new IllegalStateException("当前槽位不是枪械");
            }

            String builtPresetPayload = AttachmentPresetUtil.buildPresetFromGun(gunStack).toString();
            String itemId = gunStack.getItem().builtInRegistryHolder().key().location().toString();
            String nbtString = gunStack.hasTag() ? gunStack.getTag().toString() : "";

            String uuid = player.getUUID().toString();
            Path backpackPath = ConfigPath.SERVERBACKPACK.getPath(player.server);
            playerData = BackpackConfigRepository.loadOrCreatePlayer(uuid, backpackPath);
            BackpackConfig.Backpack backpack = playerData.getBackpacks_MAP().get(bagId);
            if (backpack == null) {
                throw new IllegalStateException("目标背包不存在");
            }

            AttachmentPresetManager.writePreset(player.server, player.getUUID(), bagId, slot, builtPresetPayload);

            mutatedBackpack = backpack;
            previousItem = backpack.getItem_MAP().get(slot);
            backpack.getItem_MAP().put(slot, new BackpackConfig.Backpack.ItemData(itemId, 1, nbtString));
            BackpackConfigRepository.save();

            saved = true;
            finalPayloadLength = builtPresetPayload.length();
            return new Result(true, "", playerData, finalPayloadLength);
        } catch (Exception e) {
            if (!saved && mutatedBackpack != null) {
                if (previousItem == null) {
                    mutatedBackpack.getItem_MAP().remove(slot);
                } else {
                    mutatedBackpack.getItem_MAP().put(slot, previousItem);
                }
            }
            return new Result(false, "§c配件保存失败，已回滚：" + e.getMessage(), playerData, 0);
        } finally {
            if (saved) {
                AttachmentEditSessionManager.endSession(player);
            } else {
                AttachmentEditSessionManager.abortSession(player, "save_failed");
            }
        }
    }
}
