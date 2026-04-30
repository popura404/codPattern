package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.ModeRoomBackedMap;
import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchemas;
import com.cdp.codpattern.app.match.editor.ModeObjectData;
import com.cdp.codpattern.app.match.editor.ModePointData;
import com.cdp.codpattern.app.match.model.JoinRoomRequest;
import com.cdp.codpattern.app.match.model.JoinRoomResult;
import com.cdp.codpattern.app.match.model.LeaveRoomResult;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeMapEditPort;
import com.cdp.codpattern.app.match.port.ModeRoomActionPort;
import com.cdp.codpattern.app.match.port.ModeRoomLifecyclePort;
import com.cdp.codpattern.app.match.port.ModeRoomReadPort;
import com.cdp.codpattern.app.match.port.ModeRosterPort;
import com.cdp.codpattern.app.match.port.TeamRoomPort;
import com.cdp.codpattern.app.tdm.service.RoomFoodLockService;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.map.EndTeleportMap;
import com.phasetranscrystal.fpsmatch.core.map.GiveStartKitsMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * COD Team Deathmatch 地图核心类
 * 实现完整的团队死斗游戏逻辑
 */
public class CodTdmMap extends BaseMap implements GiveStartKitsMap<CodTdmMap>, EndTeleportMap<CodTdmMap>, ModeRoomBackedMap {
    private static final String CODE_PHASE_LOCKED = "PHASE_LOCKED";
    private static final String CODE_TEAM_NOT_FOUND = "TEAM_NOT_FOUND";
    private static final String CODE_TEAM_FULL = "TEAM_FULL";
    private static final String CODE_BALANCE_EXCEEDED = "TEAM_BALANCE_EXCEEDED";
    private static final String CODE_UNKNOWN = "UNKNOWN";

    private final CodTdmMapLifecycleRuntime lifecycleRuntime;
    private final CodTdmActionPort actionPort;
    private final CodTdmReadPort readPort;

    // 地图配置态
    private final CodTdmKitsRuntime kitsRuntime;

    /**
     * 构造函数
     */
    public CodTdmMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        super(serverLevel, mapName, areaData);
        CodTdmMapRuntimeAssembly.BootstrapResult bootstrapResult = CodTdmMapRuntimeAssembly.bootstrap(
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
        return BuiltInGameModes.FRONTLINE;
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
        return createRoomHandle(readPort, actionPort);
    }

    protected ModeRoomHandle createRoomHandle(ModeRoomReadPort readPort, CodTdmActionPort actionPort) {
        return new ModeRoomHandle(
                readPort.roomId(),
                readPort,
                tdmLifecyclePort(readPort, actionPort),
                Optional.of(actionPort),
                Optional.of(teamPort(readPort, actionPort)),
                Optional.of(actionPort),
                Optional.of(actionPort),
                Optional.of(new CodTdmCombatEventAdapter(readPort, actionPort)),
                Optional.of(rosterPort(actionPort)),
                Optional.of(mapEditPort(readPort, actionPort)),
                Optional.empty());
    }

    private static ModeRoomLifecyclePort tdmLifecyclePort(ModeRoomReadPort readPort, ModeRoomActionPort actionPort) {
        return new ModeRoomLifecyclePort() {
            @Override
            public RoomId roomId() {
                return readPort.roomId();
            }

            @Override
            public String gameType() {
                return readPort.gameType();
            }

            @Override
            public String mapName() {
                return readPort.mapName();
            }

            @Override
            public String modeDisplayNameKey() {
                return readPort.modeDisplayNameKey();
            }

            @Override
            public JoinRoomResult join(ServerPlayer player, JoinRoomRequest request) {
                if (player == null) {
                    return JoinRoomResult.failure(roomId(), "PLAYER_MISSING", "");
                }
                if (readPort.containsJoinedPlayer(player.getUUID()) || readPort.containsSpectator(player)) {
                    return JoinRoomResult.success(roomId(), "ALREADY_JOINED");
                }
                if (!readPort.isWaitingPhase()) {
                    return JoinRoomResult.failure(roomId(), CODE_PHASE_LOCKED, "");
                }

                JoinRoomRequest resolvedRequest = request == null ? JoinRoomRequest.autoTeam() : request;
                if (resolvedRequest.spectator()) {
                    actionPort.joinSpectator(player);
                    actionPort.syncToClient();
                    return JoinRoomResult.success(roomId(), "OK");
                }

                String requestedTeam = normalizeTeam(resolvedRequest.preferredTeamName().orElse(null));
                if (requestedTeam != null && !readPort.hasTeam(requestedTeam)) {
                    return JoinRoomResult.failure(roomId(), CODE_TEAM_NOT_FOUND, "");
                }

                String targetTeam = requestedTeam;
                if (targetTeam == null) {
                    Optional<String> autoTeam = readPort.chooseAutoJoinTeam(CodTdmConfig.getConfig().getMaxTeamDiff());
                    if (autoTeam.isEmpty()) {
                        return JoinRoomResult.failure(roomId(), CODE_BALANCE_EXCEEDED, "");
                    }
                    targetTeam = autoTeam.get();
                } else {
                    if (readPort.isTeamFull(targetTeam)) {
                        return JoinRoomResult.failure(roomId(), CODE_TEAM_FULL, "");
                    }
                    if (!readPort.canJoinWithBalance(targetTeam, CodTdmConfig.getConfig().getMaxTeamDiff())) {
                        return JoinRoomResult.failure(roomId(), CODE_BALANCE_EXCEEDED, "");
                    }
                }

                actionPort.joinTeam(targetTeam, player);
                if (!readPort.containsJoinedPlayer(player.getUUID())) {
                    return JoinRoomResult.failure(roomId(), CODE_UNKNOWN, "");
                }

                actionPort.initializeReadyState(player);
                RoomFoodLockService.enforce(player);
                warnIfMissingEndTeleport(player, readPort);
                actionPort.syncToClient();
                return JoinRoomResult.success(roomId(), "OK");
            }

            @Override
            public LeaveRoomResult leave(ServerPlayer player) {
                if (player == null) {
                    return LeaveRoomResult.failure(roomId(), "PLAYER_MISSING", "");
                }
                actionPort.leaveRoom(player);
                return LeaveRoomResult.success(roomId(), "OK");
            }

            @Override
            public void syncToClient() {
                actionPort.syncToClient();
            }
        };
    }

    private static TeamRoomPort teamPort(ModeRoomReadPort readPort, ModeRoomActionPort actionPort) {
        return new TeamRoomPort() {
            @Override
            public List<com.cdp.codpattern.app.match.model.TeamDescriptor> teamDescriptors() {
                return readPort.teamDescriptors();
            }

            @Override
            public Map<String, Integer> teamPlayerCountsSnapshot() {
                return readPort.getTeamPlayerCountsSnapshot();
            }

            @Override
            public boolean hasTeam(String teamName) {
                return readPort.hasTeam(teamName);
            }

            @Override
            public boolean isTeamFull(String teamName) {
                return readPort.isTeamFull(teamName);
            }

            @Override
            public Optional<String> findTeamNameByPlayer(ServerPlayer player) {
                return readPort.findTeamNameByPlayer(player);
            }

            @Override
            public void switchTeam(ServerPlayer player, String teamName) {
                if (player == null || teamName == null || teamName.isBlank()) {
                    return;
                }
                boolean inJoinedTeam = readPort.containsJoinedPlayer(player.getUUID());
                boolean inSpectator = readPort.containsSpectator(player);
                if (!inJoinedTeam && !inSpectator) {
                    return;
                }
                if (inJoinedTeam) {
                    actionPort.switchTeam(player, teamName);
                    return;
                }
                if (!readPort.hasTeam(teamName)) {
                    player.sendSystemMessage(Component.translatable("message.codpattern.team.not_found", teamName));
                    return;
                }
                if (readPort.isTeamFull(teamName)) {
                    player.sendSystemMessage(Component.translatable("message.codpattern.team.full"));
                    return;
                }
                int maxTeamDiff = CodTdmConfig.getConfig().getMaxTeamDiff();
                if (!readPort.canJoinWithBalance(teamName, maxTeamDiff)) {
                    player.sendSystemMessage(Component.translatable("message.codpattern.team.join_balance_exceeded"));
                    return;
                }
                if (!readPort.isWaitingPhase()) {
                    player.sendSystemMessage(Component.translatable("message.codpattern.game.team_switch_locked"));
                    return;
                }

                actionPort.joinTeam(teamName, player);
                actionPort.initializeReadyState(player);
                RoomFoodLockService.enforce(player);
                actionPort.syncToClient();
            }
        };
    }

    private static ModeRosterPort rosterPort(CodTdmActionPort actionPort) {
        return new ModeRosterPort() {
            @Override
            public void requestRosterResync(ServerPlayer player) {
                actionPort.requestRosterResync(player);
            }

            @Override
            public void requestRosterPreview(ServerPlayer player) {
                actionPort.requestRosterPreview(player);
            }
        };
    }

    private static String normalizeTeam(String team) {
        if (team == null || team.isBlank()) {
            return null;
        }
        return team.trim();
    }

    private static void warnIfMissingEndTeleport(ServerPlayer player, ModeRoomReadPort readPort) {
        if (!readPort.hasMatchEndTeleportPoint()) {
            player.sendSystemMessage(
                    Component.translatable("message.codpattern.game.warning_no_end_teleport", readPort.mapName()));
        }
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
}
