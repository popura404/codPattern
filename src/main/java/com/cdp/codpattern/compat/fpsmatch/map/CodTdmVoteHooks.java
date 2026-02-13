package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.tdm.service.VoteService;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import com.cdp.codpattern.network.tdm.VoteDialogPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CodTdmVoteHooks implements VoteService.Hooks {
    private final CodTdmVoteHooksPort port;
    private final Map<UUID, Boolean> readyStates;
    private final CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster;

    CodTdmVoteHooks(
            CodTdmVoteHooksPort port,
            Map<UUID, Boolean> readyStates,
            CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster
    ) {
        this.port = port;
        this.readyStates = readyStates;
        this.joinedPlayerBroadcaster = joinedPlayerBroadcaster;
    }

    @Override
    public Player getPlayer(UUID playerId) {
        return port.getPlayer(playerId);
    }

    @Override
    public List<ServerPlayer> getJoinedPlayers() {
        return port.getJoinedPlayers();
    }

    @Override
    public boolean isWaitingPhase() {
        return port.getPhase() == TdmGamePhase.WAITING;
    }

    @Override
    public boolean isPlayingOrWarmupPhase() {
        TdmGamePhase phase = port.getPhase();
        return phase == TdmGamePhase.PLAYING || phase == TdmGamePhase.WARMUP;
    }

    @Override
    public boolean isPlayerReady(UUID playerId) {
        return readyStates.getOrDefault(playerId, false);
    }

    @Override
    public int getMinPlayersToStart() {
        return CodTdmConfig.getConfig().getMinPlayersToStart();
    }

    @Override
    public int getVotePercentageToStart() {
        return CodTdmConfig.getConfig().getVotePercentageToStart();
    }

    @Override
    public int getVotePercentageToEnd() {
        return CodTdmConfig.getConfig().getVotePercentageToEnd();
    }

    @Override
    public String getMapName() {
        return port.mapName();
    }

    @Override
    public void broadcastToJoinedPlayers(Component message) {
        joinedPlayerBroadcaster.broadcastToJoinedPlayers(message);
    }

    @Override
    public void sendVoteDialog(VoteDialogPacket packet, ServerPlayer player) {
        ModNetworkChannel.sendToPlayer(packet, player);
    }

    @Override
    public void onStartVotePassed() {
        port.startGame();
    }

    @Override
    public void onEndVotePassed() {
        port.transitionToEndedFromVote();
    }

    @Override
    public void markRoomListDirty() {
        CodTdmRoomManager.getInstance().markRoomListDirty();
    }
}
