package com.cdp.codpattern.compat.fpsmatch;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeCombatEventPort;
import com.cdp.codpattern.app.match.port.ModeEntityCombatEventPort;
import com.cdp.codpattern.app.match.port.ModeEntityLifecyclePort;
import com.cdp.codpattern.app.match.port.ModeInteractableObjectPort;
import com.cdp.codpattern.app.match.port.ModeKitDistributionPort;
import com.cdp.codpattern.app.match.port.ModeRoomLifecyclePort;
import com.cdp.codpattern.app.match.port.ModeRoomReadPort;
import com.cdp.codpattern.app.match.port.ModePlayerRuntimeStatePort;
import com.cdp.codpattern.app.match.port.ModeRespawnPolicyPort;
import com.cdp.codpattern.app.match.port.ModeRosterPort;
import com.cdp.codpattern.app.match.port.ModeRoomSummaryPort;
import com.cdp.codpattern.app.match.port.ModeRoomTickPort;
import com.cdp.codpattern.app.match.port.ModeRuntimeStatePort;
import com.cdp.codpattern.app.match.port.ReadyStatePort;
import com.cdp.codpattern.app.match.port.TeamRoomPort;
import com.cdp.codpattern.app.match.port.VoteControlPort;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FpsMatchGateway {
    boolean isInMatch(UUID playerId);

    Optional<ModeRoomLifecyclePort> findRoomLifecyclePort(RoomId roomId);

    Optional<ModeRoomLifecyclePort> findPlayerRoomLifecyclePort(ServerPlayer player);

    Optional<ModeRoomReadPort> findRoomReadPort(RoomId roomId);

    Optional<ModeRoomReadPort> findPlayerRoomReadPort(ServerPlayer player);

    Optional<TeamRoomPort> findRoomTeamPort(RoomId roomId);

    Optional<TeamRoomPort> findPlayerTeamRoomPort(ServerPlayer player);

    Optional<ReadyStatePort> findPlayerReadyStatePort(ServerPlayer player);

    Optional<VoteControlPort> findPlayerVoteControlPort(ServerPlayer player);

    Optional<ModeCombatEventPort> findPlayerCombatEventPort(ServerPlayer player);

    Optional<ModeEntityCombatEventPort> findRoomEntityCombatEventPort(RoomId roomId);

    Optional<ModeEntityCombatEventPort> findEntityCombatEventPort(Entity entity);

    Optional<ModeEntityLifecyclePort> findRoomEntityLifecyclePort(RoomId roomId);

    Optional<ModeEntityLifecyclePort> findEntityLifecyclePort(Entity entity);

    Optional<ModeRosterPort> findRoomRosterPort(RoomId roomId);

    Optional<ModeRosterPort> findPlayerRosterPort(ServerPlayer player);

    Optional<ModeKitDistributionPort> findPlayerKitDistributionPort(ServerPlayer player);

    Optional<ModeRuntimeStatePort> findRoomRuntimeStatePort(RoomId roomId);

    Optional<ModeRuntimeStatePort> findPlayerRuntimeStatePort(ServerPlayer player);

    Optional<ModeInteractableObjectPort> findPlayerInteractableObjectPort(ServerPlayer player);

    Optional<ModePlayerRuntimeStatePort> findRoomStatePort(RoomId roomId);

    Optional<ModePlayerRuntimeStatePort> findPlayerStatePort(ServerPlayer player);

    Optional<ModeRespawnPolicyPort> findPlayerRespawnPolicyPort(ServerPlayer player);

    List<ModeRoomSummaryPort> listRoomSummaryPorts();

    List<ModeRoomReadPort> listRoomReadPorts();

    List<ModeRoomTickPort> listRoomTickPorts();

    List<ModePlayerRuntimeStatePort> listRoomStatePorts();

    ModeEntityOwnershipRegistry entityOwnershipRegistry();

    void leaveCurrentMapIfDifferent(ServerPlayer player, String targetMapName);

    Optional<String> leaveCurrentMapIncludingSpectator(ServerPlayer player);
}
