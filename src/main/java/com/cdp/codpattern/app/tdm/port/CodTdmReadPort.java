package com.cdp.codpattern.app.tdm.port;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.model.TeamDescriptor;
import com.cdp.codpattern.app.match.port.ModeRoomReadPort;
import com.cdp.codpattern.app.tdm.model.CodTdmTeamPersistenceSnapshot;
import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CodTdmReadPort extends ModeRoomReadPort {
    @Override
    default RoomId roomId() {
        return RoomId.of(gameType(), mapName());
    }

    @Override
    default String gameType() {
        return TdmGameTypes.CDP_TDM;
    }

    @Override
    default String modeDisplayNameKey() {
        return GameModeRegistry.getOrDefault(gameType()).displayNameKey();
    }

    @Override
    default List<TeamDescriptor> teamDescriptors() {
        return GameModeRegistry.getOrDefault(gameType()).teams();
    }

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

    List<CodTdmTeamPersistenceSnapshot> teamPersistenceSnapshots();

    Optional<SpawnPointData> matchEndTeleportPoint();
}
