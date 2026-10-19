package com.cdp.codpattern.app.refit.service;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.backpack.service.BackpackAttachmentFilter;
import com.cdp.codpattern.compat.arcana.ArcanaRefitCompat;
import com.cdp.codpattern.config.ConfigFailureFeedback;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.core.refit.AttachmentEditSession;
import com.cdp.codpattern.core.refit.AttachmentEditSessionManager;
import com.cdp.codpattern.core.refit.RefitAction;
import com.cdp.codpattern.core.refit.RefitPreviewContext;
import com.cdp.codpattern.network.RefitOperationResultPacket;
import com.cdp.codpattern.network.SyncBackpackConfigPacket;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** All configuration actions execute on the server thread against its current session. */
public final class RefitActionService {
    private RefitActionService() { }

    public static RefitOperationResultPacket execute(ServerPlayer player, UUID sessionId, RefitAction action) {
        AttachmentEditSession session = AttachmentEditSessionManager.getSession(player.getUUID());
        if (session == null || !session.getSessionId().equals(sessionId)) {
            return new RefitOperationResultPacket(sessionId, false, true,
                    message("save_failed_session_missing"), ItemStack.EMPTY, -1);
        }
        if (action.kind() == RefitAction.Kind.CANCEL) {
            AttachmentEditSessionManager.abortSession(player, "cancelled", false);
            return new RefitOperationResultPacket(sessionId, true, true, Component.empty(), ItemStack.EMPTY, -1);
        }
        ItemStack gun = session.getRefitInventory().getSelected();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || !session.matchesGunIdentity(gun)
                || player.getInventory().getItem(session.getEditHotbarSlot()) != gun
                || (action.kind() != RefitAction.Kind.SAVE && action.gunSlot() != session.getEditHotbarSlot())) {
            if (action.kind() == RefitAction.Kind.SAVE) {
                AttachmentEditSessionManager.abortSession(player, "wrong_working_gun", false);
                return new RefitOperationResultPacket(sessionId, false, true,
                        message("operation_wrong_gun"), ItemStack.EMPTY, -1);
            }
            return result(session, false, "operation_wrong_gun");
        }
        if (action.kind() == RefitAction.Kind.SAVE) {
            var saved = AttachmentPresetSaveService.save(player, session.getBagId(), session.getSlot(), "", "");
            if (saved.success() && saved.playerData() != null) {
                ModNetworkChannel.sendToPlayer(new SyncBackpackConfigPacket(saved.playerData()), player);
            }
            return new RefitOperationResultPacket(sessionId, saved.success(), true,
                    saved.userMessage() == null ? Component.empty() : saved.userMessage(), ItemStack.EMPTY, -1);
        }

        try {
            if (action.kind() == RefitAction.Kind.LASER) {
                try (var ignored = RefitPreviewContext.open(player, gun)) {
                    for (var entry : action.laserColors().entrySet()) {
                        AttachmentType type = type(entry.getKey());
                        if (type == null || type == AttachmentType.NONE) continue;
                        ItemStack attachment = iGun.getAttachment(gun, type);
                        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachment);
                        if (iAttachment != null) {
                            iAttachment.setLaserColor(attachment, entry.getValue());
                            // getAttachment decodes an ItemStack copy; persist the changed color back into the gun.
                            iGun.installAttachment(gun, attachment);
                        }
                    }
                    if (action.applyGunColor()) {
                        iGun.setLaserColor(gun, action.gunColor());
                    }
                    AttachmentPropertyManager.postChangeEvent(player, gun);
                }
                player.inventoryMenu.broadcastChanges();
                return result(session, true, null);
            }

            AttachmentType type = type(action.attachmentType());
            if (type == null || type == AttachmentType.NONE || !iGun.allowAttachmentType(gun, type)) {
                return result(session, false, "operation_wrong_type");
            }
            ItemStack candidate = ItemStack.EMPTY;
            if (action.kind() == RefitAction.Kind.INSTALL) {
                candidate = session.getRefitInventory().getItem(action.candidateSlot());
                IAttachment attachment = IAttachment.getIAttachmentOrNull(candidate);
                if (attachment == null || attachment.getType(candidate) != type) {
                    return result(session, false, "operation_wrong_type");
                }
                var filter = WeaponFilterConfigRepository.getConfig();
                if (filter == null) filter = WeaponFilterConfigRepository.loadOrCreate(ConfigPath.SERVER_FILTER.getPath(player.server));
                if (BackpackAttachmentFilter.isAttachmentBlocked(filter, candidate)) {
                    return result(session, false, "save_failed_attachment_blocked");
                }
                if (!iGun.allowAttachment(gun, candidate)) return result(session, false, "operation_not_allowed");
            } else if (iGun.getAttachment(gun, type).isEmpty()) {
                return result(session, false, "operation_empty_slot");
            }

            if (ArcanaRefitCompat.isLoaded()) {
                boolean accepted = action.kind() == RefitAction.Kind.INSTALL
                        ? ArcanaRefitCompat.install(player, gun, candidate, action.candidateSlot(), type, session.getRefitInventory())
                        : ArcanaRefitCompat.unload(player, gun, type, session.getRefitInventory());
                if (!accepted) return result(session, false, "operation_not_allowed");
            } else {
                applyStandard(player, session, iGun, gun, candidate, type, action);
            }
            player.inventoryMenu.broadcastChanges();
            return result(session, true, null);
        } catch (RuntimeException | LinkageError failure) {
            LogUtils.getLogger().error("Attachment operation {} failed for session {}", action.kind(), sessionId, failure);
            // Never retry a partly executed operation through another backend.
            if (failure instanceof BackpackConfigRepository.RepositoryException
                    || failure instanceof WeaponFilterConfigRepository.RepositoryException) {
                return new RefitOperationResultPacket(session.getSessionId(), false, false,
                        ConfigFailureFeedback.message(failure), gun.copy(), session.getEditHotbarSlot());
            }
            return result(session, false, failure instanceof ArcanaRefitCompat.CompatibilityException
                    ? "operation_arcana_unavailable" : "operation_failed");
        }
    }

    private static void applyStandard(ServerPlayer player, AttachmentEditSession session, IGun iGun, ItemStack gun,
            ItemStack candidate, AttachmentType type, RefitAction action) {
        try (var ignored = RefitPreviewContext.open(player, gun)) {
            if (action.kind() == RefitAction.Kind.INSTALL) {
                ItemStack previous = iGun.getAttachment(gun, type);
                iGun.installAttachment(gun, candidate);
                session.getRefitInventory().setItem(action.candidateSlot(), previous);
            } else {
                session.getRefitInventory().add(iGun.getAttachment(gun, type));
                iGun.unloadAttachment(gun, type);
            }
            AttachmentPropertyManager.postChangeEvent(player, gun);
            if (type == AttachmentType.EXTENDED_MAG) iGun.dropAllAmmo(player, gun);
        }
    }

    private static AttachmentType type(String value) {
        try {
            return AttachmentType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return null;
        }
    }

    private static RefitOperationResultPacket result(AttachmentEditSession session, boolean success, String key) {
        return new RefitOperationResultPacket(session.getSessionId(), success, false,
                key == null ? Component.empty() : message(key), session.getRefitInventory().getSelected().copy(),
                session.getEditHotbarSlot());
    }

    private static Component message(String key) {
        return Component.translatable("message.codpattern.refit." + key);
    }
}
