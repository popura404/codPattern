package com.cdp.codpattern.network.tdm;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: 分数更新数据包
 */
public class ScoreUpdatePacket {
    private final int team1Score;
    private final int team2Score;
    private final int gameTimeTicks;

    public ScoreUpdatePacket(int team1Score, int team2Score, int gameTimeTicks) {
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.gameTimeTicks = gameTimeTicks;
    }

    public ScoreUpdatePacket(FriendlyByteBuf buf) {
        this.team1Score = buf.readInt();
        this.team2Score = buf.readInt();
        this.gameTimeTicks = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(team1Score);
        buf.writeInt(team2Score);
        buf.writeInt(gameTimeTicks);
    }

    public static ScoreUpdatePacket decode(FriendlyByteBuf buf) {
        return new ScoreUpdatePacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().execute(() -> {
                com.cdp.codpattern.client.ClientTdmState.team1Score = team1Score;
                com.cdp.codpattern.client.ClientTdmState.team2Score = team2Score;
                com.cdp.codpattern.client.ClientTdmState.gameTimeTicks = gameTimeTicks; // Keeping original fields as
                                                                                        // new ones are not defined in
                                                                                        // this packet
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
