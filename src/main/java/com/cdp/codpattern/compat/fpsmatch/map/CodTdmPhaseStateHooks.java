package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;
import com.cdp.codpattern.network.tdm.CountdownPacket;
import com.cdp.codpattern.network.tdm.ScoreUpdatePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class CodTdmPhaseStateHooks implements PhaseStateMachine.Hooks {
    private final CodTdmPhaseHooksPort port;
    private final CodTdmPlayerRuntimeState playerState;
    private final CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster;

    CodTdmPhaseStateHooks(
            CodTdmPhaseHooksPort port,
            CodTdmPlayerRuntimeState playerState,
            CodTdmJoinedPlayerBroadcaster joinedPlayerBroadcaster
    ) {
        this.port = port;
        this.playerState = playerState;
        this.joinedPlayerBroadcaster = joinedPlayerBroadcaster;
    }

    @Override
    public void broadcastCountdown(CountdownPacket packet) {
        joinedPlayerBroadcaster.broadcastPacketToJoinedPlayers(packet);
    }

    @Override
    public void broadcastScoreUpdate(ScoreUpdatePacket packet) {
        joinedPlayerBroadcaster.broadcastPacketToJoinedPlayers(packet);
    }

    @Override
    public void teleportAllPlayersToSpawn() {
        port.teleportAllPlayersToSpawn();
    }

    @Override
    public void giveAllPlayersKits() {
        port.giveAllPlayersKits();
    }

    @Override
    public void clearAllPlayersInventory() {
        port.clearAllPlayersInventory();
    }

    @Override
    public void restoreAllRoomPlayersToAdventure() {
        port.restoreAllRoomPlayersToAdventure();
    }

    @Override
    public void notifyMatchEnded() {
        joinedPlayerBroadcaster.broadcastToJoinedPlayers(Component.translatable("message.codpattern.game.match_ended"));
    }

    @Override
    public void onMatchEnded() {
        port.onMatchEnded();
    }

    @Override
    public void clearRoundTransientState() {
        playerState.clearRoundTransientState();
    }

    @Override
    public boolean hasMatchEndTeleportPoint() {
        return port.hasMatchEndTeleportPoint();
    }

    @Override
    public Iterable<ServerPlayer> getJoinedPlayers() {
        return port.getJoinedPlayers();
    }

    @Override
    public void teleportPlayerToMatchEndPoint(ServerPlayer player) {
        port.teleportPlayerToMatchEndPoint(player);
    }

    @Override
    public void notifyMissingEndTeleportPoint(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable(
                "message.codpattern.game.warning_no_end_teleport",
                port.mapName()));
    }

    @Override
    public void resetGame() {
        port.resetGame();
    }
}
