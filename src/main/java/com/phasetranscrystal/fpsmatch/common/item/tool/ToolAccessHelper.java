package com.phasetranscrystal.fpsmatch.common.item.tool;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ToolAccessHelper {
    private static final int ADMIN_PERMISSION_LEVEL = 2;

    private ToolAccessHelper() {
    }

    public static boolean hasAdminAccess(ServerPlayer player) {
        return player != null && player.hasPermissions(ADMIN_PERMISSION_LEVEL);
    }

    public static boolean ensureAdminAccess(ServerPlayer player) {
        if (hasAdminAccess(player)) {
            return true;
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.fpsm.tool.admin_required"), false);
        }
        return false;
    }
}
