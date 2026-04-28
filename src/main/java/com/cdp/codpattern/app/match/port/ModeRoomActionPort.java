package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.JoinRoomRequest;
import com.cdp.codpattern.app.match.model.JoinRoomResult;
import com.cdp.codpattern.app.match.model.LeaveRoomResult;
import com.cdp.codpattern.app.match.model.RoomId;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.TeamSpawnProfile;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public interface ModeRoomActionPort extends ModeRoomLifecyclePort, ReadyStatePort, VoteControlPort {
    @Override
    RoomId roomId();

    @Override
    String gameType();

    @Override
    default String mapName() {
        RoomId roomId = roomId();
        return roomId == null ? "" : roomId.mapName();
    }

    @Override
    default String modeDisplayNameKey() {
        return GameModeRegistry.getOrDefault(gameType()).displayNameKey();
    }

    default JoinRoomResult join(ServerPlayer player, JoinRoomRequest request) {
        if (player == null) {
            return JoinRoomResult.failure(roomId(), "PLAYER_MISSING", "");
        }
        JoinRoomRequest resolvedRequest = request == null ? JoinRoomRequest.autoTeam() : request;
        if (resolvedRequest.spectator()) {
            joinSpectator(player);
            return JoinRoomResult.success(roomId(), "OK");
        }
        Optional<String> teamName = resolvedRequest.preferredTeamName();
        if (teamName.isEmpty()) {
            return JoinRoomResult.failure(roomId(), "TEAM_REQUIRED", "");
        }
        joinTeam(teamName.get(), player);
        return JoinRoomResult.success(roomId(), "OK");
    }

    void onPlayerDamaged(ServerPlayer player);

    void onPlayerKill(ServerPlayer killer, ServerPlayer victim);

    void onPlayerDead(ServerPlayer player, ServerPlayer killer);

    void leaveRoom(ServerPlayer player);

    default LeaveRoomResult leave(ServerPlayer player) {
        if (player == null) {
            return LeaveRoomResult.failure(roomId(), "PLAYER_MISSING", "");
        }
        leaveRoom(player);
        return LeaveRoomResult.success(roomId(), "OK");
    }

    void switchTeam(ServerPlayer player, String teamName);

    void joinTeam(String teamName, ServerPlayer player);

    void joinSpectator(ServerPlayer player);

    void respawnPlayerNow(ServerPlayer player);

    void syncToClient();

    void applyTeamSpawnProfile(String teamName, int playerLimit, TeamSpawnProfile spawnProfile);

    void setMatchEndTeleportPoint(SpawnPointData point);

    boolean initiateStartVote(UUID initiator);

    boolean initiateEndVote(UUID initiator);

    boolean submitVoteResponse(UUID playerId, long voteId, boolean accepted);

    void initializeReadyState(ServerPlayer player);

    boolean setPlayerReady(ServerPlayer player, boolean ready);

    void setSpectatorPreferredTeam(ServerPlayer player, String teamName);

    Optional<String> consumeSpectatorPreferredTeam(ServerPlayer player);

    default void requestRosterResync(ServerPlayer player) {
    }

    default void requestRosterPreview(ServerPlayer player) {
    }
}
