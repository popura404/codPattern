package com.cdp.codpattern.client.gui.overlay;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Lightweight helper for rendering player 3D models in HUD-style overlays.
 */
final class TdmPlayerModelRenderer {
    private TdmPlayerModelRenderer() {
    }

    static boolean render(
            GuiGraphics graphics,
            PlayerInfo playerInfo,
            int centerX,
            int baselineY,
            int modelScale,
            float motionX,
            float motionY
    ) {
        LivingEntity entity = resolveEntity(playerInfo);
        if (entity == null) {
            return false;
        }

        InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics,
                centerX,
                baselineY,
                modelScale,
                motionX,
                motionY,
                entity
        );
        return true;
    }

    private static LivingEntity resolveEntity(PlayerInfo playerInfo) {
        if (playerInfo == null || playerInfo.uuid() == null) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return null;
        }

        Player trackedPlayer = level.getPlayerByUUID(playerInfo.uuid());
        return trackedPlayer;
    }
}
