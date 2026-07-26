package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.match.ModeRoomBackedMap;
import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchemas;
import com.cdp.codpattern.app.match.editor.ModeObjectData;
import com.cdp.codpattern.app.match.editor.ModePointData;
import com.cdp.codpattern.app.match.port.ModeMapEditPort;
import com.cdp.codpattern.app.match.port.ModeRoomActionPort;
import com.cdp.codpattern.app.match.port.ModeRoomReadPort;
import com.cdp.codpattern.app.teammatch.TeamMatchPolicy;
import com.cdp.codpattern.app.teammatch.TeamMatchRuntime;
import com.cdp.codpattern.app.tdm.model.TdmTeamMatchPolicies;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.data.SpawnSelectionReason;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.map.EndTeleportMap;
import com.phasetranscrystal.fpsmatch.core.map.GiveStartKitsMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * COD Team Deathmatch 地图核心类
 * 实现完整的团队死斗游戏逻辑
 */
public class CodTdmMap extends BaseMap implements GiveStartKitsMap<CodTdmMap>, EndTeleportMap<CodTdmMap>, ModeRoomBackedMap {
    private final CodTdmMapLifecycleRuntime lifecycleRuntime;
    private final CodTdmActionPort actionPort;
    private final CodTdmReadPort readPort;
    private final TeamMatchRuntime teamMatchRuntime;

    // 地图配置态
    private final CodTdmKitsRuntime kitsRuntime;

    /**
     * 构造函数
     */
    public CodTdmMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        this(serverLevel, mapName, areaData, TdmTeamMatchPolicies.frontline());
    }

    protected CodTdmMap(
            ServerLevel serverLevel,
            String mapName,
            AreaData areaData,
            TeamMatchPolicy policy
    ) {
        super(serverLevel, mapName, areaData);
        CodTdmMapRuntimeAssembly.BootstrapResult bootstrapResult = CodTdmMapRuntimeAssembly.bootstrap(
                policy,
                this,
                player -> CodTdmMap.super.leave(player),
                () -> mapName,
                () -> isStart,
                () -> this.isStart = true,
                () -> this.isStart = false
        );
        this.kitsRuntime = bootstrapResult.kitsRuntime();
        this.lifecycleRuntime = bootstrapResult.lifecycleRuntime();
        this.actionPort = bootstrapResult.actionPort();
        this.readPort = bootstrapResult.readPort();
        this.teamMatchRuntime = new TeamMatchRuntime(policy, readPort, actionPort);
    }

    // 基础方法覆盖

    @Override
    public void tick() {
        lifecycleRuntime.tick();
    }

    @Override
    public void syncToClient() {
        lifecycleRuntime.syncToClient();
    }

    @Override
    public String getGameType() {
        return teamMatchRuntime.gameType();
    }

    @Override
    public boolean teleportPlayerToSpawnPoint(ServerPlayer player, SpawnSelectionReason reason) {
        return teamMatchRuntime.teleportPlayerToSpawnPoint(
                reason,
                kind -> super.teleportPlayerToSpawnPoint(player, kind));
    }

    @Override
    public void startGame() {
        lifecycleRuntime.startGame();
    }

    @Override
    public void victory() {
        lifecycleRuntime.transitionToEnded();
    }

    @Override
    public boolean victoryGoal() {
        return lifecycleRuntime.hasReachedVictoryGoal();
    }

    @Override
    public void resetGame() {
        lifecycleRuntime.resetGame();
    }

    /**
     * 玩家离开房间（不是换队）。
     * 会尝试传送到比赛结束点，然后移除房间内状态。
     */
    @Override
    public void leave(ServerPlayer player) {
        lifecycleRuntime.leaveRoom(player);
    }

    @Override
    public void onPlayerLoggedOut(ServerPlayer player) {
        lifecycleRuntime.handleUnexpectedDisconnect(player);
    }

    @Override
    public void onPlayerLoggedIn(ServerPlayer player) {
        lifecycleRuntime.handleReconnect(player);
    }

    /**
     * 发放玩家装备 (基于背包系统)
     */
    @Override
    public void givePlayerKits(ServerPlayer player) {
        kitsRuntime.givePlayerKits(player);
    }

    public void givePlayerKitsSilently(ServerPlayer player) {
        kitsRuntime.givePlayerKitsSilently(player);
    }

    @Override
    public void clearPlayerInventory(ServerPlayer player) {
        super.clearPlayerInventory(player);
        ThrowableInventoryService.clearRuntime(player, true);
    }

    // GiveStartKitsMap 实现

    @Override
    public ArrayList<ItemStack> getKits(BaseTeam team) {
        return kitsRuntime.getOrCreateKits(team.name);
    }

    @Override
    public void addKits(BaseTeam team, ItemStack itemStack) {
        kitsRuntime.addKit(team.name, itemStack);
    }

    @Override
    public void setStartKits(Map<String, ArrayList<ItemStack>> kits) {
        kitsRuntime.setStartKits(kits);
    }

    @Override
    public void setAllTeamKits(ItemStack itemStack) {
        kitsRuntime.setAllTeamKits(itemStack);
    }

    @Override
    public Map<String, List<ItemStack>> getStartKits() {
        return kitsRuntime.startKitsSnapshot();
    }

    @Override
    public CodTdmMap getMap() {
        return this;
    }

    // EndTeleportMap 实现

    @Override
    public void setMatchEndTeleportPoint(SpawnPointData data) {
        lifecycleRuntime.setMatchEndTeleportPoint(data);
    }

    @Override
    public ModeRoomHandle roomHandle() {
        return teamMatchRuntime.createRoomHandle(
                new CodTdmCombatEventAdapter(readPort, actionPort),
                mapEditPort(readPort, actionPort));
    }

    private ModeMapEditPort mapEditPort(ModeRoomReadPort readPort, ModeRoomActionPort actionPort) {
        return new ModeMapEditPort() {
            @Override
            public boolean supportsPointLayer(String layerKey) {
                return ModeMapEditorSchemas.supportsPointLayer(getGameType(), layerKey)
                        && ModeMapEditorSchemas.legacySpawnPointKind(layerKey).isPresent();
            }

            @Override
            public List<ModePointData> pointLayerPoints(String teamName, String layerKey) {
                return findTeam(teamName)
                        .flatMap(team -> legacyKind(layerKey).map(kind -> team.getSpawnPointsData(kind).stream()
                                .map(point -> ModePointData.fromSpawnPointData(layerKey, point))
                                .toList()))
                        .orElse(List.of());
            }

            @Override
            public boolean addPointLayerPoint(String teamName, ModePointData point) {
                if (point == null || !supportsPointLayer(point.layerKey())) {
                    return false;
                }
                Optional<BaseTeam> team = findTeam(teamName);
                Optional<SpawnPointKind> kind = legacyKind(point.layerKey());
                if (team.isEmpty() || kind.isEmpty()) {
                    return false;
                }
                boolean added = team.get().addSpawnPointDataIfAbsent(point.toSpawnPointData(kind.get()));
                if (added && isStart && kind.get() == SpawnPointKind.INITIAL) {
                    team.get().assignNextSpawnPoints(SpawnPointKind.INITIAL);
                }
                return added;
            }

            @Override
            public Optional<ModePointData> removePointLayerPoint(String teamName, String layerKey, int index) {
                Optional<BaseTeam> team = findTeam(teamName);
                Optional<SpawnPointKind> kind = legacyKind(layerKey);
                if (team.isEmpty() || kind.isEmpty()) {
                    return Optional.empty();
                }
                Optional<SpawnPointData> removed = team.get().removeSpawnPointData(kind.get(), index);
                if (removed.isEmpty()) {
                    return Optional.empty();
                }
                team.get().clearPlayerSpawnPointAssignments();
                if (kind.get() == SpawnPointKind.INITIAL && !team.get().getSpawnPointsData(kind.get()).isEmpty()) {
                    team.get().assignNextSpawnPoints(SpawnPointKind.INITIAL);
                }
                return removed.map(point -> ModePointData.fromSpawnPointData(layerKey, point));
            }

            @Override
            public void replacePointLayerPoints(String teamName, String layerKey, List<ModePointData> points) {
                Optional<BaseTeam> team = findTeam(teamName);
                Optional<SpawnPointKind> kind = legacyKind(layerKey);
                if (team.isEmpty() || kind.isEmpty()) {
                    return;
                }
                team.get().resetSpawnPointData(kind.get());
                if (points != null) {
                    points.forEach(point -> team.get().addSpawnPointData(
                            point == null ? null : point.toSpawnPointData(kind.get())));
                }
                team.get().clearPlayerSpawnPointAssignments();
                if (isStart && kind.get() == SpawnPointKind.INITIAL) {
                    team.get().assignNextSpawnPoints(SpawnPointKind.INITIAL);
                }
            }

            @Override
            public boolean supportsObjectFeature(String featureKey) {
                return ModeMapEditorSchemas.supportsMatchEndTeleport(getGameType())
                        && ModeMapEditorSchemas.MATCH_END_TELEPORT.equals(featureKey);
            }

            @Override
            public Optional<ModeObjectData> objectFeature(String featureKey) {
                if (!supportsObjectFeature(featureKey)) {
                    return Optional.empty();
                }
                return readPort.matchEndTeleportPoint()
                        .map(point -> ModeObjectData.fromSpawnPointData(featureKey, point));
            }

            @Override
            public void setObjectFeature(String featureKey, ModeObjectData objectData) {
                if (!supportsObjectFeature(featureKey)) {
                    return;
                }
                actionPort.setMatchEndTeleportPoint(objectData == null ? null : objectData.toSpawnPointData());
            }
        };
    }

    private Optional<BaseTeam> findTeam(String teamName) {
        return getMapTeams().getTeamByName(teamName);
    }

    private Optional<SpawnPointKind> legacyKind(String layerKey) {
        return ModeMapEditorSchemas.resolvePointLayerKey(getGameType(), layerKey)
                .flatMap(ModeMapEditorSchemas::legacySpawnPointKind);
    }

    public CodTdmActionPort actionPort() {
        return actionPort;
    }

    public CodTdmReadPort readPort() {
        return readPort;
    }

    public TeamMatchPolicy teamMatchPolicy() {
        return teamMatchRuntime.policy();
    }
}
