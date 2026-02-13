package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.service.ScoreService;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import com.cdp.codpattern.network.tdm.ScoreUpdatePacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

final class CodTdmScoreHooks implements ScoreService.Hooks {
    private final CodTdmScoreHooksPort port;
    private final CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster;

    CodTdmScoreHooks(CodTdmScoreHooksPort port, CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster) {
        this.port = port;
        this.joinedPlayerBroadcaster = joinedPlayerBroadcaster;
    }

    @Override
    public Optional<String> findTeamNameByPlayer(ServerPlayer player) {
        return port.findTeamNameByPlayer(player);
    }

    @Override
    public void broadcastScoreUpdate(ScoreUpdatePacket packet) {
        joinedPlayerBroadcaster.broadcastPacketToJoinedPlayers(packet);
    }

    @Override
    public void markRoomListDirty() {
        CodTdmRoomManager.getInstance().markRoomListDirty();
    }
}
