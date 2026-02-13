package com.cdp.codpattern.compat.fpsmatch.map;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

final class CodTdmMapMutationRuntime {
    private final BiConsumer<String, ServerPlayer> joinTeamAction;
    private final Consumer<ServerPlayer> joinSpectatorAction;
    private final Runnable syncToClientAction;
    private final BiConsumer<String, Integer> addTeamAction;
    private final Function<String, Optional<BaseTeam>> findTeamByNameAction;

    CodTdmMapMutationRuntime(
            BiConsumer<String, ServerPlayer> joinTeamAction,
            Consumer<ServerPlayer> joinSpectatorAction,
            Runnable syncToClientAction,
            BiConsumer<String, Integer> addTeamAction,
            Function<String, Optional<BaseTeam>> findTeamByNameAction
    ) {
        this.joinTeamAction = joinTeamAction;
        this.joinSpectatorAction = joinSpectatorAction;
        this.syncToClientAction = syncToClientAction;
        this.addTeamAction = addTeamAction;
        this.findTeamByNameAction = findTeamByNameAction;
    }

    void joinTeam(String teamName, ServerPlayer player) {
        joinTeamAction.accept(teamName, player);
    }

    void joinSpectator(ServerPlayer player) {
        joinSpectatorAction.accept(player);
    }

    void syncToClient() {
        syncToClientAction.run();
    }

    void applyTeamSpawnData(String teamName, int playerLimit, List<SpawnPointData> spawnPoints) {
        addTeamAction.accept(teamName, playerLimit);
        findTeamByNameAction.apply(teamName).ifPresent(team -> team.addAllSpawnPointData(spawnPoints));
    }
}
