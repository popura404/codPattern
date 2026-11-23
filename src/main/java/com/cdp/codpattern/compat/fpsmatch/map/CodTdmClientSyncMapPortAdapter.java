package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.Function;

final record CodTdmClientSyncMapPortAdapter(
        Supplier<String> roomKeySupplier,
        Function<Boolean, Map<String, List<PlayerInfo>>> teamPlayersSupplier,
        Supplier<List<ServerPlayer>> joinedPlayersSupplier,
        Supplier<List<ServerPlayer>> spectatorPlayersSupplier
) implements CodTdmClientSyncPort {

    @Override
    public String roomKey() {
        return roomKeySupplier.get();
    }

    @Override
    public Map<String, List<PlayerInfo>> getTeamPlayers(boolean includeOffline) {
        return teamPlayersSupplier.apply(includeOffline);
    }

    @Override
    public List<ServerPlayer> getJoinedPlayers() {
        return joinedPlayersSupplier.get();
    }

    @Override
    public List<ServerPlayer> getSpectatorPlayers() {
        return spectatorPlayersSupplier.get();
    }
}
