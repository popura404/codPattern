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
            //一直想把这个生草的COW_HURT音效替换掉，找了半天忘记放哪了，结果在这。。。
            minecraft.player.playNotifySound(SoundEvents.BRUSH_GRAVEL, SoundSource.PLAYERS, 1.5f, 1f);
            minecraft.setScreen(new BackpackMenuScreen());
        }
    }
}