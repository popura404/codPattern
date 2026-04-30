package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Map;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.ScoreUpdatePacket}.
 */
@Deprecated(forRemoval = false)
public class ScoreUpdatePacket extends com.cdp.codpattern.network.match.ScoreUpdatePacket {
    public ScoreUpdatePacket(int team1Score, int team2Score, int gameTimeTicks) {
        super(team1Score, team2Score, gameTimeTicks);
    }

    public ScoreUpdatePacket(Map<String, Integer> teamScores, int gameTimeTicks) {
        super(teamScores, gameTimeTicks);
    }

    public ScoreUpdatePacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static ScoreUpdatePacket decode(FriendlyByteBuf buf) {
        return new ScoreUpdatePacket(buf);
    }
}
