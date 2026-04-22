package com.cdp.codpattern.core.refit;

import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.mojang.logging.LogUtils;
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
    private static final long SESSION_TIMEOUT_MS = 120_000L;
    private static final int SANDBOX_MAIN_INVENTORY_SIZE = 36;
    private static final int SANDBOX_RESERVED_EMPTY_SLOTS = 1;

    public static AttachmentEditSession startSession(ServerPlayer player, int bagId, String slot, ItemStack gunStack,
            List<ItemStack> sandboxAttachments) {
        abortSession(player, "replace_session");
        Inventory inventory = player.getInventory();
        int originalSelected = inventory.selected;
        int editSlot = Math.max(0, Math.min(8, originalSelected));
        List<ItemStack> snapshot = snapshotInventory(inventory);

        inventory.clearContent();
        inventory.setItem(editSlot, gunStack.copy());

        int insertedAttachments = 0;
        int truncatedAttachments = 0;
        int writeCursor = 0;
        int maxWritableSlotExclusive = Math.max(0, SANDBOX_MAIN_INVENTORY_SIZE - SANDBOX_RESERVED_EMPTY_SLOTS);
        for (ItemStack attachment : sandboxAttachments) {
            if (attachment == null || attachment.isEmpty()) {
                continue;
            }
            // Keep at least one empty slot so TaCZ unload action can always proceed.
            while (writeCursor < maxWritableSlotExclusive && writeCursor == editSlot) {
                writeCursor++;
            }
            if (writeCursor >= maxWritableSlotExclusive) {
                truncatedAttachments++;
                continue;
            }
            inventory.setItem(writeCursor, attachment.copy());
            insertedAttachments++;
            writeCursor++;
        }

        inventory.selected = editSlot;
        player.connection.send(new ClientboundSetCarriedItemPacket(editSlot));
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());

        AttachmentRefitInventory refitInventory = new AttachmentRefitInventory(
                player,
                editSlot,
                inventory.getItem(editSlot),
                new ArrayList<>(snapshotAttachments(sandboxAttachments)));
        long now = System.currentTimeMillis();
        AttachmentEditSession session = new AttachmentEditSession(
                bagId,
                slot,
                editSlot,
                originalSelected,
                snapshot,
                refitInventory,
                now,
                now + SESSION_TIMEOUT_MS,
                insertedAttachments,
                truncatedAttachments);
        SESSIONS.put(player.getUUID(), session);
        BackpackRefitSessionContext.markServerActive(player.getUUID());
        LOGGER.info("Attachment edit session started: player={} bagId={} slot={} hotbar={} attachments={} truncated={}",
                player.getGameProfile().getName(), bagId, slot, editSlot, insertedAttachments, truncatedAttachments);
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

        List<ItemStack> snapshot = new ArrayList<>();
        Inventory inventory = session.getRefitInventory();
        if (inventory == null) {
            return List.of();
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot == session.getEditHotbarSlot()) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (TaczGatewayProvider.gateway().resolveAttachmentId(stack).isEmpty()) {
                continue;
            }
            snapshot.add(stack.copy());
        }
        return snapshot;
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
            player.sendSystemMessage(Component.translatable("message.codpattern.refit.session_rollback", reason));
        }
        LOGGER.info("Attachment edit session ended: player={} bagId={} slot={} reason={} abnormal={}",
                player.getGameProfile().getName(), session.getBagId(), session.getSlot(), reason, abnormal);
    }

    public static void tickTimeouts(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<UUID> playerIds = new ArrayList<>(SESSIONS.keySet());
        for (UUID playerId : playerIds) {
            AttachmentEditSession session = SESSIONS.get(playerId);
            if (session == null || !session.isExpired(now)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                abortSession(player, "timeout");
            } else {
                SESSIONS.remove(playerId);
                LOGGER.warn("Attachment edit session dropped after timeout for offline player={}", playerId);
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
            if (attachment == null || attachment.isEmpty()) {
                continue;
            }
            snapshot.add(attachment.copy());
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
