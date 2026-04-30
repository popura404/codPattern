package com.cdp.codpattern.compat.fpsmatch;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeCombatEventPort;
import com.cdp.codpattern.app.match.port.ModeKitDistributionPort;
import com.cdp.codpattern.app.match.port.ModeRoomLifecyclePort;
import com.cdp.codpattern.app.match.port.ModeRoomReadPort;
import com.cdp.codpattern.app.match.port.ModeRosterPort;
import com.cdp.codpattern.app.match.port.ModeRoomSummaryPort;
import com.cdp.codpattern.app.match.port.ReadyStatePort;
import com.cdp.codpattern.app.match.port.TeamRoomPort;
import com.cdp.codpattern.app.match.port.VoteControlPort;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class NoopFpsMatchGateway implements FpsMatchGateway {
    @Override
    public boolean isInMatch(UUID playerId) {
        return false;
    }

    @Override
    public Optional<ModeRoomLifecyclePort> findRoomLifecyclePort(RoomId roomId) {
        return Optional.empty();
    }

    @Override
    public Optional<ModeRoomLifecyclePort> findPlayerRoomLifecyclePort(ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public Optional<ModeRoomReadPort> findRoomReadPort(RoomId roomId) {
        return Optional.empty();
    }

    @Override
    public Optional<ModeRoomReadPort> findPlayerRoomReadPort(ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public Optional<TeamRoomPort> findRoomTeamPort(RoomId roomId) {
        return Optional.empty();
    }

    @Override
    public Optional<TeamRoomPort> findPlayerTeamRoomPort(ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public Optional<ReadyStatePort> findPlayerReadyStatePort(ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public Optional<VoteControlPort> findPlayerVoteControlPort(ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public Optional<ModeCombatEventPort> findPlayerCombatEventPort(ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public Optional<ModeRosterPort> findRoomRosterPort(RoomId roomId) {
        return Optional.empty();
    }

    @Override
    public Optional<ModeRosterPort> findPlayerRosterPort(ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public Optional<ModeKitDistributionPort> findPlayerKitDistributionPort(ServerPlayer player) {
        return Optional.empty();
    }

    @Override
    public List<ModeRoomSummaryPort> listRoomSummaryPorts() {
        return List.of();
    }

    @Override
    public List<ModeRoomReadPort> listRoomReadPorts() {
        return List.of();
    }

    @Override
    public void leaveCurrentMapIfDifferent(ServerPlayer player, String targetMapName) {
    }

    @Override
    public Optional<String> leaveCurrentMapIncludingSpectator(ServerPlayer player) {
        return Optional.empty();
    }
}
