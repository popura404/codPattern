package com.cdp.codpattern.fpsmatch.map;

import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.app.tdm.service.DeathCamService;
import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;
import com.cdp.codpattern.app.tdm.service.PlayerDeathService;
import com.cdp.codpattern.app.tdm.service.ScoreService;
import com.cdp.codpattern.app.tdm.service.VoteService;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.tdm.CountdownPacket;
import com.cdp.codpattern.network.tdm.DeathCamPacket;
import com.cdp.codpattern.network.tdm.PhysicsMobRetainPacket;
import com.cdp.codpattern.network.tdm.ScoreUpdatePacket;
import com.cdp.codpattern.network.tdm.VoteDialogPacket;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class CodTdmMapHooks {
    private CodTdmMapHooks() {
    }

    static PhaseStateMachine.Hooks createPhaseHooks(
            CodTdmMap map,
            Map<UUID, DeathCamData> deathCamPlayers,
            Map<UUID, Integer> respawnTimers
    ) {
        return new PhaseHooks(map, deathCamPlayers, respawnTimers);
    }

    static ScoreService.Hooks createScoreHooks(CodTdmMap map) {
        return new ScoreHooks(map);
    }

    static PlayerDeathService.Hooks createPlayerDeathHooks(CodTdmMap map, Map<UUID, DeathCamData> deathCamPlayers) {
        return new PlayerDeathHooks(map, deathCamPlayers);
    }

    static VoteService.Hooks createVoteHooks(CodTdmMap map, Map<UUID, Boolean> readyStates) {
        return new VoteHooks(map, readyStates);
    }

    private record PhaseHooks(
            CodTdmMap map,
            Map<UUID, DeathCamData> deathCamPlayers,
            Map<UUID, Integer> respawnTimers
    ) implements PhaseStateMachine.Hooks {
        @Override
        public void broadcastCountdown(CountdownPacket packet) {
            map.broadcastPacketToJoinedPlayers(packet);
        }

        @Override
        public void broadcastScoreUpdate(ScoreUpdatePacket packet) {
            map.broadcastPacketToJoinedPlayers(packet);
        }

        @Override
        public void teleportAllPlayersToSpawn() {
            map.teleportAllPlayersToSpawn();
        }

        @Override
        public void giveAllPlayersKits() {
            map.giveAllPlayersKits();
        }

        @Override
        public void clearAllPlayersInventory() {
            map.clearAllPlayersInventory();
        }

        @Override
        public void restoreAllRoomPlayersToAdventure() {
            map.restoreAllRoomPlayersToAdventure();
        }

        @Override
        public void clearRoundTransientState() {
            deathCamPlayers.clear();
            respawnTimers.clear();
        }

        @Override
        public boolean hasMatchEndTeleportPoint() {
            return map.hasMatchEndTeleportPoint();
        }

        @Override
        public Iterable<ServerPlayer> getJoinedPlayers() {
            return map.getJoinedServerPlayers();
        }

        @Override
        public void teleportPlayerToMatchEndPoint(ServerPlayer player) {
            map.teleportPlayerToMatchEndPoint(player);
        }

        @Override
        public void notifyMissingEndTeleportPoint(ServerPlayer player) {
            player.sendSystemMessage(Component.translatable(
                    "message.codpattern.game.warning_no_end_teleport",
                    map.mapNameView()));
        }

        @Override
        public void resetGame() {
            map.resetGame();
        }
    }

    private record ScoreHooks(CodTdmMap map) implements ScoreService.Hooks {
        @Override
        public Optional<BaseTeam> findTeamByPlayer(ServerPlayer player) {
            return map.findTeamByPlayer(player);
        }

        @Override
        public void broadcastScoreUpdate(ScoreUpdatePacket packet) {
            map.broadcastPacketToJoinedPlayers(packet);
        }

        @Override
        public void markRoomListDirty() {
            CodTdmRoomManager.getInstance().markRoomListDirty();
        }
    }

    private record PlayerDeathHooks(CodTdmMap map, Map<UUID, DeathCamData> deathCamPlayers)
            implements PlayerDeathService.Hooks {
        @Override
        public void broadcastPhysicsSnapshot(PhysicsMobRetainPacket packet) {
            map.broadcastPacketToJoinedPlayers(packet);
        }

        @Override
        public void clearPlayerInventory(ServerPlayer player) {
            map.clearPlayerInventoryForHooks(player);
        }

        @Override
        public void moveToDeathCam(ServerPlayer player, Vec3 cameraPosition, Vec3 lookAtPosition) {
            player.teleportTo(
                    map.getServerLevel(),
                    cameraPosition.x,
                    cameraPosition.y,
                    cameraPosition.z,
                    Set.of(),
                    player.getYRot(),
                    player.getXRot()
            );
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, lookAtPosition);
        }

        @Override
        public void sendDeathCamPacket(ServerPlayer player, DeathCamPacket packet) {
            ModNetworkChannel.sendToPlayer(packet, player);
        }

        @Override
        public void registerDeathCam(UUID playerId, UUID killerId, Vec3 deathPosition, Vec3 cameraPosition,
                int deathCamTicks) {
            DeathCamService.registerDeathCam(deathCamPlayers, playerId, killerId, deathPosition, cameraPosition,
                    deathCamTicks);
        }

        @Override
        public void scheduleRespawn(ServerPlayer player) {
            map.scheduleRespawn(player);
        }
    }

    private record VoteHooks(CodTdmMap map, Map<UUID, Boolean> readyStates) implements VoteService.Hooks {
        @Override
        public Player getPlayer(UUID playerId) {
            return map.getServerLevel().getPlayerByUUID(playerId);
        }

        @Override
        public List<ServerPlayer> getJoinedPlayers() {
            return map.getJoinedServerPlayers();
        }

        @Override
        public boolean isWaitingPhase() {
            return map.getPhase() == CodTdmMap.GamePhase.WAITING;
        }

        @Override
        public boolean isPlayingOrWarmupPhase() {
            CodTdmMap.GamePhase phase = map.getPhase();
            return phase == CodTdmMap.GamePhase.PLAYING || phase == CodTdmMap.GamePhase.WARMUP;
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
            return map.mapNameView();
        }

        @Override
        public void broadcastToJoinedPlayers(Component message) {
            map.broadcastToJoinedPlayers(message);
        }

        @Override
        public void sendVoteDialog(VoteDialogPacket packet, ServerPlayer player) {
            ModNetworkChannel.sendToPlayer(packet, player);
        }

        @Override
        public void onStartVotePassed() {
            map.startGame();
        }

        @Override
        public void onEndVotePassed() {
            map.transitionToEndedFromVote();
        }

        @Override
        public void markRoomListDirty() {
            CodTdmRoomManager.getInstance().markRoomListDirty();
        }
    }
}
