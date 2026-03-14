package com.cdp.codpattern.app.tdm.port;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodTdmActionPort {
    void onPlayerDamaged(ServerPlayer player);

    void onPlayerKill(ServerPlayer killer, ServerPlayer victim);

    void onPlayerDead(ServerPlayer player, ServerPlayer killer);

    void leaveRoom(ServerPlayer player);

    void switchTeam(ServerPlayer player, String teamName);

    void joinTeam(String teamName, ServerPlayer player);

    void joinSpectator(ServerPlayer player);

    void respawnPlayerNow(ServerPlayer player);

    void syncToClient();

    void applyTeamSpawnData(String teamName, int playerLimit, List<SpawnPointData> spawnPoints);

    void setMatchEndTeleportPoint(SpawnPointData point);

    boolean initiateStartVote(UUID initiator);

    boolean initiateEndVote(UUID initiator);

    boolean submitVoteResponse(UUID playerId, long voteId, boolean accepted);

    void initializeReadyState(ServerPlayer player);

    boolean setPlayerReady(ServerPlayer player, boolean ready);

    void setSpectatorPreferredTeam(ServerPlayer player, String teamName);

    Optional<String> consumeSpectatorPreferredTeam(ServerPlayer player);
}
