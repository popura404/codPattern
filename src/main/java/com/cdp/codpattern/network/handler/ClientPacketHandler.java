package com.cdp.codpattern.network.handler;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.client.gui.screen.BackpackMenuScreen;
import com.cdp.codpattern.network.tdm.VoteResponsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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

    public static void handleVoteDialog(String roomName, long voteId, String voteType, String initiatorName,
            int requiredVotes, int totalVoters) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        Screen previous = minecraft.screen;
        boolean startVote = "START".equalsIgnoreCase(voteType);
        Component title = startVote
                ? Component.translatable("screen.codpattern.vote_dialog.title_start")
                : Component.translatable("screen.codpattern.vote_dialog.title_end");
        Component message = startVote
                ? Component.translatable("screen.codpattern.vote_dialog.message_start", initiatorName, roomName,
                        requiredVotes, totalVoters)
                : Component.translatable("screen.codpattern.vote_dialog.message_end", initiatorName, roomName,
                        requiredVotes, totalVoters);

        minecraft.setScreen(new ConfirmScreen(accepted -> {
            minecraft.setScreen(previous);
            ModNetworkChannel.sendToServer(new VoteResponsePacket(voteId, accepted));
        },
                title,
                message,
                Component.translatable("screen.codpattern.vote_dialog.accept"),
                Component.translatable("screen.codpattern.vote_dialog.reject")));
    }
}
