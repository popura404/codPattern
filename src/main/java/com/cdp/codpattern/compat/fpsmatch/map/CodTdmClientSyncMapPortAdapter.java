package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final record CodTdmClientSyncMapPortAdapter(
        Supplier<String> mapNameSupplier,
        Supplier<Map<String, List<PlayerInfo>>> teamPlayersSupplier,
        Supplier<List<ServerPlayer>> joinedPlayersSupplier
) implements CodTdmClientSyncPort {

    @Override
    public String mapName() {
        return mapNameSupplier.get();
    }

    @Override
    public Map<String, List<PlayerInfo>> getTeamPlayers() {
        return teamPlayersSupplier.get();
    }

    @Override
    public List<ServerPlayer> getJoinedPlayers() {
        return joinedPlayersSupplier.get();
    }
}
