package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.DeathCamData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class CodTdmPlayerRuntimeState {
    private final Map<UUID, Integer> respawnTimers = new HashMap<>();
    private final Set<UUID> invinciblePlayers = new HashSet<>();
    private final Map<UUID, Integer> invincibilityTimers = new HashMap<>();
    private final Map<UUID, DeathCamData> deathCamPlayers = new HashMap<>();
    private final Map<UUID, Integer> playerKills = new HashMap<>();
    private final Map<UUID, Integer> playerDeaths = new HashMap<>();
    private final Map<UUID, Integer> currentKillStreaks = new HashMap<>();
    private final Map<UUID, Integer> maxKillStreaks = new HashMap<>();
    private final Map<UUID, Integer> combatRegenCooldowns = new HashMap<>();
    private final Map<UUID, Integer> respawnRetryCounts = new HashMap<>();
    private final Map<UUID, Boolean> readyStates = new HashMap<>();
    private final Map<UUID, String> spectatorPreferredJoinTeams = new HashMap<>();
    private final Map<UUID, Integer> disconnectedGraceTimers = new HashMap<>();

    Map<UUID, Integer> respawnTimers() {
        return respawnTimers;
    }

    Set<UUID> invinciblePlayers() {
        return invinciblePlayers;
    }

    Map<UUID, Integer> invincibilityTimers() {
        return invincibilityTimers;
    }

    Map<UUID, DeathCamData> deathCamPlayers() {
        return deathCamPlayers;
    }

    Map<UUID, Integer> playerKills() {
        return playerKills;
    }

    Map<UUID, Integer> playerDeaths() {
        return playerDeaths;
    }

    Map<UUID, Integer> currentKillStreaks() {
        return currentKillStreaks;
    }

    Map<UUID, Integer> maxKillStreaks() {
        return maxKillStreaks;
    }

    Map<UUID, Integer> combatRegenCooldowns() {
        return combatRegenCooldowns;
    }

    int incrementRespawnRetryCount(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return respawnRetryCounts.merge(playerId, 1, Integer::sum);
    }

    void clearRespawnRetryCount(UUID playerId) {
        if (playerId == null) {
            return;
        }
        respawnRetryCounts.remove(playerId);
    }

    Map<UUID, Boolean> readyStates() {
        return readyStates;
    }

    Map<UUID, Integer> disconnectedGraceTimers() {
        return disconnectedGraceTimers;
    }

    void markDisconnected(UUID playerId, int graceTicks) {
        if (playerId == null) {
            return;
        }
        disconnectedGraceTimers.put(playerId, Math.max(1, graceTicks));
    }

    void clearDisconnected(UUID playerId) {
        if (playerId == null) {
            return;
        }
        disconnectedGraceTimers.remove(playerId);
    }

    void setSpectatorPreferredJoinTeam(UUID playerId, String teamName) {
        if (playerId == null || teamName == null || teamName.isBlank()) {
            return;
        }
        spectatorPreferredJoinTeams.put(playerId, teamName);
    }

    Optional<String> getSpectatorPreferredJoinTeam(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(spectatorPreferredJoinTeams.get(playerId));
    }

    void clearSpectatorPreferredJoinTeam(UUID playerId) {
        if (playerId == null) {
            return;
        }
        spectatorPreferredJoinTeams.remove(playerId);
    }

    void clearRoundTransientState() {
        deathCamPlayers.clear();
        respawnTimers.clear();
        combatRegenCooldowns.clear();
        respawnRetryCounts.clear();
    }

    void clearAll() {
        clearRoundTransientState();
        invinciblePlayers.clear();
        invincibilityTimers.clear();
        playerKills.clear();
        playerDeaths.clear();
        currentKillStreaks.clear();
        maxKillStreaks.clear();
        readyStates.clear();
        spectatorPreferredJoinTeams.clear();
        disconnectedGraceTimers.clear();
    }

    void clearTransientPlayerState(UUID playerId) {
        respawnTimers.remove(playerId);
        invinciblePlayers.remove(playerId);
        invincibilityTimers.remove(playerId);
        deathCamPlayers.remove(playerId);
        combatRegenCooldowns.remove(playerId);
        respawnRetryCounts.remove(playerId);
        spectatorPreferredJoinTeams.remove(playerId);
        disconnectedGraceTimers.remove(playerId);
    }

    void removePlayerCombatStats(UUID playerId) {
        playerKills.remove(playerId);
        playerDeaths.remove(playerId);
        currentKillStreaks.remove(playerId);
        maxKillStreaks.remove(playerId);
    }

    boolean isInvincible(UUID playerId) {
        return invinciblePlayers.contains(playerId);
    }
}
