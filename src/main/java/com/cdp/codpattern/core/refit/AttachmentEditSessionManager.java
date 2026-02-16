package com.cdp.codpattern.core.refit;

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
        for (ItemStack attachment : sandboxAttachments) {
            if (attachment == null || attachment.isEmpty()) {
                continue;
            }
            while (writeCursor < SANDBOX_MAIN_INVENTORY_SIZE && writeCursor == editSlot) {
                writeCursor++;
            }
            if (writeCursor >= SANDBOX_MAIN_INVENTORY_SIZE) {
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

        long now = System.currentTimeMillis();
        AttachmentEditSession session = new AttachmentEditSession(
                bagId,
                slot,
                editSlot,
                originalSelected,
                snapshot,
                now,
                now + SESSION_TIMEOUT_MS,
                insertedAttachments,
                truncatedAttachments);
        SESSIONS.put(player.getUUID(), session);
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
        if (session == null) {
            return;
        }

        restoreInventory(player.getInventory(), session.getInventorySnapshot());
        int selected = Math.max(0, Math.min(8, session.getOriginalSelectedSlot()));
        player.getInventory().selected = selected;
        player.connection.send(new ClientboundSetCarriedItemPacket(selected));
        player.inventoryMenu.broadcastChanges();

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
