package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.model.TeamDescriptor;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ModeRoomReadPort {
    RoomId roomId();

    String gameType();

    String modeDisplayNameKey();

    List<TeamDescriptor> teamDescriptors();

    String mapName();

    boolean containsJoinedPlayer(UUID playerId);

    boolean containsSpectator(ServerPlayer player);

    boolean isStarted();

    String phaseName();

    boolean isPlayingPhase();

    boolean isWaitingPhase();

    boolean canDealDamage();

    boolean isPlayerInvincible(UUID playerId);

    boolean hasMatchEndTeleportPoint();

    int getRemainingTimeTicks();

    Map<String, Integer> getTeamScoresSnapshot();

    Map<String, Integer> getTeamPlayerCountsSnapshot();

    int getMaxPlayerCapacity();

    boolean hasTeam(String teamName);

    boolean isTeamFull(String teamName);

    Optional<String> findTeamNameByPlayer(ServerPlayer player);

    Optional<String> chooseAutoJoinTeam(int maxTeamDiff);

    boolean canJoinWithBalance(String teamName, int maxTeamDiff);

    AreaData mapArea();

    String dimensionId();

    Optional<SpawnPointData> matchEndTeleportPoint();
}
