package com.phasetranscrystal.fpsmatch;

import com.cdp.codpattern.CodPatternConstants;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class FPSMatch {
    public static final String MODID = CodPatternConstants.MOD_ID;
    public static final Logger LOGGER = LogUtils.getLogger();

    private FPSMatch() {
    }

    public static <MSG> void sendToServer(MSG message) {
        ModNetworkChannel.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(ServerPlayer player, MSG message) {
        ModNetworkChannel.sendToPlayer(message, player);
    }
}
