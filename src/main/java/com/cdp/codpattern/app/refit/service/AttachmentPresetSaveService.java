package com.cdp.codpattern.app.refit.service;

import com.cdp.codpattern.app.backpack.service.BackpackAttachmentFilter;
import com.cdp.codpattern.compat.arcana.ArcanaRefitCompat;
import com.cdp.codpattern.config.ConfigFailureFeedback;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.AttachmentPresetUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
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
        return save(player, bagId, slot, ConfigPath.SERVERBACKPACK.getPath(player.server),
                ConfigPath.SERVER_FILTER.getPath(player.server), BackpackConfigRepository::save);
    }

    // Keep the final persistence step independently exercisable with real temporary-file IO.
    static Result save(ServerPlayer player, int bagId, String slot, Path backpackPath, Path filterPath, Runnable persist) {
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
            ItemStack gunStack = session.getRefitInventory() == null
                    ? player.getInventory().getItem(session.getEditHotbarSlot())
                    : session.getRefitInventory().getItem(session.getEditHotbarSlot());
            if (!TaczGatewayProvider.gateway().isGun(gunStack)) {
                throw new SaveFailureException("message.codpattern.refit.save_failed_not_gun");
            }
            if (!session.matchesGunIdentity(gunStack)
                    || player.getInventory().getItem(session.getEditHotbarSlot()) != gunStack) {
                throw new SaveFailureException("message.codpattern.refit.operation_wrong_gun");
            }

            String builtPresetPayload = AttachmentPresetUtil.buildPresetFromGun(gunStack).toString();
            String itemId = gunStack.getItem().builtInRegistryHolder().key().location().toString();
            String nbtString = gunStack.hasTag() ? gunStack.getTag().toString() : "";

            String uuid = player.getUUID().toString();
            playerData = BackpackConfigRepository.loadOrCreatePlayer(uuid, backpackPath);
            WeaponFilterConfig filterConfig = WeaponFilterConfigRepository.loadOrCreate(filterPath);
            BackpackConfig.Backpack backpack = playerData.getBackpacks_MAP().get(bagId);
            if (backpack == null) {
                throw new SaveFailureException("message.codpattern.refit.save_failed_backpack_missing");
            }
            if (BackpackAttachmentFilter.findFirstBlockedInstalledAttachmentId(filterConfig, gunStack).isPresent()) {
                throw new SaveFailureException("message.codpattern.refit.save_failed_attachment_blocked");
            }

            previousItem = backpack.getItem_MAP().get(slot);
            String normalizedPreset = builtPresetPayload.isBlank() ? null : builtPresetPayload;
            String preservedItemId = previousItem != null ? previousItem.getItem() : itemId;
            int preservedCount = previousItem != null ? Math.max(1, previousItem.getCount()) : 1;
            String preservedNbt = previousItem != null ? previousItem.getNbt() : nbtString;
            if (ArcanaRefitCompat.isLoaded() && previousItem != null) {
                CompoundTag configured = preservedNbt == null || preservedNbt.isBlank()
                        ? new CompoundTag() : TagParser.parseTag(preservedNbt);
                if (!session.getConfiguredGunId().equals(configured.getString("GunId"))
                        || !preservedItemId.equals(itemId)) {
                    throw new SaveFailureException("message.codpattern.refit.operation_wrong_gun");
                }
                // The session only accepts form changes derived from server-loaded rules.
                // Persist that one configuration field; ammunition and all other base NBT stay intact.
                if (!session.getExpectedGunId().equals(session.getConfiguredGunId())) {
                    configured.putString("GunId", session.getExpectedGunId());
                    preservedNbt = configured.toString();
                }
            }
            mutatedBackpack = backpack;
            backpack.getItem_MAP().put(slot,
                    new BackpackConfig.Backpack.ItemData(preservedItemId, preservedCount, preservedNbt, normalizedPreset));
            persist.run();

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
            Component message;
            if (e instanceof SaveFailureException saveFailureException) {
                message = Component.translatable(saveFailureException.translationKey);
                LogUtils.getLogger().warn("Attachment preset save rejected: player={} session={} bag={} slot={} reason={}",
                        player.getUUID(), session.getSessionId(), bagId, slot, saveFailureException.translationKey);
            } else {
                message = e instanceof BackpackConfigRepository.RepositoryException
                        || e instanceof WeaponFilterConfigRepository.RepositoryException
                        ? ConfigFailureFeedback.message(e)
                        : Component.translatable("message.codpattern.refit.save_failed_rollback");
                LogUtils.getLogger().error("Attachment preset save failed: player={} session={} bag={} slot={}",
                        player.getUUID(), session.getSessionId(), bagId, slot, e);
            }
            return new Result(false, message, playerData, 0);
        } finally {
            if (saved) {
                AttachmentEditSessionManager.endSession(player);
            } else {
                AttachmentEditSessionManager.abortSession(player, "save_failed", false);
            }
        }
    }
}
