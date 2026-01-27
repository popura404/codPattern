package com.cdp.codpattern.core.refit;

import com.mojang.logging.LogUtils;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AttachmentEditSessionManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, AttachmentEditSession> SESSIONS = new ConcurrentHashMap<>();

    public static AttachmentEditSession startSession(ServerPlayer player, int bagId, String slot, ItemStack gunStack) {
        endSession(player);
        Inventory inventory = player.getInventory();
        int editSlot = inventory.selected;
        int originalSelected = inventory.selected;
        ItemStack backup = inventory.getItem(editSlot).copy();

        inventory.setItem(editSlot, gunStack);
        inventory.selected = editSlot;
        player.connection.send(new ClientboundSetCarriedItemPacket(editSlot));
        player.inventoryMenu.broadcastChanges();

        AttachmentEditSession session = new AttachmentEditSession(bagId, slot, editSlot, originalSelected, backup);
        SESSIONS.put(player.getUUID(), session);
        LOGGER.info("Attachment edit session started: player={} bagId={} slot={} hotbar={}",
                player.getGameProfile().getName(), bagId, slot, editSlot);
        return session;
    }

    public static AttachmentEditSession getSession(UUID playerId) {
        return SESSIONS.get(playerId);
    }

    public static void endSession(ServerPlayer player) {
        AttachmentEditSession session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        inventory.setItem(session.getEditHotbarSlot(), session.getBackupStack());
        inventory.selected = session.getOriginalSelectedSlot();
        player.connection.send(new ClientboundSetCarriedItemPacket(session.getOriginalSelectedSlot()));
        player.inventoryMenu.broadcastChanges();
        LOGGER.info("Attachment edit session ended: player={} bagId={} slot={}",
                player.getGameProfile().getName(), session.getBagId(), session.getSlot());
    }
}
