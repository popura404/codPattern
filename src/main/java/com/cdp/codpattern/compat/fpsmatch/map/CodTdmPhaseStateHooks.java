package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.network.tdm.CountdownPacket;
import com.cdp.codpattern.network.tdm.ScoreUpdatePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
    public void showCountdownActionBar(int secondsLeft) {
        port.getJoinedPlayers().forEach(player -> player.displayClientMessage(
                Component.translatable("hud.codpattern.tdm.actionbar.countdown", secondsLeft),
                true));
    }

    @Override
    public void clearCountdownActionBar() {
        port.getJoinedPlayers().forEach(player -> player.displayClientMessage(Component.empty(), true));
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
    public void lockWarmupMovement() {
        port.lockWarmupMovement();
    }

    @Override
    public void unlockAllRoomPlayersMovement() {
        port.unlockAllRoomPlayersMovement();
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
    public boolean teleportPlayerToMatchEndPoint(ServerPlayer player) {
        return port.teleportPlayerToMatchEndPoint(player);
    }

    @Override
    public void notifyMissingEndTeleportPoint(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable(
                "message.codpattern.game.warning_no_end_teleport",
                port.mapName()));
    }

    @Override
    public void notifyUnusableEndTeleportPoint(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable(
                "message.codpattern.game.warning_end_teleport_unusable",
                port.mapName()));
    }

    @Override
    public void showPlayingIntro() {
        Component title = Component.translatable(GameModeRegistry.getOrDefault(port.gameType()).displayNameKey());
        Component subtitle = Component.translatable(resolveObjectiveKey(port.gameType()),
                CodTdmConfig.getConfig().getScoreLimit());
        ClientboundSetTitlesAnimationPacket animationPacket = new ClientboundSetTitlesAnimationPacket(4, 42, 16);
        ClientboundSetTitleTextPacket titlePacket = new ClientboundSetTitleTextPacket(title);
        ClientboundSetSubtitleTextPacket subtitlePacket = new ClientboundSetSubtitleTextPacket(subtitle);

        for (ServerPlayer player : port.getJoinedPlayers()) {
            player.connection.send(animationPacket);
            player.connection.send(titlePacket);
            player.connection.send(subtitlePacket);
            player.playNotifySound(SoundEvents.RAID_HORN.get(), SoundSource.PLAYERS, 0.85f, 1.0f);
        }
    }

    @Override
    public void resetGame() {
        port.resetGame();
    }

    private String resolveObjectiveKey(String gameType) {
        if (TdmGameTypes.isTeamDeathMatch(gameType)) {
            return "hud.codpattern.tdm.intro.teamdeathmatch.objective";
        }
        return "hud.codpattern.tdm.intro.frontline.objective";
    }
}
