package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.model.JoinRoomRequest;
import com.cdp.codpattern.app.match.model.JoinRoomResult;
import com.cdp.codpattern.app.match.model.LeaveRoomResult;
import com.cdp.codpattern.app.match.model.MetricDisplay;
import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.ModePrompt;
import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.model.RoomSummaryMetric;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.match.port.ModeRoomLifecyclePort;
import com.cdp.codpattern.app.match.port.ModeRoomSummaryPort;
import com.cdp.codpattern.app.match.port.ModeRosterPort;
import com.cdp.codpattern.app.match.port.ModeRuntimeStatePort;
import com.cdp.codpattern.app.match.port.ReadyStatePort;
import com.cdp.codpattern.app.zombies.model.ZombiesGamePhase;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.model.ZombiesTeamNames;
import com.cdp.codpattern.app.zombies.runtime.ZombiesWaveRuntimeState;
import com.cdp.codpattern.app.zombies.sync.ZombiesRuntimeStateKeys;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.match.RoomPreviewRosterPacket;
import com.cdp.codpattern.network.match.TeamPlayerListPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class ZombiesRoomHandleFactory {
    private static final String CODE_OK = "OK";
    private static final String CODE_ALREADY_JOINED = "ALREADY_JOINED";
    private static final String CODE_PLAYER_MISSING = "PLAYER_MISSING";
    private static final String CODE_PHASE_LOCKED = "PHASE_LOCKED";
    private static final String CODE_SPECTATOR_UNSUPPORTED = "SPECTATOR_UNSUPPORTED";
    private static final String CODE_TEAM_FULL = "TEAM_FULL";
    private static final String CODE_UNKNOWN = "UNKNOWN";

    private ZombiesRoomHandleFactory() {
    }

    static ModeRoomHandle create(ZombiesMap map) {
        ZombiesPorts ports = new ZombiesPorts(map);
        ZombiesEntityCombatEventAdapter entityCombatPort = new ZombiesEntityCombatEventAdapter(
                map.roomId(),
                null,
                map.economyService(),
                map.playerStateService(),
                ModeEntityOwnershipRegistry.instance(),
                ZombiesEntityCombatEventAdapter.RewardResolver.defaults(),
                (entity, reason) -> map.mobLifecycleService().onKilled(
                        map.roomId(),
                        entity,
                        map.runtimeState().waveState()));
        ZombiesPlayerCombatEventAdapter playerCombatPort = new ZombiesPlayerCombatEventAdapter(
                map.roomId(),
                null,
                map.deathService(),
                new ZombiesPlayerCombatEventAdapter.RoundState() {
                    @Override
                    public boolean isStarted() {
                        return map.runtimeState().phase().isRoundRunning();
                    }

                    @Override
                    public long currentTick() {
                        return map.runtimeState().roomTick();
                    }
                },
                null,
                map::onPlayerDamaged);
        ZombiesEntityLifecyclePortAdapter entityLifecyclePort = new ZombiesEntityLifecyclePortAdapter(
                map.roomId(),
                null,
                ModeEntityOwnershipRegistry.instance(),
                map.cleanupService(),
                dimension -> map.getServerLevel().getServer().getLevel(dimension),
                entity -> map.mobLifecycleService().onRemoved(
                        map.roomId(),
                        entity,
                        map.runtimeState().waveState()));
        ZombiesRespawnPolicy respawnPolicy = new ZombiesRespawnPolicy(map.roomId(), null, (player, context) -> {
        });
        return new ModeRoomHandle(
                map.roomId(),
                ports,
                ports,
                Optional.empty(),
                Optional.empty(),
                Optional.of(ports),
                Optional.of(map.startVoteService()),
                Optional.of(playerCombatPort),
                Optional.of(ports),
                Optional.empty(),
                Optional.empty(),
                Optional.of(entityCombatPort),
                Optional.of(entityLifecyclePort),
                Optional.empty(),
                Optional.of(ports),
                Optional.of(map.objectInteractionService()),
                Optional.empty(),
                Optional.of(respawnPolicy));
    }

    private static final class ZombiesPorts implements ModeRoomSummaryPort, ModeRoomLifecyclePort, ModeRosterPort, ModeRuntimeStatePort, ReadyStatePort {
        private final ZombiesMap map;

        private ZombiesPorts(ZombiesMap map) {
            this.map = map;
        }

        @Override
        public RoomId roomId() {
            return map.roomId();
        }

        @Override
        public String gameType() {
            return BuiltInGameModes.ZOMBIES;
        }

        @Override
        public String mapName() {
            return map.getMapName();
        }

        @Override
        public String modeDisplayNameKey() {
            return GameModeRegistry.getOrDefault(gameType()).displayNameKey();
        }

        @Override
        public String lifecycleStateKey() {
            return map.runtimeState().phaseKey();
        }

        @Override
        public boolean isJoinable() {
            return map.runtimeState().phase() == ZombiesGamePhase.WAITING && !map.isSurvivorTeamFull();
        }

        @Override
        public boolean isRunning() {
            return map.runtimeState().phase().isRoundRunning();
        }

        @Override
        public int playerCount() {
            return map.survivorPlayerIds().size();
        }

        @Override
        public int maxPlayers() {
            return ZombiesMap.SURVIVOR_LIMIT;
        }

        @Override
        public int remainingTimeTicks() {
            return map.lifecycleRuntime().remainingPhaseTicks();
        }

        @Override
        public List<RoomSummaryMetric> metrics() {
            return buildMetrics(map);
        }

        @Override
        public com.phasetranscrystal.fpsmatch.core.data.AreaData mapArea() {
            return map.getMapArea();
        }

        @Override
        public String dimensionId() {
            return map.getServerLevel().dimension().location().toString();
        }

        @Override
        public JoinRoomResult join(ServerPlayer player, JoinRoomRequest request) {
            if (player == null) {
                return JoinRoomResult.failure(roomId(), CODE_PLAYER_MISSING, "");
            }
            if (map.checkGameHasPlayer(player.getUUID()) || map.checkSpecHasPlayer(player)) {
                return JoinRoomResult.success(roomId(), CODE_ALREADY_JOINED);
            }
            if (map.runtimeState().phase() != ZombiesGamePhase.WAITING) {
                return JoinRoomResult.failure(roomId(), CODE_PHASE_LOCKED, "");
            }
            JoinRoomRequest resolvedRequest = request == null ? JoinRoomRequest.autoTeam() : request;
            if (resolvedRequest.spectator()) {
                return JoinRoomResult.failure(roomId(), CODE_SPECTATOR_UNSUPPORTED, "");
            }
            if (map.isSurvivorTeamFull()) {
                return JoinRoomResult.failure(roomId(), CODE_TEAM_FULL, "");
            }

            map.join(ZombiesTeamNames.SURVIVORS, player);
            if (!map.checkGameHasPlayer(player.getUUID())) {
                return JoinRoomResult.failure(roomId(), CODE_UNKNOWN, "");
            }

            map.onSurvivorJoined(player);
            sendRosterSnapshotToSurvivors();
            return JoinRoomResult.success(roomId(), CODE_OK);
        }

        @Override
        public LeaveRoomResult leave(ServerPlayer player) {
            if (player == null) {
                return LeaveRoomResult.failure(roomId(), CODE_PLAYER_MISSING, "");
            }
            map.leaveRoomPlayer(player);
            sendRosterSnapshotToSurvivors();
            return LeaveRoomResult.success(roomId(), CODE_OK);
        }

        @Override
        public void initializeReadyState(ServerPlayer player) {
            map.readyService().initializeReadyState(player);
        }

        @Override
        public boolean setPlayerReady(ServerPlayer player, boolean ready) {
            boolean changed = map.readyService().setPlayerReady(player, ready);
            if (changed) {
                sendRosterSnapshotToSurvivors();
            }
            return changed;
        }

        @Override
        public void syncToClient() {
            map.syncToClient();
        }

        @Override
        public void requestRosterResync(ServerPlayer player) {
            if (player == null || !map.hasSurvivor(player.getUUID())) {
                return;
            }
            sendRosterSnapshot(player, false);
        }

        @Override
        public void requestRosterPreview(ServerPlayer player) {
            if (player == null) {
                return;
            }
            sendRosterSnapshot(player, true);
        }

        @Override
        public ModeRuntimeStateSnapshot runtimeStateSnapshot(ServerPlayer viewer) {
            Map<String, ModePlayerValue> playerValues = new LinkedHashMap<>();
            if (viewer != null) {
                playerValues.putAll(map.playerStateService().playerValues(viewer.getUUID()));
            }
            playerValues.put(
                    ZombiesRuntimeStateKeys.PLAYER_POWER_ENABLED,
                    ModePlayerValue.ofBoolean(map.powerService().isPowerOn()));
            playerValues.putAll(map.playerStateService().survivorValues());
            appendSurvivorHealthValues(map, playerValues);
            playerValues.put(
                    ZombiesRuntimeStateKeys.ACTIVE_ZOMBIE_ENTITY_IDS,
                    ModePlayerValue.ofString(activeZombieEntityIds(map.runtimeState().waveState())));

            return new ModeRuntimeStateSnapshot(
                    roomId().encode(),
                    map.runtimeState().phaseKey(),
                    remainingTimeTicks(),
                    buildMetrics(map),
                    playerValues,
                    List.<ModePrompt>of(),
                    map.runtimeState().revision());
        }

        private void sendRosterSnapshot(ServerPlayer player, boolean preview) {
            Map<String, List<PlayerInfo>> teamPlayers = buildTeamPlayers(map);
            int rosterVersion = map.rosterVersion();
            if (preview) {
                ModNetworkChannel.sendToPlayer(
                        new RoomPreviewRosterPacket(roomId().encode(), rosterVersion, teamPlayers),
                        player);
                return;
            }
            ModNetworkChannel.sendToPlayer(
                    new TeamPlayerListPacket(roomId().encode(), rosterVersion, teamPlayers),
                    player);
        }

        private void sendRosterSnapshotToSurvivors() {
            Map<String, List<PlayerInfo>> teamPlayers = buildTeamPlayers(map);
            int rosterVersion = map.rosterVersion();
            String roomKey = roomId().encode();
            for (ServerPlayer survivor : map.survivorPlayers()) {
                if (survivor != null) {
                    ModNetworkChannel.sendToPlayer(
                            new TeamPlayerListPacket(roomKey, rosterVersion, teamPlayers),
                            survivor);
                }
            }
        }

        private void appendSurvivorHealthValues(ZombiesMap map, Map<String, ModePlayerValue> playerValues) {
            for (ServerPlayer player : map.survivorPlayers()) {
                if (player == null) {
                    continue;
                }
                String playerId = player.getUUID().toString();
                playerValues.put(
                        ZombiesRuntimeStateKeys.survivorHealth(playerId),
                        ModePlayerValue.ofDouble(Math.max(0.0D, player.getHealth())));
                playerValues.put(
                        ZombiesRuntimeStateKeys.survivorMaxHealth(playerId),
                        ModePlayerValue.ofDouble(Math.max(1.0D, player.getMaxHealth())));
            }
        }
    }

    private static List<RoomSummaryMetric> buildMetrics(ZombiesMap map) {
        ZombiesWaveRuntimeState waveState = map.runtimeState().waveState();
        int displayWave = map.runtimeState().phase() == ZombiesGamePhase.INTERMISSION
                ? waveState.targetWave()
                : waveState.currentWave();
        int zombiesLeft = Math.max(0, waveState.remainingBudget() + waveState.activeZombies());
        int alivePlayers = map.playerStateService().aliveCount(
                map.runtimeState().roomTick(),
                map.connectionStateService().offlineGraceTicks());

        return List.of(
                metric(ZombiesRuntimeStateKeys.METRIC_WAVE, displayWave, MetricDisplay.NUMBER),
                metric(ZombiesRuntimeStateKeys.METRIC_ZOMBIES_LEFT, zombiesLeft, MetricDisplay.NUMBER),
                metric(ZombiesRuntimeStateKeys.METRIC_ACTIVE_ZOMBIES, waveState.activeZombies(), MetricDisplay.NUMBER),
                metric(ZombiesRuntimeStateKeys.METRIC_ALIVE_PLAYERS, alivePlayers, MetricDisplay.PLAYER_COUNT),
                metric(ZombiesRuntimeStateKeys.METRIC_MAX_PLAYERS, ZombiesMap.SURVIVOR_LIMIT, MetricDisplay.PLAYER_COUNT)
        );
    }

    private static RoomSummaryMetric metric(String key, int value, MetricDisplay display) {
        return new RoomSummaryMetric(key, "screen.codpattern.zombies_room.metric." + key, value, display);
    }

    private static String activeZombieEntityIds(ZombiesWaveRuntimeState waveState) {
        if (waveState == null) {
            return "";
        }
        return waveState.activeZombieEntityIdsSnapshot().stream()
                .map(UUID::toString)
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static Map<String, List<PlayerInfo>> buildTeamPlayers(ZombiesMap map) {
        List<PlayerInfo> survivors = new ArrayList<>();
        for (ServerPlayer player : map.survivorPlayers()) {
            UUID playerId = player.getUUID();
            map.playerStateService().recordPlayerName(playerId, player.getName().getString());
            ZombiesPlayerRuntimeState state = map.playerStateService()
                    .get(playerId)
                    .orElseGet(() -> map.playerStateService().getOrCreate(playerId));
            survivors.add(new PlayerInfo(
                    playerId,
                    player.getName().getString(),
                    map.readyService().isPlayerReady(playerId),
                    state.kills(),
                    state.deaths(),
                    0,
                    state.isAlive() && player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                    false,
                    Math.max(0, player.latency)
            ));
        }
        survivors.sort((left, right) -> {
            int leftScore = map.playerStateService().get(left.uuid())
                    .map(ZombiesPlayerRuntimeState::displayTotalEarnedPoints)
                    .orElse(0);
            int rightScore = map.playerStateService().get(right.uuid())
                    .map(ZombiesPlayerRuntimeState::displayTotalEarnedPoints)
                    .orElse(0);
            int byScore = Integer.compare(rightScore, leftScore);
            if (byScore != 0) {
                return byScore;
            }
            int byKills = Integer.compare(right.kills(), left.kills());
            if (byKills != 0) {
                return byKills;
            }
            int leftBarriers = map.playerStateService().get(left.uuid())
                    .map(ZombiesPlayerRuntimeState::barriersOpened)
                    .orElse(0);
            int rightBarriers = map.playerStateService().get(right.uuid())
                    .map(ZombiesPlayerRuntimeState::barriersOpened)
                    .orElse(0);
            int byBarriers = Integer.compare(rightBarriers, leftBarriers);
            if (byBarriers != 0) {
                return byBarriers;
            }
            int byDeaths = Integer.compare(left.deaths(), right.deaths());
            if (byDeaths != 0) {
                return byDeaths;
            }
            return left.name().compareToIgnoreCase(right.name());
        });
        Map<String, List<PlayerInfo>> result = new LinkedHashMap<>();
        result.put(ZombiesTeamNames.SURVIVORS, survivors);
        return result;
    }
}
