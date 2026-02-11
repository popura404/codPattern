package com.cdp.codpattern.network.tdm;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S→C: 分数更新数据包
 */
public class ScoreUpdatePacket {
    private final int team1Score;
    private final int team2Score;
    private final Map<String, Integer> teamScores;
    private final int gameTimeTicks;

    public ScoreUpdatePacket(int team1Score, int team2Score, int gameTimeTicks) {
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.teamScores = new HashMap<>();
        this.teamScores.put("kortac", team1Score);
        this.teamScores.put("specgru", team2Score);
        this.gameTimeTicks = gameTimeTicks;
    }

    public ScoreUpdatePacket(Map<String, Integer> teamScores, int gameTimeTicks) {
        this.teamScores = new HashMap<>();
        if (teamScores != null) {
            this.teamScores.putAll(teamScores);
        }
        this.team1Score = this.teamScores.getOrDefault("kortac", 0);
        this.team2Score = this.teamScores.getOrDefault("specgru", 0);
        this.gameTimeTicks = gameTimeTicks;
    }

    public ScoreUpdatePacket(FriendlyByteBuf buf) {
        this.team1Score = buf.readInt();
        this.team2Score = buf.readInt();
        this.gameTimeTicks = buf.readInt();
        this.teamScores = new HashMap<>();
        if (buf.readableBytes() >= Integer.BYTES) {
            int scoreMapSize = Math.max(0, buf.readInt());
            for (int i = 0; i < scoreMapSize; i++) {
                if (buf.readableBytes() <= 0) {
                    break;
                }
                this.teamScores.put(buf.readUtf(), buf.readInt());
            }
        }
        if (this.teamScores.isEmpty()) {
            this.teamScores.put("kortac", this.team1Score);
            this.teamScores.put("specgru", this.team2Score);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(team1Score);
        buf.writeInt(team2Score);
        buf.writeInt(gameTimeTicks);
        buf.writeInt(teamScores.size());
        for (Map.Entry<String, Integer> entry : teamScores.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }
    }

    public static ScoreUpdatePacket decode(FriendlyByteBuf buf) {
        return new ScoreUpdatePacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().execute(() -> {
                com.cdp.codpattern.client.ClientTdmState.updateScore(teamScores, team1Score, team2Score, gameTimeTicks);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
