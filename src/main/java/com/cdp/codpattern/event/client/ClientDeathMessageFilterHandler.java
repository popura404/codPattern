package com.cdp.codpattern.event.client;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.client.ClientTdmState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientDeathMessageFilterHandler {
    private ClientDeathMessageFilterHandler() {
    }

    @SubscribeEvent
    public static void onSystemChatReceived(ClientChatReceivedEvent.System event) {
        if (event == null || event.isOverlay() || !ClientTdmState.hasRoomContext()) {
            return;
        }
        if (isVanillaDeathMessage(event.getMessage())) {
            event.setCanceled(true);
        }
    }

    private static boolean isVanillaDeathMessage(Component message) {
        if (message == null) {
            return false;
        }

        if (message.getContents() instanceof TranslatableContents translatableContents
                && translatableContents.getKey().startsWith("death.")) {
            return true;
        }

        for (Component sibling : message.getSiblings()) {
            if (isVanillaDeathMessage(sibling)) {
                return true;
            }
        }

        return false;
    }
}
