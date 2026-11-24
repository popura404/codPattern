package com.cdp.codpattern.event.client;

import com.cdp.codpattern.CodPatternConstants;
import com.cdp.codpattern.compat.tacz.client.CodGunRefitScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CodPatternConstants.MOD_ID, value = Dist.CLIENT)
public final class RefitPreviewHandHandler {
    private RefitPreviewHandHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hideDuplicateHandModel(RenderHandEvent event) {
        // The refit screen now renders the working gun once in the same stage as the weapon picker.
        if (Minecraft.getInstance().screen instanceof CodGunRefitScreen) event.setCanceled(true);
    }
}
