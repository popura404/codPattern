package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tactical.port.CodTacticalTdmReadPort;
import com.cdp.codpattern.app.teammatch.TeamMatchPolicy;
import com.cdp.codpattern.app.tdm.model.CodTdmTeamPersistenceSnapshot;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

class CodTdmMapReadPortAdapter implements CodTdmReadPort {
    private final TeamMatchPolicy policy;
    private final CodTdmMapReadViews readViews;
    private final CodTdmMapReadRuntime readRuntime;

    protected CodTdmMapReadPortAdapter(
            TeamMatchPolicy policy,
            CodTdmMapReadViews readViews,
            CodTdmMapReadRuntime readRuntime
    ) {
        this.policy = policy;
        this.readViews = readViews;
        this.readRuntime = readRuntime;
    }

    static CodTdmReadPort fromMap(
            TeamMatchPolicy policy,
            CodTdmMap map,
            CodTdmMapReadRuntime readRuntime,
            Supplier<String> mapNameSupplier,
            BooleanSupplier startedSupplier
    ) {
        CodTdmMapReadViews readViews = new CodTdmMapReadViews(
                mapNameSupplier,
                map::checkGameHasPlayer,
                map::checkSpecHasPlayer,
                startedSupplier,
                () -> CodTdmMapTeamViews.teamPlayerCountsSnapshot(map),
                () -> CodTdmMapTeamViews.maxPlayerCapacity(map),
                teamName -> map.getMapTeams().checkTeam(teamName),
                teamName -> map.getMapTeams().testTeamIsFull(teamName),
                player -> CodTdmMapTeamViews.findTeamNameByPlayer(map, player),
                () -> CodTdmMapTeamViews.teamBalanceSnapshots(map),
                map::getMapArea,
                () -> map.getServerLevel().dimension().location().toString(),
                () -> CodTdmMapTeamViews.teamPersistenceSnapshots(map)
        );
        if (policy.tacticalCompatibilityPorts()) {
            return new TacticalReadPortAdapter(policy, readViews, readRuntime);
        }
        return new CodTdmMapReadPortAdapter(policy, readViews, readRuntime);
    }

    @Override
    public String gameType() {
        return policy.gameType();
    }

    @Override
    public String mapName() {
        return readViews.mapName();
    }

    @Override
    public boolean containsJoinedPlayer(UUID playerId) {
        return readViews.containsJoinedPlayer(playerId);
    }

    @Override
    public boolean containsSpectator(ServerPlayer player) {
        return readViews.containsSpectator(player);
    }

    @Override
    public boolean isStarted() {
        return readViews.isStarted();
    }

    @Override
    public String phaseName() {
        return readRuntime.phaseName();
    }

    @Override
    public boolean isPlayingPhase() {
        return readRuntime.isPlayingPhase();
    }

    @Override
    public boolean isWaitingPhase() {
        return readRuntime.isWaitingPhase();
    }

    @Override
    public boolean canDealDamage() {
        return readRuntime.canDealDamage();
    }

    @Override
    public boolean isPlayerInvincible(UUID playerId) {
        return readRuntime.isPlayerInvincible(playerId);
    }

    @Override
    public boolean hasMatchEndTeleportPoint() {
        return readRuntime.hasMatchEndTeleportPoint();
    }

    @Override
    public int getRemainingTimeTicks() {
        return readRuntime.remainingTimeTicks();
    }

    @Override
    public Map<String, Integer> getTeamScoresSnapshot() {
        return readRuntime.teamScoresSnapshot();
    }

    @Override
    public Map<String, Integer> getTeamPlayerCountsSnapshot() {
        return readViews.teamPlayerCountsSnapshot();
    }

    @Override
    public int getMaxPlayerCapacity() {
        return readViews.maxPlayerCapacity();
    }

    @Override
    public boolean hasTeam(String teamName) {
        return readViews.hasTeam(teamName);
    }

    @Override
    public boolean isTeamFull(String teamName) {
        return readViews.isTeamFull(teamName);
    }

    @Override
    public Optional<String> findTeamNameByPlayer(ServerPlayer player) {
        return readViews.findTeamNameByPlayer(player);
    }

    @Override
    public Optional<String> chooseAutoJoinTeam(int maxTeamDiff) {
        return readViews.chooseAutoJoinTeam(maxTeamDiff);
    }

    @Override
    public boolean canJoinWithBalance(String teamName, int maxTeamDiff) {
        return readViews.canJoinWithBalance(teamName, maxTeamDiff);
    }

    @Override
    public AreaData mapArea() {
        return readViews.mapArea();
    }

    @Override
    public String dimensionId() {
        return readViews.dimensionId();
    }

    @Override
    public List<CodTdmTeamPersistenceSnapshot> teamPersistenceSnapshots() {
        return readViews.teamPersistenceSnapshots();
    }

    @Override
    public Optional<SpawnPointData> matchEndTeleportPoint() {
        return readRuntime.matchEndTeleportPoint();
    }

    private static final class TacticalReadPortAdapter extends CodTdmMapReadPortAdapter
            implements CodTacticalTdmReadPort {
        private TacticalReadPortAdapter(
                TeamMatchPolicy policy,
                CodTdmMapReadViews readViews,
                CodTdmMapReadRuntime readRuntime
        ) {
            super(policy, readViews, readRuntime);
        }
    }
}
