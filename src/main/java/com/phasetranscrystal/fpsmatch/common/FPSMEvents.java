package com.phasetranscrystal.fpsmatch.common;

import com.cdp.codpattern.app.match.runtime.tool.ModeHeldToolPreviewContributors;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolAccessHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FPSMEvents {
    private FPSMEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTickEvent(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        ModeHeldToolPreviewContributors.route(player, stack, ToolAccessHelper.hasAdminAccess(player));
    }
}
