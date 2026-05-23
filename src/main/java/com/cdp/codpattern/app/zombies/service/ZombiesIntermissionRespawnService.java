package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure MVP3 intermission respawn decision and player-state preparation service.
 */
public final class ZombiesIntermissionRespawnService {
    private final ZombiesPlayerStateService playerStateService;
    private final ZombiesBuffService buffService;

    public ZombiesIntermissionRespawnService(
            ZombiesPlayerStateService playerStateService,
            ZombiesBuffService buffService
    ) {
        this.playerStateService = Objects.requireNonNull(playerStateService, "playerStateService");
        this.buffService = Objects.requireNonNull(buffService, "buffService");
    }

    public ZombiesServiceResult<IntermissionRespawnDecision> selectRespawnCandidates(
            List<UUID> memberIds,
            long currentTick,
            long offlineGraceTicks
    ) {
        return selectRespawnCandidates(memberIds, playerStateService, currentTick, offlineGraceTicks);
    }

    public static ZombiesServiceResult<IntermissionRespawnDecision> selectRespawnCandidates(
            List<UUID> memberIds,
            ZombiesPlayerStateService playerStateService,
            long currentTick,
            long offlineGraceTicks
    ) {
        Objects.requireNonNull(playerStateService, "playerStateService");
        List<UUID> members = normalizeMemberIds(memberIds);
        if (members.size() <= 1) {
            return ZombiesServiceResult.success(new IntermissionRespawnDecision(members, false, List.of()));
        }

        boolean hasAliveMember = false;
        for (UUID memberId : members) {
            Optional<ZombiesPlayerRuntimeState> state = playerStateService.get(memberId);
            if (state.filter(value -> isAliveForRespawnPrerequisite(value, currentTick, offlineGraceTicks)).isPresent()) {
                hasAliveMember = true;
                break;
            }
        }
        if (!hasAliveMember) {
            return ZombiesServiceResult.success(new IntermissionRespawnDecision(members, false, List.of()));
        }

        List<UUID> candidates = new ArrayList<>();
        for (UUID memberId : members) {
            Optional<ZombiesPlayerRuntimeState> state = playerStateService.get(memberId);
            if (state.filter(ZombiesIntermissionRespawnService::isRespawnCandidate).isPresent()) {
                candidates.add(memberId);
            }
        }
        return ZombiesServiceResult.success(new IntermissionRespawnDecision(members, true, candidates));
    }

    public ZombiesServiceResult<IntermissionRespawnStateChange> prepareStateForRespawn(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        ZombiesServiceResult<ZombiesBuffService.BuffClearResult> clearResult =
                buffService.clearBuffsForDeathOrRevive(playerId);
        if (!clearResult.success()) {
            return ZombiesServiceResult.failure(clearResult.code(), clearResult.params(), clearResult.logMessage());
        }

        playerStateService.markAlive(playerId);
        ZombiesBuffService.BuffClearResult clear = clearResult.value()
                .orElse(new ZombiesBuffService.BuffClearResult(0, false));
        return ZombiesServiceResult.success(new IntermissionRespawnStateChange(
                playerId,
                clear.clearedBuffs(),
                clear.halvedReserveAmmo()));
    }

    public ZombiesServiceResult<IntermissionRespawnStateChange> markRespawned(UUID playerId) {
        return prepareStateForRespawn(playerId);
    }

    private static boolean isAliveForRespawnPrerequisite(
            ZombiesPlayerRuntimeState state,
            long currentTick,
            long offlineGraceTicks
    ) {
        if (state == null || !state.lifeState().isAlive() || state.connectionState().isLeft()) {
            return false;
        }
        if (state.connectionState().isOnline()) {
            return true;
        }
        return state.offlineSinceTick()
                .stream()
                .anyMatch(offlineSinceTick -> currentTick - offlineSinceTick <= Math.max(0L, offlineGraceTicks));
    }

    private static boolean isRespawnCandidate(ZombiesPlayerRuntimeState state) {
        return state.connectionState().isOnline() && state.lifeState().isDeadSpectating();
    }

    private static List<UUID> normalizeMemberIds(List<UUID> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> normalized = new LinkedHashSet<>();
        for (UUID memberId : memberIds) {
            if (memberId != null) {
                normalized.add(memberId);
            }
        }
        return List.copyOf(normalized);
    }

    public record IntermissionRespawnDecision(
            List<UUID> memberIds,
            boolean hasAliveMember,
            List<UUID> respawnPlayerIds
    ) {
        public IntermissionRespawnDecision {
            memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
            respawnPlayerIds = respawnPlayerIds == null ? List.of() : List.copyOf(respawnPlayerIds);
        }

        public int memberCount() {
            return memberIds.size();
        }

        public boolean shouldRespawnAny() {
            return !respawnPlayerIds.isEmpty();
        }
    }

    public record IntermissionRespawnStateChange(
            UUID playerId,
            int clearedBuffs,
            boolean halvedReserveAmmo
    ) {
        public IntermissionRespawnStateChange {
            Objects.requireNonNull(playerId, "playerId");
            clearedBuffs = Math.max(0, clearedBuffs);
        }
    }
}
