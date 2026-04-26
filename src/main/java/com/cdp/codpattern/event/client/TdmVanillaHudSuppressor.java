package com.cdp.codpattern.event.client;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.client.gui.overlay.TdmHudOverlay;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TdmVanillaHudSuppressor {
    private static final Set<ResourceLocation> SUPPRESSED_OVERLAY_IDS = Set.of(
            VanillaGuiOverlay.HOTBAR.id(),
            VanillaGuiOverlay.PLAYER_HEALTH.id(),
            VanillaGuiOverlay.EXPERIENCE_BAR.id(),
            VanillaGuiOverlay.FOOD_LEVEL.id());

    private TdmVanillaHudSuppressor() {
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!TdmHudOverlay.shouldReplaceVanillaPlayerHud()) {
            return;
        }
        if (SUPPRESSED_OVERLAY_IDS.contains(event.getOverlay().id())) {
            event.setCanceled(true);
        }
    }
}
