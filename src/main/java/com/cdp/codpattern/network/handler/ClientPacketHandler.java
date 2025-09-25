package com.cdp.codpattern.network.handler;

import com.cdp.codpattern.client.gui.screen.BackpackMenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {

    public static void handleOpenBackpackScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.playNotifySound(SoundEvents.COW_HURT, SoundSource.PLAYERS, 1f, 1f);
            minecraft.setScreen(new BackpackMenuScreen());
        }
    }
}