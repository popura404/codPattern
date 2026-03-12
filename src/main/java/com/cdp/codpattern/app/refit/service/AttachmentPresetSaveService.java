package com.cdp.codpattern.app.refit.service;

import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.compat.taczaddon.TaczAddonRefitCompat;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.AttachmentPresetUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;

public final class AttachmentPresetSaveService {
    public record Result(boolean success,
            Component userMessage,
            BackpackConfig.PlayerBackpackData playerData,
            int payloadLength) {
    }

    private static final class SaveFailureException extends RuntimeException {
        private final String translationKey;

        private SaveFailureException(String translationKey) {
            super(translationKey);
            this.translationKey = translationKey;
        }
    }

    private AttachmentPresetSaveService() {
    }

    public static Result save(ServerPlayer player, int bagId, String slot, String presetPayload, String updatedGunNbtString) {
        AttachmentEditSession session = AttachmentEditSessionManager.getSession(player.getUUID());
        if (session == null) {
            return new Result(false, Component.translatable("message.codpattern.refit.save_failed_session_missing"), null,
                    0);
        }
        if (session.getBagId() != bagId || !session.getSlot().equals(slot)) {
            AttachmentEditSessionManager.abortSession(player, "session_mismatch");
            return new Result(false, Component.translatable("message.codpattern.refit.save_failed_session_mismatch"),
                    null, 0);
        }

        boolean saved = false;
        BackpackConfig.Backpack mutatedBackpack = null;
        BackpackConfig.Backpack.ItemData previousItem = null;
        BackpackConfig.PlayerBackpackData playerData = null;
        int finalPayloadLength = 0;

        try {
            ItemStack gunStack = player.getInventory().getItem(session.getEditHotbarSlot());
            if (!TaczGatewayProvider.gateway().isGun(gunStack)) {
                throw new SaveFailureException("message.codpattern.refit.save_failed_not_gun");
            }

            TaczAddonRefitCompat.sanitizeGunForBackpackRefitSession(gunStack);

            String builtPresetPayload = AttachmentPresetUtil.buildPresetFromGun(gunStack).toString();
            String itemId = gunStack.getItem().builtInRegistryHolder().key().location().toString();
            String nbtString = gunStack.hasTag() ? gunStack.getTag().toString() : "";

            String uuid = player.getUUID().toString();
            Path backpackPath = ConfigPath.SERVERBACKPACK.getPath(player.server);
            playerData = BackpackConfigRepository.loadOrCreatePlayer(uuid, backpackPath);
            BackpackConfig.Backpack backpack = playerData.getBackpacks_MAP().get(bagId);
            if (backpack == null) {
                throw new SaveFailureException("message.codpattern.refit.save_failed_backpack_missing");
            }

            mutatedBackpack = backpack;
            previousItem = backpack.getItem_MAP().get(slot);
            String normalizedPreset = builtPresetPayload.isBlank() ? null : builtPresetPayload;
            backpack.getItem_MAP().put(slot,
                    new BackpackConfig.Backpack.ItemData(itemId, 1, nbtString, normalizedPreset));
            BackpackConfigRepository.save();

            saved = true;
            finalPayloadLength = builtPresetPayload.length();
            return new Result(true, Component.empty(), playerData, finalPayloadLength);
        } catch (Exception e) {
            if (!saved && mutatedBackpack != null) {
                if (previousItem == null) {
                    mutatedBackpack.getItem_MAP().remove(slot);
                } else {
                    mutatedBackpack.getItem_MAP().put(slot, previousItem);
                }
            }
            Component message = e instanceof SaveFailureException saveFailureException
                    ? Component.translatable(saveFailureException.translationKey)
                    : Component.translatable("message.codpattern.refit.save_failed_rollback");
            return new Result(false, message, playerData, 0);
        } finally {
            if (saved) {
                AttachmentEditSessionManager.endSession(player);
            } else {
                AttachmentEditSessionManager.abortSession(player, "save_failed");
            }
        }
    }
}
