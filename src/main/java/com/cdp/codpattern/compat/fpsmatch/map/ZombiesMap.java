package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.map.ZombiesMapObjects;
import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesInitialSpawnData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesZombieSpawnData;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.app.zombies.model.ZombiesTeamNames;
import com.cdp.codpattern.app.zombies.runtime.ZombiesLifecycleRuntime;
import com.cdp.codpattern.app.zombies.runtime.ZombiesPhaseStateMachine;
import com.cdp.codpattern.app.zombies.runtime.ZombiesRoomRuntimeState;
import com.cdp.codpattern.app.zombies.service.ZombiesActiveSpawnGroupService;
import com.cdp.codpattern.app.zombies.service.ZombiesBarrierService;
import com.cdp.codpattern.app.zombies.service.ZombiesCleanupParticipant;
import com.cdp.codpattern.app.zombies.service.ZombiesCleanupService;
import com.cdp.codpattern.app.zombies.service.ZombiesConnectionStateService;
import com.cdp.codpattern.app.zombies.service.ZombiesDeathService;
import com.cdp.codpattern.app.zombies.service.ZombiesEconomyService;
import com.cdp.codpattern.app.zombies.service.ZombiesErrorCode;
import com.cdp.codpattern.app.zombies.service.ZombiesMapOccupancyService;
import com.cdp.codpattern.app.zombies.service.ZombiesMobLifecycleService;
import com.cdp.codpattern.app.zombies.service.ZombiesMobSpawnService;
import com.cdp.codpattern.app.zombies.service.ZombiesObjectInteractionService;
import com.cdp.codpattern.app.zombies.service.ZombiesObjectStateStore;
import com.cdp.codpattern.app.zombies.service.ZombiesPlayerStateService;
import com.cdp.codpattern.app.zombies.service.ZombiesReadyService;
import com.cdp.codpattern.app.zombies.service.ZombiesServiceResult;
import com.cdp.codpattern.app.zombies.service.ZombiesSpawnAssignmentService;
import com.cdp.codpattern.app.zombies.service.ZombiesStartVoteService;
import com.cdp.codpattern.app.zombies.service.ZombiesStarterKitDistributor;
import com.cdp.codpattern.app.zombies.service.ZombiesStartupFlow;
import com.cdp.codpattern.app.zombies.service.ZombiesStartupValidationService;
import com.cdp.codpattern.app.zombies.service.ZombiesWaveDirector;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.zombies.ZombiesBackpackConfigRepository;
import com.cdp.codpattern.config.zombies.ZombiesRulesConfig;
import com.cdp.codpattern.config.zombies.ZombiesRulesRepository;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.config.zombies.ZombiesWeaponFilterRepository;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import com.cdp.codpattern.network.match.VoteDialogPacket;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.EndTeleportMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ZombiesMap extends BaseMap implements EndTeleportMap<ZombiesMap> {
    static final int SURVIVOR_LIMIT = 4;

    private final RoomId roomId;
    private final ZombiesRoomRuntimeState runtimeState;
    private final ZombiesLifecycleRuntime lifecycleRuntime;
    private final ZombiesPlayerStateService playerStateService;
    private final ZombiesConnectionStateService connectionStateService;
    private final ZombiesEconomyService economyService;
    private final ZombiesReadyService readyService;
    private final ZombiesStartVoteService startVoteService;
    private final ZombiesMobSpawnService mobSpawnService;
    private final ZombiesMobLifecycleService mobLifecycleService;
    private final ZombiesDeathService deathService;
    private final ZombiesCleanupService cleanupService;
    private final ZombiesActiveSpawnGroupService activeSpawnGroupService;
    private final ZombiesObjectStateStore objectStateStore;
    private final ZombiesObjectInteractionService objectInteractionService;
    private final ModeRoomHandle roomHandle;
    private Optional<SpawnPointData> matchEndTeleportPoint = Optional.empty();
    private ZombiesMapObjects objects = ZombiesMapObjects.EMPTY;
    private ZombiesWaveDirector waveDirector;
    private int rosterVersion = 1;

    public ZombiesMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        super(serverLevel, mapName, areaData);
        addTeam(ZombiesTeamNames.SURVIVORS, SURVIVOR_LIMIT);
        this.roomId = RoomId.of(BuiltInGameModes.ZOMBIES, mapName);
        this.runtimeState = new ZombiesRoomRuntimeState(roomId);
        this.playerStateService = new ZombiesPlayerStateService();
        this.connectionStateService = new ZombiesConnectionStateService(playerStateService, configuredOfflineGraceTicks());
        this.economyService = new ZombiesEconomyService(playerStateService);
        this.readyService = new ZombiesReadyService(new ZombiesReadyHooks());
        this.mobSpawnService = new ZombiesMobSpawnService();
        this.mobLifecycleService = new ZombiesMobLifecycleService(ModeEntityOwnershipRegistry.instance(), mobSpawnService);
        this.deathService = new ZombiesDeathService(
                playerStateService,
                connectionStateService.offlineGraceTicks(),
                new ZombiesDeathHooks());
        this.activeSpawnGroupService = new ZombiesActiveSpawnGroupService();
        this.objectStateStore = new ZombiesObjectStateStore();
        ZombiesBarrierService barrierService = new ZombiesBarrierService(
                roomId,
                this::barriers,
                economyService,
                objectStateStore,
                activeSpawnGroupService,
                this::hasSurvivor,
                runtimeState::phase);
        this.objectInteractionService = new ZombiesObjectInteractionService(
                roomId,
                this::barriers,
                barrierService,
                objectStateStore);
        this.cleanupService = new ZombiesCleanupService(
                ModeEntityOwnershipRegistry.instance(),
                ZombiesMapOccupancyService.instance(),
                new ZombiesCleanupHooks(),
                List.of());
        this.lifecycleRuntime = new ZombiesLifecycleRuntime(
                runtimeState,
                lifecycleConfig(),
                this::failurePriority,
                state -> state.waveState().isWaveComplete(),
                List.of(new ZombiesLifecycleRuntimeHooks()));
        this.startVoteService = new ZombiesStartVoteService(new ZombiesStartVoteHooks());
        this.roomHandle = ZombiesRoomHandleFactory.create(this);
    }

    @Override
    public String getGameType() {
        return BuiltInGameModes.ZOMBIES;
    }

    @Override
    public void tick() {
        if (runtimeState.phase() == ZombiesGamePhase.START_VOTE) {
            startVoteService.tickVoteSession();
        }
        lifecycleRuntime.tick();
    }

    @Override
    public void syncToClient() {
        markRoomListDirty();
    }

    @Override
    public void startGame() {
        startGame(survivorPlayerIds());
    }

    private void startGame(Collection<UUID> memberSnapshot) {
        MinecraftServer server = getServerLevel().getServer();
        List<UUID> members = normalizeStartMembers(memberSnapshot);
        if (server == null || members.isEmpty()) {
            lifecycleRuntime.cancelStartVote();
            markRoomListDirty();
            return;
        }

        loadStartupConfigs(server);
        ZombiesStartupFlow startupFlow = new ZombiesStartupFlow(
                new ZombiesStartupValidationService(ConfigPath.SERVER_ZOMBIES_WAVES.getPath(server)),
                new ZombiesStarterKitDistributor(),
                ZombiesMapOccupancyService.instance(),
                new ZombiesSpawnAssignmentService());
        ZombiesServiceResult<ZombiesStartupFlow.StartupResult> startupResult = startupFlow.start(
                ZombiesStartupFlow.StartupRequest.forMap(
                        roomId,
                        currentMapSnapshot(),
                        members,
                        initialSpawnPoints(),
                        this,
                        ZombiesBackpackConfigRepository.getConfig(),
                        ZombiesWeaponFilterRepository.getConfig(),
                        List.of(new ZombiesStartupMapParticipant())));
        if (!startupResult.success()) {
            lifecycleRuntime.cancelStartVote();
            markRoomListDirty();
        }
    }

    @Override
    public void victory() {
        resetGame();
    }

    @Override
    public boolean victoryGoal() {
        return false;
    }

    @Override
    public void resetGame() {
        runCleanup("reset");
    }

    @Override
    public void leave(ServerPlayer player) {
        leaveRoomPlayer(player);
    }

    @Override
    public void onPlayerLoggedOut(ServerPlayer player) {
        if (player == null || !hasSurvivor(player.getUUID())) {
            return;
        }
        connectionStateService.markOffline(player.getUUID(), runtimeState.roomTick());
        markRosterDirty();
    }

    @Override
    public void onPlayerLoggedIn(ServerPlayer player) {
        if (player == null || !hasSurvivor(player.getUUID())) {
            return;
        }
        connectionStateService.markOnline(player.getUUID());
        markRosterDirty();
    }

    public ModeRoomHandle roomHandle() {
        return roomHandle;
    }

    RoomId roomId() {
        return roomId;
    }

    ZombiesRoomRuntimeState runtimeState() {
        return runtimeState;
    }

    ZombiesLifecycleRuntime lifecycleRuntime() {
        return lifecycleRuntime;
    }

    ZombiesPlayerStateService playerStateService() {
        return playerStateService;
    }

    ZombiesConnectionStateService connectionStateService() {
        return connectionStateService;
    }

    ZombiesEconomyService economyService() {
        return economyService;
    }

    ZombiesReadyService readyService() {
        return readyService;
    }

    ZombiesStartVoteService startVoteService() {
        return startVoteService;
    }

    ZombiesDeathService deathService() {
        return deathService;
    }

    ZombiesCleanupService cleanupService() {
        return cleanupService;
    }

    ZombiesObjectInteractionService objectInteractionService() {
        return objectInteractionService;
    }

    ZombiesMobLifecycleService mobLifecycleService() {
        return mobLifecycleService;
    }

    int rosterVersion() {
        return Math.max(1, rosterVersion);
    }

    boolean isSurvivorTeamFull() {
        return getMapTeams().testTeamIsFull(ZombiesTeamNames.SURVIVORS);
    }

    boolean hasSurvivor(UUID playerId) {
        return playerId != null && checkGameHasPlayer(playerId);
    }

    List<ServerPlayer> survivorPlayers() {
        List<ServerPlayer> players = new ArrayList<>();
        getMapTeams().getJoinedPlayers().forEach(playerData -> playerData.getPlayer().ifPresent(players::add));
        return players;
    }

    Set<UUID> survivorPlayerIds() {
        return Set.copyOf(survivorPlayerIdList());
    }

    List<UUID> survivorPlayerIdList() {
        Set<UUID> playerIds = new LinkedHashSet<>();
        getMapTeams().getTeamByName(ZombiesTeamNames.SURVIVORS)
                .ifPresent(team -> playerIds.addAll(team.getPlayerList()));
        return List.copyOf(playerIds);
    }

    void onSurvivorJoined(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        playerStateService.markAlive(playerId);
        connectionStateService.markOnline(playerId);
        readyService.initializeReadyState(player);
        startVoteService.onPlayerJoined(playerId);
        markRosterDirty();
    }

    void leaveRoomPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        readyService.removePlayer(playerId);
        playerStateService.markLeft(playerId);
        connectionStateService.markLeft(playerId);
        startVoteService.onSnapshotMemberLeft(playerId);
        super.leave(player);
        if (survivorPlayers().isEmpty() && runtimeState.phase().isRoundRunning()) {
            resetGame();
        }
        markRosterDirty();
    }

    void markRoomListDirty() {
        CodTdmRoomManager.getInstance().markRoomListDirty();
    }

    void markRosterDirty() {
        rosterVersion = Math.max(1, rosterVersion + 1);
        markRoomListDirty();
    }

    @Override
    public void setMatchEndTeleportPoint(SpawnPointData data) {
        matchEndTeleportPoint = Optional.ofNullable(data);
    }

    @Override
    public ZombiesMap getMap() {
        return this;
    }

    public Optional<SpawnPointData> matchEndTeleportPoint() {
        return matchEndTeleportPoint;
    }

    public ZombiesMapObjects objects() {
        return objects;
    }

    public void applyObjects(ZombiesMapObjects objects) {
        this.objects = objects == null ? ZombiesMapObjects.EMPTY : objects;
        resetObjectRuntime();
        getMapTeams().getTeamByName(ZombiesTeamNames.SURVIVORS).ifPresent(team -> {
            team.resetSpawnPointData(SpawnPointKind.INITIAL);
            this.objects.initialSpawns().stream()
                    .map(ZombiesInitialSpawnData::toSpawnPointData)
                    .forEach(team::addSpawnPointData);
            team.clearPlayerSpawnPointAssignments();
            if (isStart) {
                team.assignNextSpawnPoints(SpawnPointKind.INITIAL);
            }
        });
    }

    public List<ZombiesInitialSpawnData> initialSpawns() {
        return objects.initialSpawns();
    }

    public List<ZombiesZombieSpawnData> zombieSpawns() {
        return objects.zombieSpawns();
    }

    public List<ZombiesBarrierData> barriers() {
        return objects.barriers();
    }

    private void loadStartupConfigs(MinecraftServer server) {
        ZombiesRulesRepository.loadOrCreate(server);
        ZombiesBackpackConfigRepository.loadOrCreate(server);
        ZombiesWeaponFilterRepository.loadOrCreate(server);
    }

    private ZombiesMapSnapshot currentMapSnapshot() {
        return ZombiesMapSnapshot.fromMapObjects(
                roomId,
                getMapName(),
                matchEndTeleportPoint.isPresent(),
                objects);
    }

    private List<SpawnPointData> initialSpawnPoints() {
        return initialSpawns().stream()
                .map(ZombiesInitialSpawnData::toSpawnPointData)
                .toList();
    }

    private List<UUID> normalizeStartMembers(Collection<UUID> memberSnapshot) {
        Set<UUID> requestedMembers = new LinkedHashSet<>();
        if (memberSnapshot != null) {
            memberSnapshot.stream()
                    .filter(java.util.Objects::nonNull)
                    .forEach(requestedMembers::add);
        }
        if (requestedMembers.isEmpty()) {
            requestedMembers.addAll(survivorPlayerIdList());
        }

        List<UUID> orderedMembers = new ArrayList<>();
        for (UUID playerId : survivorPlayerIdList()) {
            if (requestedMembers.contains(playerId)) {
                orderedMembers.add(playerId);
            }
        }
        for (UUID playerId : requestedMembers) {
            if (hasSurvivor(playerId) && !orderedMembers.contains(playerId)) {
                orderedMembers.add(playerId);
            }
        }
        return List.copyOf(orderedMembers);
    }

    private ZombiesPhaseStateMachine.FailureCheckResult failurePriority(ZombiesRoomRuntimeState state) {
        if (state == null || !state.phase().isRoundRunning()) {
            return ZombiesPhaseStateMachine.FailureCheckResult.none();
        }
        boolean hasAlivePlayer = playerStateService.hasAnyAlive(state.roomTick(), connectionStateService.offlineGraceTicks());
        return hasAlivePlayer
                ? ZombiesPhaseStateMachine.FailureCheckResult.none()
                : ZombiesPhaseStateMachine.FailureCheckResult.failed(ZombiesErrorCode.PLAYER_DEAD);
    }

    private ZombiesPhaseStateMachine.Config lifecycleConfig() {
        ZombiesRulesConfig.Room room = ZombiesRulesRepository.getConfig().getRoom();
        return ZombiesPhaseStateMachine.Config.fromSeconds(
                ZombiesPhaseStateMachine.DEFAULT_OPENING_COUNTDOWN_SECONDS,
                room.getIntermissionSeconds(),
                room.getFailDelaySeconds());
    }

    private long configuredOfflineGraceTicks() {
        ZombiesRulesConfig.Room room = ZombiesRulesRepository.getConfig().getRoom();
        return (long) room.getOfflineGraceSeconds() * ZombiesPhaseStateMachine.TICKS_PER_SECOND;
    }

    private int voteTimeoutTicks() {
        ZombiesRulesConfig.Room room = ZombiesRulesRepository.getConfig().getRoom();
        return room.getStartVoteTimeoutSeconds() * ZombiesPhaseStateMachine.TICKS_PER_SECOND;
    }

    private int voteRequiredPercent() {
        return ZombiesRulesRepository.getConfig().getRoom().getStartVoteRequiredPercent();
    }

    private ServerLevel levelForDimension(ResourceKey<Level> dimension) {
        MinecraftServer server = getServerLevel().getServer();
        return server == null || dimension == null ? null : server.getLevel(dimension);
    }

    private void runCleanup(String reason) {
        cleanupService.cleanup(roomId, reason, this::levelForDimension);
    }

    private void resetObjectRuntime() {
        activeSpawnGroupService.resetToInitial();
        objectStateStore.resetBarriers(objects.barriers());
    }

    private void resetRuntimeForWaiting() {
        isStart = false;
        waveDirector = null;
        resetObjectRuntime();
        lifecycleRuntime.resetToWaiting();
        playerStateService.clear();
        readyService.clear();
        for (ServerPlayer player : survivorPlayers()) {
            player.setGameMode(GameType.ADVENTURE);
            matchEndTeleportPoint.ifPresent(point -> teleportToPoint(player, point));
            player.getInventory().clearContent();
            ThrowableInventoryService.clearRuntime(player, true);
            player.inventoryMenu.broadcastChanges();
            player.inventoryMenu.slotsChanged(player.getInventory());
            ThrowableInventoryService.sync(player);
            playerStateService.markAlive(player.getUUID());
            connectionStateService.markOnline(player.getUUID());
            readyService.initializeReadyState(player);
        }
    }

    private void resetStartupRuntime(Collection<UUID> memberIds) {
        isStart = false;
        waveDirector = null;
        resetObjectRuntime();
        lifecycleRuntime.cancelStartVote();
        playerStateService.clear();
        for (ServerPlayer player : survivorPlayers()) {
            playerStateService.markAlive(player.getUUID());
            connectionStateService.markOnline(player.getUUID());
        }
    }

    private Map<UUID, StartupPlayerPosition> captureStartupPositions(Collection<UUID> memberIds) {
        Map<UUID, StartupPlayerPosition> positions = new LinkedHashMap<>();
        if (memberIds == null) {
            return positions;
        }
        for (UUID playerId : memberIds) {
            ServerPlayer player = getServerLevel().getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                positions.put(playerId, StartupPlayerPosition.capture(player));
            }
        }
        return positions;
    }

    private void restoreStartupPositions(Map<UUID, StartupPlayerPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, StartupPlayerPosition> entry : positions.entrySet()) {
            ServerPlayer player = getServerLevel().getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                entry.getValue().restore(player);
            }
        }
    }

    private void clearStartupInventories(Collection<UUID> memberIds) {
        if (memberIds == null) {
            return;
        }
        for (UUID playerId : memberIds) {
            ServerPlayer player = getServerLevel().getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            player.getInventory().clearContent();
            ThrowableInventoryService.clearRuntime(player, true);
            player.inventoryMenu.broadcastChanges();
            player.inventoryMenu.slotsChanged(player.getInventory());
            ThrowableInventoryService.sync(player);
        }
    }

    private final class ZombiesReadyHooks implements ZombiesReadyService.Hooks {
        @Override
        public boolean isWaitingPhase() {
            return runtimeState.phase() == ZombiesGamePhase.WAITING;
        }

        @Override
        public void markRoomListDirty() {
            markRosterDirty();
        }
    }

    private final class ZombiesStartVoteHooks implements ZombiesStartVoteService.Hooks {
        @Override
        public Collection<UUID> currentMembers() {
            return survivorPlayerIds();
        }

        @Override
        public boolean isWaitingPhase() {
            return runtimeState.phase() == ZombiesGamePhase.WAITING;
        }

        @Override
        public boolean isPlayerReady(UUID playerId) {
            return readyService.isPlayerReady(playerId);
        }

        @Override
        public int minPlayersToStart() {
            return 1;
        }

        @Override
        public int votePercentageToStart() {
            return voteRequiredPercent();
        }

        @Override
        public int voteTimeoutTicks() {
            return ZombiesMap.this.voteTimeoutTicks();
        }

        @Override
        public void onVoteStarted(ZombiesStartVoteService.VoteSnapshot snapshot) {
            lifecycleRuntime.beginStartVote();
            sendVoteDialog(snapshot);
        }

        @Override
        public void onVotePassed(ZombiesStartVoteService.VoteSnapshot snapshot) {
            startGame(snapshot == null ? survivorPlayerIds() : snapshot.members());
        }

        @Override
        public void onVoteFailed(ZombiesStartVoteService.VoteSnapshot snapshot, ZombiesStartVoteService.FailureReason reason) {
            lifecycleRuntime.cancelStartVote();
        }

        @Override
        public void markRoomListDirty() {
            ZombiesMap.this.markRoomListDirty();
        }

        private void sendVoteDialog(ZombiesStartVoteService.VoteSnapshot snapshot) {
            if (snapshot == null) {
                return;
            }
            String initiatorName = Optional.ofNullable(getServerLevel().getPlayerByUUID(snapshot.initiator()))
                    .map(player -> player.getName().getString())
                    .orElse("");
            VoteDialogPacket packet = new VoteDialogPacket(
                    getMapName(),
                    snapshot.voteId(),
                    "START",
                    initiatorName,
                    snapshot.requiredVotes(),
                    snapshot.totalMembers());
            for (ServerPlayer player : survivorPlayers()) {
                if (snapshot.members().contains(player.getUUID())) {
                    ModNetworkChannel.sendToPlayer(packet, player);
                }
            }
        }
    }

    private final class ZombiesStartupMapParticipant implements ZombiesStartupFlow.ZombiesStartupParticipant {
        @Override
        public String name() {
            return "zombies_map_startup";
        }

        @Override
        public ZombiesServiceResult<Optional<ZombiesStartupFlow.ZombiesStartupRollbackAction>> onStartupStage(
                ZombiesStartupFlow.ParticipantStage stage,
                ZombiesStartupFlow.ZombiesStartupContext context
        ) {
            if (stage == ZombiesStartupFlow.ParticipantStage.AFTER_OCCUPANCY_ACQUIRED) {
                isStart = true;
                playerStateService.registerPlayers(context.memberIds());
                context.memberIds().forEach(playerId -> {
                    playerStateService.markAlive(playerId);
                    connectionStateService.markOnline(playerId);
                });
                return ZombiesServiceResult.success(Optional.of(new StartupRollbackAction(
                        "reset_startup_runtime",
                        ignored -> {
                            resetStartupRuntime(context.memberIds());
                            return ZombiesServiceResult.ok();
                        })));
            }
            if (stage == ZombiesStartupFlow.ParticipantStage.BEFORE_TELEPORT) {
                Map<UUID, StartupPlayerPosition> positions = captureStartupPositions(context.memberIds());
                return ZombiesServiceResult.success(Optional.of(new StartupRollbackAction(
                        "restore_startup_positions",
                        ignored -> {
                            restoreStartupPositions(positions);
                            return ZombiesServiceResult.ok();
                        })));
            }
            if (stage == ZombiesStartupFlow.ParticipantStage.AFTER_STARTER_KIT_APPLIED) {
                return ZombiesServiceResult.success(Optional.of(new StartupRollbackAction(
                        "clear_startup_inventories",
                        ignored -> {
                            clearStartupInventories(context.memberIds());
                            return ZombiesServiceResult.ok();
                        })));
            }
            if (stage == ZombiesStartupFlow.ParticipantStage.COMPLETE_STARTUP) {
                if (context.preflightSnapshot().isEmpty()) {
                    return ZombiesServiceResult.failure(ZombiesErrorCode.STARTUP_PREFLIGHT_FAILED);
                }
                ZombiesStartupFlow.ZombiesStartupContext startupContext = context;
                waveDirector = new ZombiesWaveDirector(
                        startupContext.preflightSnapshot().get().waveLoadResult(),
                        mobSpawnService);
                lifecycleRuntime.beginOpeningCountdown(startupContext.preflightSnapshot().get().maxWave());
                markRoomListDirty();
            }
            return ZombiesServiceResult.success(Optional.empty());
        }
    }

    private record StartupRollbackAction(
            String name,
            RollbackHandler handler
    ) implements ZombiesStartupFlow.ZombiesStartupRollbackAction {
        @Override
        public ZombiesServiceResult<Void> rollback(ZombiesStartupFlow.ZombiesStartupRollbackContext context) {
            return handler.rollback(context);
        }
    }

    @FunctionalInterface
    private interface RollbackHandler {
        ZombiesServiceResult<Void> rollback(ZombiesStartupFlow.ZombiesStartupRollbackContext context);
    }

    private record StartupPlayerPosition(
            ServerLevel level,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            GameType gameType
    ) {
        private static StartupPlayerPosition capture(ServerPlayer player) {
            return new StartupPlayerPosition(
                    player.serverLevel(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot(),
                    player.gameMode.getGameModeForPlayer());
        }

        private void restore(ServerPlayer player) {
            player.teleportTo(level, x, y, z, yaw, pitch);
            player.setGameMode(gameType == null ? GameType.ADVENTURE : gameType);
        }
    }

    private final class ZombiesDeathHooks implements ZombiesDeathService.Hooks {
        @Override
        public int activeMemberCount() {
            return survivorPlayerIds().size();
        }

        @Override
        public void onRoundFailed(String reason) {
            lifecycleRuntime.fail(ZombiesErrorCode.PLAYER_DEAD);
            markRoomListDirty();
        }
    }

    private final class ZombiesCleanupHooks implements ZombiesCleanupService.Hooks {
        @Override
        public void clearPlayerRuntime(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            resetRuntimeForWaiting();
        }

        @Override
        public void clearReadyState(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            readyService.clear();
            for (ServerPlayer player : survivorPlayers()) {
                readyService.initializeReadyState(player);
            }
        }

        @Override
        public void clearStartVote(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            startVoteService.clearActiveVoteSession();
        }

        @Override
        public void clearLifecycleRuntime(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            lifecycleRuntime.resetToWaiting();
        }

        @Override
        public void onEntityCleanup(Entity entity) {
            mobLifecycleService.onCleanup(roomId, entity, runtimeState.waveState());
        }

        @Override
        public void afterCleanup(ZombiesCleanupParticipant.ZombiesCleanupContext context) {
            markRosterDirty();
        }
    }

    private final class ZombiesLifecycleRuntimeHooks implements com.cdp.codpattern.app.zombies.runtime.ZombiesLifecycleHooks {
        @Override
        public ZombiesServiceResult<Void> onEnter(com.cdp.codpattern.app.zombies.runtime.ZombiesPhaseTransitionContext context) {
            if (runtimeState.phase() == ZombiesGamePhase.WAVE_ACTIVE && waveDirector != null) {
                waveDirector.enterTargetWave(runtimeState.waveState());
            }
            return ZombiesServiceResult.ok();
        }

        @Override
        public ZombiesServiceResult<Void> onTick(com.cdp.codpattern.app.zombies.runtime.ZombiesPhaseTransitionContext context) {
            for (ServerPlayer player : survivorPlayers()) {
                playerStateService.updateLastAliveTargetPos(player.getUUID(), player.blockPosition());
            }
            List<UUID> timedOutPlayers = connectionStateService.applyOfflineGraceTimeouts(runtimeState.roomTick());
            if (!timedOutPlayers.isEmpty()) {
                markRosterDirty();
            }
            if (runtimeState.phase() == ZombiesGamePhase.WAVE_ACTIVE && waveDirector != null) {
                waveDirector.tick(
                        roomId,
                        getServerLevel(),
                        objects,
                        runtimeState.waveState(),
                        runtimeState.roomTick(),
                        activeSpawnGroupService.snapshot());
            }
            return ZombiesServiceResult.ok();
        }

        @Override
        public ZombiesServiceResult<Void> onCleanup(com.cdp.codpattern.app.zombies.runtime.ZombiesPhaseTransitionContext context) {
            runCleanup(context.previousPhase().isBlank() ? "phase_cleanup" : context.previousPhase());
            return ZombiesServiceResult.ok();
        }
    }
}
