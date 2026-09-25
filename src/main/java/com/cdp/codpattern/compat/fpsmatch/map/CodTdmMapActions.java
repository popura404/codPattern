package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tactical.port.CodTacticalTdmActionPort;
import com.cdp.codpattern.app.teammatch.TeamMatchPolicy;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.TeamSpawnProfile;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.UUID;

final class CodTdmMapActions {
    private CodTdmMapActions() {
    }

    static CodTdmActionPort fromRuntimes(
            TeamMatchPolicy policy,
            CodTdmMap map,
            CodTdmCombatRuntime combatRuntime,
            CodTdmTeamMembershipCoordinator teamMembershipCoordinator,
            CodTdmMapMutationRuntime mapMutationRuntime,
            CodTdmEndTeleportRuntime endTeleportRuntime,
            CodTdmVoteRuntime voteRuntime,
            CodTdmRespawnRuntime respawnRuntime,
            Consumer<ServerPlayer> requestRosterResyncAction,
            Consumer<ServerPlayer> requestRosterPreviewAction,
            Supplier<String> mapNameSupplier
    ) {
        if (policy.tacticalCompatibilityPorts()) {
            return new TacticalMapActionPort(
                    policy,
                    map,
                    combatRuntime,
                    teamMembershipCoordinator,
                    mapMutationRuntime,
                    endTeleportRuntime,
                    voteRuntime,
                    respawnRuntime,
                    requestRosterResyncAction,
                    requestRosterPreviewAction,
                    mapNameSupplier
            );
        }
        return new MapActionPort(
                policy,
                map,
                combatRuntime,
                teamMembershipCoordinator,
                mapMutationRuntime,
                endTeleportRuntime,
                voteRuntime,
                respawnRuntime,
                requestRosterResyncAction,
                requestRosterPreviewAction,
                mapNameSupplier
        );
    }

    private static class MapActionPort implements CodTdmActionPort {
        private final TeamMatchPolicy policy;
        private final CodTdmMap map;
        private final CodTdmCombatRuntime combatRuntime;
        private final CodTdmTeamMembershipCoordinator teamMembershipCoordinator;
        private final CodTdmMapMutationRuntime mapMutationRuntime;
        private final CodTdmEndTeleportRuntime endTeleportRuntime;
        private final CodTdmVoteRuntime voteRuntime;
        private final CodTdmRespawnRuntime respawnRuntime;
        private final Consumer<ServerPlayer> requestRosterResyncAction;
        private final Consumer<ServerPlayer> requestRosterPreviewAction;
        private final Supplier<String> mapNameSupplier;

        private MapActionPort(
                TeamMatchPolicy policy,
                CodTdmMap map,
                CodTdmCombatRuntime combatRuntime,
                CodTdmTeamMembershipCoordinator teamMembershipCoordinator,
                CodTdmMapMutationRuntime mapMutationRuntime,
                CodTdmEndTeleportRuntime endTeleportRuntime,
                CodTdmVoteRuntime voteRuntime,
                CodTdmRespawnRuntime respawnRuntime,
                Consumer<ServerPlayer> requestRosterResyncAction,
                Consumer<ServerPlayer> requestRosterPreviewAction,
                Supplier<String> mapNameSupplier
        ) {
            this.policy = policy;
            this.map = map;
            this.combatRuntime = combatRuntime;
            this.teamMembershipCoordinator = teamMembershipCoordinator;
            this.mapMutationRuntime = mapMutationRuntime;
            this.endTeleportRuntime = endTeleportRuntime;
            this.voteRuntime = voteRuntime;
            this.respawnRuntime = respawnRuntime;
            this.requestRosterResyncAction = requestRosterResyncAction;
            this.requestRosterPreviewAction = requestRosterPreviewAction;
            this.mapNameSupplier = mapNameSupplier;
        }

        @Override
        public com.cdp.codpattern.app.match.runtime.termination.ModeForceEndHandler forceEndHandler() {
            return map.forceEndHandler();
        }

        private boolean terminated() {
            return com.cdp.codpattern.app.match.runtime.termination.RoomTerminationService.get(map.getServerLevel().getServer())
                    .terminated(com.cdp.codpattern.app.match.model.RoomId.of(gameType(), mapName()));
        }

        @Override
        public String gameType() {
            return policy.gameType();
        }

        @Override
        public String mapName() {
            return mapNameSupplier.get();
        }

        @Override
        public void onPlayerDamaged(ServerPlayer player) {
            if (terminated()) return;
            combatRuntime.onPlayerDamaged(player);
        }

        @Override
        public void onPlayerKill(ServerPlayer killer, ServerPlayer victim) {
            if (terminated()) return;
            combatRuntime.onPlayerKill(killer, victim);
        }

        @Override
        public void onPlayerDead(ServerPlayer player, ServerPlayer killer) {
            if (terminated()) return;
            combatRuntime.onPlayerDead(player, killer);
        }

        @Override
        public void leaveRoom(ServerPlayer player) {
            teamMembershipCoordinator.leaveRoom(player);
        }

        @Override
        public void switchTeam(ServerPlayer player, String teamName) {
            if (map.recoveryBlocked()) return;
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
        public void respawnPlayerNow(ServerPlayer player) {
            if (terminated()) return;
            respawnRuntime.respawnPlayerNow(player);
        }

        @Override
        public void syncToClient() {
            mapMutationRuntime.syncToClient();
        }

        @Override
        public void applyTeamSpawnProfile(String teamName, int playerLimit, TeamSpawnProfile spawnProfile) {
            mapMutationRuntime.applyTeamSpawnProfile(teamName, playerLimit, spawnProfile);
        }

        @Override
        public void setMatchEndTeleportPoint(SpawnPointData point) {
            endTeleportRuntime.setMatchEndTeleportPoint(point);
        }

        @Override
        public boolean initiateStartVote(UUID initiator) {
            if (map.recoveryBlocked()) return false;
            map.beginAttempt();
            return voteRuntime.initiateStartVote(initiator);
        }

        @Override
        public boolean initiateEndVote(UUID initiator) {
            if (terminated()) return false;
            return voteRuntime.initiateEndVote(initiator);
        }

        @Override
        public boolean submitVoteResponse(UUID playerId, long voteId, boolean accepted) {
            if (terminated()) return false;
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

        @Override
        public void setSpectatorPreferredTeam(ServerPlayer player, String teamName) {
            teamMembershipCoordinator.setSpectatorPreferredTeam(player, teamName);
        }

        @Override
        public Optional<String> consumeSpectatorPreferredTeam(ServerPlayer player) {
            return teamMembershipCoordinator.consumeSpectatorPreferredTeam(player);
        }

        @Override
        public void requestRosterResync(ServerPlayer player) {
            requestRosterResyncAction.accept(player);
        }

        @Override
        public void requestRosterPreview(ServerPlayer player) {
            requestRosterPreviewAction.accept(player);
        }
    }

    private static final class TacticalMapActionPort extends MapActionPort implements CodTacticalTdmActionPort {
        private TacticalMapActionPort(
                TeamMatchPolicy policy,
                CodTdmMap map,
                CodTdmCombatRuntime combatRuntime,
                CodTdmTeamMembershipCoordinator teamMembershipCoordinator,
                CodTdmMapMutationRuntime mapMutationRuntime,
                CodTdmEndTeleportRuntime endTeleportRuntime,
                CodTdmVoteRuntime voteRuntime,
                CodTdmRespawnRuntime respawnRuntime,
                Consumer<ServerPlayer> requestRosterResyncAction,
                Consumer<ServerPlayer> requestRosterPreviewAction,
                Supplier<String> mapNameSupplier
        ) {
            super(
                    policy,
                    map,
                    combatRuntime,
                    teamMembershipCoordinator,
                    mapMutationRuntime,
                    endTeleportRuntime,
                    voteRuntime,
                    respawnRuntime,
                    requestRosterResyncAction,
                    requestRosterPreviewAction,
                    mapNameSupplier);
        }
    }
}
