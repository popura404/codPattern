package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.CodTdmTeamPersistenceSnapshot;
import com.cdp.codpattern.app.tdm.service.TeamBalanceService;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

final record CodTdmMapReadViews(
        Supplier<String> mapNameSupplier,
        Predicate<UUID> containsJoinedPlayerPredicate,
        Predicate<ServerPlayer> containsSpectatorPredicate,
        BooleanSupplier startedSupplier,
        Supplier<Map<String, Integer>> teamPlayerCountsSnapshotSupplier,
        IntSupplier maxPlayerCapacitySupplier,
        Predicate<String> hasTeamPredicate,
        Predicate<String> isTeamFullPredicate,
        Supplier<List<TeamBalanceService.TeamSnapshot>> teamBalanceSnapshotsSupplier,
        Supplier<AreaData> mapAreaSupplier,
        Supplier<String> dimensionIdSupplier,
        Supplier<List<CodTdmTeamPersistenceSnapshot>> teamPersistenceSnapshotsSupplier
) {

    String mapName() {
        return mapNameSupplier.get();
    }

    boolean containsJoinedPlayer(UUID playerId) {
        return containsJoinedPlayerPredicate.test(playerId);
    }

    boolean containsSpectator(ServerPlayer player) {
        return containsSpectatorPredicate.test(player);
    }

    boolean isStarted() {
        return startedSupplier.getAsBoolean();
    }

    Map<String, Integer> teamPlayerCountsSnapshot() {
        return teamPlayerCountsSnapshotSupplier.get();
    }

    int maxPlayerCapacity() {
        return maxPlayerCapacitySupplier.getAsInt();
    }

    boolean hasTeam(String teamName) {
        return hasTeamPredicate.test(teamName);
    }

    boolean isTeamFull(String teamName) {
        return isTeamFullPredicate.test(teamName);
    }

    Optional<String> chooseAutoJoinTeam(int maxTeamDiff) {
        return TeamBalanceService.chooseAutoJoinTeam(
                teamBalanceSnapshotsSupplier.get(),
                isTeamFullPredicate,
                maxTeamDiff
        );
    }

    boolean canJoinWithBalance(String teamName, int maxTeamDiff) {
        return TeamBalanceService.canJoinWithBalance(
                teamBalanceSnapshotsSupplier.get(),
                teamName,
                maxTeamDiff
        );
    }

    AreaData mapArea() {
        return mapAreaSupplier.get();
    }

    String dimensionId() {
        return dimensionIdSupplier.get();
    }

    List<CodTdmTeamPersistenceSnapshot> teamPersistenceSnapshots() {
        return teamPersistenceSnapshotsSupplier.get();
    }
}
