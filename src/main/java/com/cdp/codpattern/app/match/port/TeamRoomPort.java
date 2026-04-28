package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.TeamDescriptor;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TeamRoomPort {
    List<TeamDescriptor> teamDescriptors();

    Map<String, Integer> teamPlayerCountsSnapshot();

    boolean hasTeam(String teamName);

    boolean isTeamFull(String teamName);

    Optional<String> findTeamNameByPlayer(ServerPlayer player);

    void switchTeam(ServerPlayer player, String teamName);
}
