package com.cdp.codpattern.core.refit;

import com.mojang.logging.LogUtils;
import com.cdp.codpattern.app.backpack.service.BackpackAttachmentFilter;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.network.SyncAttachmentCandidatesPacket;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.RefitOperationResultPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AttachmentEditSessionManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, AttachmentEditSession> SESSIONS = new ConcurrentHashMap<>();

    public static AttachmentEditSession startSession(ServerPlayer player, int bagId, String slot, ItemStack gunStack,
            List<ItemStack> attachmentCandidates) {
        return startSession(player, UUID.randomUUID(), bagId, slot, gunStack, attachmentCandidates);
    }

    public static AttachmentEditSession startSession(ServerPlayer player, UUID sessionId, int bagId, String slot,
            ItemStack gunStack, List<ItemStack> attachmentCandidates) {
        return startSession(player, sessionId, bagId, slot, gunStack, attachmentCandidates,
                com.cdp.codpattern.compat.tacz.TaczGatewayProvider.gateway().resolveRefitGunId(gunStack).orElse(""));
    }

    public static AttachmentEditSession startSession(ServerPlayer player, UUID sessionId, int bagId, String slot,
            ItemStack gunStack, List<ItemStack> attachmentCandidates, String configuredGunId) {
        abortSession(player, "replace_session");
        Inventory inventory = player.getInventory();
        int originalSelected = inventory.selected;
        int editSlot = Math.max(0, Math.min(8, originalSelected));
        List<ItemStack> snapshot = snapshotInventory(inventory);

        inventory.clearContent();
        inventory.setItem(editSlot, gunStack.copy());

        // Only the working gun needs a real held slot for TaCZ rendering/state.
        // Candidates live exclusively in the list-backed adapter below, never in
        // the player's bounded inventory.
        inventory.selected = editSlot;
        player.connection.send(new ClientboundSetCarriedItemPacket(editSlot));
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());

        AttachmentRefitInventory refitInventory = new AttachmentRefitInventory(
                player,
                editSlot,
                inventory.getItem(editSlot),
                new ArrayList<>(snapshotAttachments(attachmentCandidates)));
        AttachmentEditSession session = new AttachmentEditSession(
                sessionId,
                bagId,
                slot,
                editSlot,
                originalSelected,
                snapshot,
                refitInventory, configuredGunId);
        SESSIONS.put(player.getUUID(), session);
        BackpackRefitSessionContext.markServerActive(player.getUUID());
        LOGGER.info("Attachment edit session started: player={} bagId={} slot={} hotbar={} candidates={}",
                player.getGameProfile().getName(), bagId, slot, editSlot, refitInventory.getCandidateCount());
        return session;
    }

    public static AttachmentEditSession getSession(UUID playerId) {
        return SESSIONS.get(playerId);
    }

    public static boolean hasSession(UUID playerId) {
        return SESSIONS.containsKey(playerId);
    }

    public static Inventory getRefitInventory(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        AttachmentEditSession session = SESSIONS.get(player.getUUID());
        return session == null ? null : session.getRefitInventory();
    }

    public static List<ItemStack> snapshotAttachmentCandidates(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        AttachmentEditSession session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return List.of();
        }

        return session.getRefitInventory() == null
                ? List.of() : session.getRefitInventory().snapshotCandidates();
    }

    /** Hidden candidates occupy their original empty slot; the server catalog remains intact. */
    public static List<ItemStack> snapshotVisibleAttachmentCandidates(ServerPlayer player, WeaponFilterConfig filter) {
        List<ItemStack> candidates = snapshotAttachmentCandidates(player);
        List<ItemStack> visible = new ArrayList<>(candidates.size());
        for (ItemStack candidate : candidates) {
            visible.add(BackpackAttachmentFilter.isAttachmentBlocked(filter, candidate) ? ItemStack.EMPTY : candidate);
        }
        return visible;
    }

    public static void syncVisibleAttachmentCandidates(ServerPlayer player) {
        syncVisibleAttachmentCandidates(player, WeaponFilterConfigRepository.getConfig());
    }

    public static void syncVisibleAttachmentCandidates(ServerPlayer player, WeaponFilterConfig filter) {
        AttachmentEditSession session = SESSIONS.get(player.getUUID());
        if (session != null) {
            SyncAttachmentCandidatesPacket.sendTo(player, session.getSessionId(), session.getBagId(), session.getSlot(),
                    snapshotVisibleAttachmentCandidates(player, filter));
        }
    }

    public static void endSession(ServerPlayer player) {
        endSessionInternal(player, false, "completed", false);
    }

    public static void abortSession(ServerPlayer player, String reason) {
        abortSession(player, reason, true);
    }

    public static void abortSession(ServerPlayer player, String reason, boolean notifyPlayer) {
        endSessionInternal(player, true, reason == null ? "aborted" : reason, notifyPlayer);
    }

    private static void endSessionInternal(ServerPlayer player, boolean abnormal, String reason, boolean notifyPlayer) {
        AttachmentEditSession session = SESSIONS.remove(player.getUUID());
        BackpackRefitSessionContext.clearServerActive(player.getUUID());
        if (session == null) {
            return;
        }

        restoreInventory(player.getInventory(), session.getInventorySnapshot());
        int selected = Math.max(0, Math.min(8, session.getOriginalSelectedSlot()));
        player.getInventory().selected = selected;
        player.connection.send(new ClientboundSetCarriedItemPacket(selected));
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());

        if (abnormal && notifyPlayer) {
            ModNetworkChannel.sendToPlayer(new RefitOperationResultPacket(session.getSessionId(), false, true,
                    Component.translatable("message.codpattern.refit.session_rollback", reason), ItemStack.EMPTY,
                    session.getEditHotbarSlot()), player);
        }
        LOGGER.info("Attachment edit session ended: player={} bagId={} slot={} reason={} abnormal={}",
                player.getGameProfile().getName(), session.getBagId(), session.getSlot(), reason, abnormal);
    }

    public static void abortAllSessions(MinecraftServer server) {
        for (UUID playerId : new ArrayList<>(SESSIONS.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                abortSession(player, "server_stopping", false);
            } else {
                SESSIONS.remove(playerId);
                BackpackRefitSessionContext.clearServerActive(playerId);
            }
        }
    }

    private static List<ItemStack> snapshotInventory(Inventory inventory) {
        List<ItemStack> snapshot = new ArrayList<>(inventory.getContainerSize());
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            snapshot.add(inventory.getItem(i).copy());
        }
        return snapshot;
    }

    private static List<ItemStack> snapshotAttachments(List<ItemStack> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        List<ItemStack> snapshot = new ArrayList<>(attachments.size());
        for (ItemStack attachment : attachments) {
            snapshot.add(attachment == null ? ItemStack.EMPTY : attachment.copy());
        }
        return snapshot;
    }

    private static void restoreInventory(Inventory inventory, List<ItemStack> snapshot) {
        int restoreSize = Math.min(inventory.getContainerSize(), snapshot.size());
        for (int i = 0; i < restoreSize; i++) {
            inventory.setItem(i, snapshot.get(i).copy());
        }
        for (int i = restoreSize; i < inventory.getContainerSize(); i++) {
            inventory.setItem(i, ItemStack.EMPTY);
        }
    }
}
