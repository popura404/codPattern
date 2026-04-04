package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

interface CodTdmTeamMembershipPort {
    void clearTransientPlayerState(UUID playerId);

    void clearPlayerCombatStats(UUID playerId);

    void removePlayerFromVote(UUID playerId);

    boolean hasMatchEndTeleportPoint();

    boolean teleportPlayerToMatchEndPoint(ServerPlayer player);

    void clearPlayerInventory(ServerPlayer player);

    void syncToClient();

    void setSpectatorPreferredTeam(UUID playerId, String teamName);

    Optional<String> getSpectatorPreferredTeam(UUID playerId);

    void clearSpectatorPreferredTeam(UUID playerId);

    boolean isWaitingPhase();

    boolean hasJoinedPlayers();

    void markDisconnected(UUID playerId, int graceTicks);

    void clearDisconnected(UUID playerId);

    Map<UUID, Integer> disconnectedGraceTimers();

    void removePlayerById(UUID playerId);

    boolean hasTeam(String teamName);

    boolean isTeamFull(String teamName);

    Optional<String> findTeamNameByPlayer(ServerPlayer player);

    boolean canSwitchWithBalance(String currentTeam, String targetTeam, int maxTeamDiff);

    void leaveTeam(ServerPlayer player);

    void joinTeam(String teamName, ServerPlayer player);

    ServerLevel serverLevel();

    String mapName();
}
