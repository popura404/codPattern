package com.phasetranscrystal.fpsmatch.common;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.MapCreatorTool;
import com.phasetranscrystal.fpsmatch.common.item.SpawnPointTool;
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
        if (stack.getItem() instanceof MapCreatorTool mapCreatorTool) {
            mapCreatorTool.syncHeldPreview(player, stack);
            SpawnPointTool.clearHeldPreview(player);
            return;
        }
        if (stack.getItem() instanceof SpawnPointTool spawnPointTool) {
            spawnPointTool.syncHeldPreview(player, stack);
            MapCreatorTool.clearHeldPreview(player);
            return;
        }

        MapCreatorTool.clearHeldPreview(player);
        SpawnPointTool.clearHeldPreview(player);
    }
}
