package com.cdp.codpattern.event;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.core.throwable.ThrowableInventoryProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ThrowableCapabilityAttachHandler {
    private static final ResourceLocation CAPABILITY_ID =
            ResourceLocation.fromNamespaceAndPath(CodPattern.MODID, "throwable_inventory");

    private ThrowableCapabilityAttachHandler() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player)) {
            return;
        }
        event.addCapability(CAPABILITY_ID, new ThrowableInventoryProvider());
    }
}
