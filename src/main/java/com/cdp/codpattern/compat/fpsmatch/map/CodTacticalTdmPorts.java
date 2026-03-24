package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tactical.port.CodTacticalTdmActionPort;
import com.cdp.codpattern.app.tactical.port.CodTacticalTdmReadPort;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.TeamSpawnProfile;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class CodTacticalTdmPorts {
    private CodTacticalTdmPorts() {
    }

    static CodTacticalTdmActionPort wrapAction(CodTdmActionPort delegate) {
        return new TacticalActionPort(delegate);
    }

    static CodTacticalTdmReadPort wrapRead(CodTdmReadPort delegate) {
        return new TacticalReadPort(delegate);
    }

    private record TacticalActionPort(CodTdmActionPort delegate) implements CodTacticalTdmActionPort {
        @Override
        public String mapName() {
            return delegate.mapName();
        }

        @Override
        public void onPlayerDamaged(ServerPlayer player) {
            delegate.onPlayerDamaged(player);
        }

        @Override
        public void onPlayerKill(ServerPlayer killer, ServerPlayer victim) {
            delegate.onPlayerKill(killer, victim);
        }

        @Override
        public void onPlayerDead(ServerPlayer player, ServerPlayer killer) {
            delegate.onPlayerDead(player, killer);
        }

        @Override
        public void leaveRoom(ServerPlayer player) {
            delegate.leaveRoom(player);
        }

        @Override
        public void switchTeam(ServerPlayer player, String teamName) {
            delegate.switchTeam(player, teamName);
        }

        @Override
        public void joinTeam(String teamName, ServerPlayer player) {
            delegate.joinTeam(teamName, player);
        }

        @Override
        public void joinSpectator(ServerPlayer player) {
            delegate.joinSpectator(player);
        }

        @Override
        public void respawnPlayerNow(ServerPlayer player) {
            delegate.respawnPlayerNow(player);
        }

        @Override
        public void syncToClient() {
            delegate.syncToClient();
        }

        @Override
        public void applyTeamSpawnProfile(String teamName, int playerLimit, TeamSpawnProfile spawnProfile) {
            delegate.applyTeamSpawnProfile(teamName, playerLimit, spawnProfile);
        }

        @Override
        public void setMatchEndTeleportPoint(SpawnPointData point) {
            delegate.setMatchEndTeleportPoint(point);
        }

        @Override
        public boolean initiateStartVote(UUID initiator) {
            return delegate.initiateStartVote(initiator);
        }

        @Override
        public boolean initiateEndVote(UUID initiator) {
            return delegate.initiateEndVote(initiator);
        }

        @Override
        public boolean submitVoteResponse(UUID playerId, long voteId, boolean accepted) {
            return delegate.submitVoteResponse(playerId, voteId, accepted);
        }

        @Override
        public void initializeReadyState(ServerPlayer player) {
            delegate.initializeReadyState(player);
        }

        @Override
        public boolean setPlayerReady(ServerPlayer player, boolean ready) {
            return delegate.setPlayerReady(player, ready);
        }

        @Override
        public void setSpectatorPreferredTeam(ServerPlayer player, String teamName) {
            delegate.setSpectatorPreferredTeam(player, teamName);
        }

        @Override
        public Optional<String> consumeSpectatorPreferredTeam(ServerPlayer player) {
            return delegate.consumeSpectatorPreferredTeam(player);
        }
    }

    private record TacticalReadPort(CodTdmReadPort delegate) implements CodTacticalTdmReadPort {
        @Override
        public String mapName() {
            return delegate.mapName();
        }

        @Override
        public boolean containsJoinedPlayer(UUID playerId) {
            return delegate.containsJoinedPlayer(playerId);
        }

        @Override
        public boolean containsSpectator(ServerPlayer player) {
            return delegate.containsSpectator(player);
        }

        @Override
        public boolean isStarted() {
            return delegate.isStarted();
        }

        @Override
        public String phaseName() {
            return delegate.phaseName();
        }

        @Override
        public boolean isPlayingPhase() {
            return delegate.isPlayingPhase();
        }

        @Override
        public boolean isWaitingPhase() {
            return delegate.isWaitingPhase();
        }

        @Override
        public boolean canDealDamage() {
            return delegate.canDealDamage();
        }

        @Override
        public boolean isPlayerInvincible(UUID playerId) {
            return delegate.isPlayerInvincible(playerId);
        }

        @Override
        public boolean hasMatchEndTeleportPoint() {
            return delegate.hasMatchEndTeleportPoint();
        }

        @Override
        public int getRemainingTimeTicks() {
            return delegate.getRemainingTimeTicks();
        }

        @Override
        public Map<String, Integer> getTeamScoresSnapshot() {
            return delegate.getTeamScoresSnapshot();
        }

        @Override
        public Map<String, Integer> getTeamPlayerCountsSnapshot() {
            return delegate.getTeamPlayerCountsSnapshot();
        }

        @Override
        public int getMaxPlayerCapacity() {
            return delegate.getMaxPlayerCapacity();
        }

        @Override
        public boolean hasTeam(String teamName) {
            return delegate.hasTeam(teamName);
        }

        @Override
        public boolean isTeamFull(String teamName) {
            return delegate.isTeamFull(teamName);
        }

        @Override
        public Optional<String> findTeamNameByPlayer(ServerPlayer player) {
            return delegate.findTeamNameByPlayer(player);
        }

        @Override
        public Optional<String> chooseAutoJoinTeam(int maxTeamDiff) {
            return delegate.chooseAutoJoinTeam(maxTeamDiff);
        }

        @Override
        public boolean canJoinWithBalance(String teamName, int maxTeamDiff) {
            return delegate.canJoinWithBalance(teamName, maxTeamDiff);
        }

        @Override
        public com.phasetranscrystal.fpsmatch.core.data.AreaData mapArea() {
            return delegate.mapArea();
        }

        @Override
        public String dimensionId() {
            return delegate.dimensionId();
        }

        @Override
        public java.util.List<com.cdp.codpattern.app.tdm.model.CodTdmTeamPersistenceSnapshot> teamPersistenceSnapshots() {
            return delegate.teamPersistenceSnapshots();
        }

        @Override
        public Optional<SpawnPointData> matchEndTeleportPoint() {
            return delegate.matchEndTeleportPoint();
        }
    }
}
