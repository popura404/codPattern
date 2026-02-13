package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

final class CodTdmMapActions {
    private CodTdmMapActions() {
    }

    static CodTdmActionPort fromRuntimes(
            CodTdmCombatRuntime combatRuntime,
            CodTdmTeamMembershipCoordinator teamMembershipCoordinator,
            CodTdmMapMutationRuntime mapMutationRuntime,
            CodTdmEndTeleportRuntime endTeleportRuntime,
            CodTdmVoteRuntime voteRuntime
    ) {
        return new MapActionPort(
                combatRuntime,
                teamMembershipCoordinator,
                mapMutationRuntime,
                endTeleportRuntime,
                voteRuntime
        );
    }

    private record MapActionPort(
            CodTdmCombatRuntime combatRuntime,
            CodTdmTeamMembershipCoordinator teamMembershipCoordinator,
            CodTdmMapMutationRuntime mapMutationRuntime,
            CodTdmEndTeleportRuntime endTeleportRuntime,
            CodTdmVoteRuntime voteRuntime
    ) implements CodTdmActionPort {

        @Override
        public void onPlayerKill(ServerPlayer killer, ServerPlayer victim) {
            combatRuntime.onPlayerKill(killer, victim);
        }

        @Override
        public void onPlayerDead(ServerPlayer player, ServerPlayer killer) {
            combatRuntime.onPlayerDead(player, killer);
        }

        @Override
        public void leaveRoom(ServerPlayer player) {
            teamMembershipCoordinator.leaveRoom(player);
        }

        @Override
        public void switchTeam(ServerPlayer player, String teamName) {
            teamMembershipCoordinator.switchTeam(player, teamName);
        }

        @Override
        public void joinTeam(String teamName, ServerPlayer player) {
            mapMutationRuntime.joinTeam(teamName, player);
        }

        @Override
        public void joinSpectator(ServerPlayer player) {
            mapMutationRuntime.joinSpectator(player);
        }

        @Override
        public void syncToClient() {
            mapMutationRuntime.syncToClient();
        }

        @Override
        public void applyTeamSpawnData(String teamName, int playerLimit, List<SpawnPointData> spawnPoints) {
            mapMutationRuntime.applyTeamSpawnData(teamName, playerLimit, spawnPoints);
        }

        @Override
        public void setMatchEndTeleportPoint(SpawnPointData point) {
            endTeleportRuntime.setMatchEndTeleportPoint(point);
        }

        @Override
        public boolean initiateStartVote(UUID initiator) {
            return voteRuntime.initiateStartVote(initiator);
        }

        @Override
        public boolean initiateEndVote(UUID initiator) {
            return voteRuntime.initiateEndVote(initiator);
        }

        @Override
        public boolean submitVoteResponse(UUID playerId, long voteId, boolean accepted) {
            return voteRuntime.submitVoteResponse(playerId, voteId, accepted);
        }

        @Override
        public void initializeReadyState(ServerPlayer player) {
            voteRuntime.initializeReadyState(player);
        }

        @Override
        public boolean setPlayerReady(ServerPlayer player, boolean ready) {
            return voteRuntime.setPlayerReady(player, ready);
        }
    }
}
