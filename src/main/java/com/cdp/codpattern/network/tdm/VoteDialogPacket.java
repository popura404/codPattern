package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.network.handler.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: 投票弹窗请求数据包
 */
public class VoteDialogPacket {
    private final String roomName;
    private final long voteId;
    private final String voteType;
    private final String initiatorName;
    private final int requiredVotes;
    private final int totalVoters;

    public VoteDialogPacket(String roomName, long voteId, String voteType, String initiatorName, int requiredVotes,
            int totalVoters) {
        this.roomName = roomName;
        this.voteId = voteId;
        this.voteType = voteType;
        this.initiatorName = initiatorName;
        this.requiredVotes = requiredVotes;
        this.totalVoters = totalVoters;
    }

    public VoteDialogPacket(FriendlyByteBuf buf) {
        this.roomName = buf.readUtf();
        this.voteId = buf.readLong();
        this.voteType = buf.readUtf();
        this.initiatorName = buf.readUtf();
        this.requiredVotes = buf.readInt();
        this.totalVoters = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(roomName);
        buf.writeLong(voteId);
        buf.writeUtf(voteType);
        buf.writeUtf(initiatorName);
        buf.writeInt(requiredVotes);
        buf.writeInt(totalVoters);
    }

    public static VoteDialogPacket decode(FriendlyByteBuf buf) {
        return new VoteDialogPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().execute(() -> {
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
                    PacketHandler.sendToServer(new VoteResponsePacket(voteId, accepted));
                },
                        title,
                        message,
                        Component.translatable("screen.codpattern.vote_dialog.accept"),
                        Component.translatable("screen.codpattern.vote_dialog.reject")));
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
