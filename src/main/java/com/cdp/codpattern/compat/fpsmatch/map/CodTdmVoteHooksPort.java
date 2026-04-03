package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

interface CodTdmVoteHooksPort {
    Player getPlayer(UUID playerId);

    List<ServerPlayer> getJoinedPlayers();

    TdmGamePhase getPhase();

    boolean hasMatchEndTeleportPoint();

    String mapName();

    void startGame();

    void transitionToEndedFromVote();
}
