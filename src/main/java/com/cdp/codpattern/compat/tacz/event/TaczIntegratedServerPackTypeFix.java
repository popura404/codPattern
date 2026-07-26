package com.cdp.codpattern.compat.tacz.event;

import com.cdp.codpattern.CodPatternConstants;
import com.tacz.guns.resource.GunPackLoader;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TaCZ stores the active pack type on a singleton loader.
 * In singleplayer that singleton is initialized on the physical client,
 * so the server data repository can incorrectly reuse CLIENT_RESOURCES.
 */
@Mod.EventBusSubscriber(modid = CodPatternConstants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TaczIntegratedServerPackTypeFix {
    private TaczIntegratedServerPackTypeFix() {
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        PackType packType = event.getPackType();
        if (packType != PackType.CLIENT_RESOURCES && packType != PackType.SERVER_DATA) {
            return;
        }
        GunPackLoader.INSTANCE.packType = packType;
    }
}
