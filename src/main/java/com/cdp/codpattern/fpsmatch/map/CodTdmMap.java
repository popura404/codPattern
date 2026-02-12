package com.cdp.codpattern.fpsmatch.map;

import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.app.tdm.service.DeathCamService;
import com.cdp.codpattern.app.tdm.service.KitDistributionService;
import com.cdp.codpattern.app.tdm.service.PhaseStateMachine;
import com.cdp.codpattern.app.tdm.service.PlayerDeathService;
import com.cdp.codpattern.app.tdm.service.RespawnService;
import com.cdp.codpattern.app.tdm.service.ScoreService;
import com.cdp.codpattern.app.tdm.service.TeamBalanceService;
import com.cdp.codpattern.app.tdm.service.TeamPlayerSnapshotService;
import com.cdp.codpattern.app.tdm.service.VoteService;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.tdm.CountdownPacket;
import com.cdp.codpattern.network.tdm.DeathCamPacket;
import com.cdp.codpattern.network.tdm.GamePhasePacket;
import com.cdp.codpattern.network.tdm.PhysicsMobRetainPacket;
import com.cdp.codpattern.network.tdm.ScoreUpdatePacket;
import com.cdp.codpattern.network.tdm.TeamPlayerListPacket;
import com.cdp.codpattern.network.tdm.VoteDialogPacket;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.PlayerData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.map.EndTeleportMap;
import com.phasetranscrystal.fpsmatch.core.map.GiveStartKitsMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import java.util.*;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * COD Team Deathmatch 地图核心类
 * 实现完整的团队死斗游戏逻辑
 */
public class CodTdmMap extends BaseMap implements GiveStartKitsMap<CodTdmMap>, EndTeleportMap<CodTdmMap> {

    public static final String GAME_TYPE = "cdptdm";

    /**
     * 游戏阶段枚举
     */
    public enum GamePhase {
        WAITING, // 等待玩家
        COUNTDOWN, // 倒计时（最后几秒黑屏）
        WARMUP, // 热身（可开火不能击杀，保留击退）
        PLAYING, // 正式比赛
        ENDED // 游戏结束
    }

    // ========== 游戏状态 ==========
    private GamePhase phase = GamePhase.WAITING;
    private int phaseTimer = 0;
    private final Map<String, Integer> teamScores = new HashMap<>();
    private int gameTimeTicks = 0;

    // ========== 复活系统 ==========
    private final Map<UUID, Integer> respawnTimers = new HashMap<>();
    private final Set<UUID> invinciblePlayers = new HashSet<>();
    private final Map<UUID, Integer> invincibilityTimers = new HashMap<>();

    // ========== 死亡视角系统 ==========
    private final Map<UUID, DeathCamData> deathCamPlayers = new HashMap<>();

    // ========== 玩家统计 ==========
    private final Map<UUID, Integer> playerKills = new HashMap<>();
    private final Map<UUID, Integer> playerDeaths = new HashMap<>();
    private final Map<UUID, Boolean> readyStates = new HashMap<>();

    // ========== 阶段与计分服务 ==========
    private final PhaseStateMachine.Hooks phaseStateHooks;
    private final ScoreService.Hooks scoreServiceHooks;
    private final PlayerDeathService.Hooks playerDeathHooks;

    // ========== 投票系统 ==========
    private final VoteService voteService;
    private final CodTdmVoteCoordinator voteCoordinator;

    // ========== 装备系统 ==========
    private Map<String, ArrayList<ItemStack>> startKits = new HashMap<>();

    // ========== 结束传送点 ==========
    private SpawnPointData matchEndTeleportPoint = null;

    /**
     * 队伍名称常量
     */
    public static final String TEAM_KORTAC = "kortac";
    public static final String TEAM_SPECGRU = "specgru";
    public static final int DEFAULT_TEAM_LIMIT = 6;

    /**
     * 构造函数
     */
    public CodTdmMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        super(serverLevel, mapName, areaData);

        this.phaseStateHooks = CodTdmMapHooks.createPhaseHooks(this, deathCamPlayers, respawnTimers);
        this.scoreServiceHooks = CodTdmMapHooks.createScoreHooks(this);
        this.playerDeathHooks = CodTdmMapHooks.createPlayerDeathHooks(this, deathCamPlayers);
        this.voteService = new VoteService(CodTdmMapHooks.createVoteHooks(this, readyStates));
        this.voteCoordinator = new CodTdmVoteCoordinator(
                readyStates,
                voteService,
                () -> phase,
                this::checkGameHasPlayer,
                this::syncToClient
        );

        // 注册默认队伍
        addTeam(TEAM_KORTAC, DEFAULT_TEAM_LIMIT);
        addTeam(TEAM_SPECGRU, DEFAULT_TEAM_LIMIT);

        // 初始化队伍分数
        teamScores.put(TEAM_KORTAC, 0);
        teamScores.put(TEAM_SPECGRU, 0);
    }

    // ========== 基础方法覆盖 ==========

    @Override
    public void tick() {
        PhaseStateMachine.TickResult tickResult = PhaseStateMachine.tick(
                phase,
                phaseTimer,
                gameTimeTicks,
                CodTdmConfig.getConfig(),
                teamScores,
                phaseStateHooks
        );

        if (!tickResult.resetTriggered()) {
            phaseTimer = tickResult.phaseTimer();
            gameTimeTicks = tickResult.gameTimeTicks();
            tickResult.nextPhase().ifPresent(this::transitionToPhase);
        }

        // 更新投票超时
        voteService.tickVoteSession();

        // 更新死亡视角
        tickDeathCam();

        // 更新复活计时器
        tickRespawn();

        // 更新无敌状态
        tickInvincibility();
    }

    @Override
    public void syncToClient() {
        CodTdmConfig config = CodTdmConfig.getConfig();
        int remainingTime = PhaseStateMachine.getRemainingTimeTicks(phase, phaseTimer, gameTimeTicks, config);

        GamePhasePacket phasePacket = new GamePhasePacket(phase.name(), remainingTime);

        // 同步分数（按队伍名）
        ScoreUpdatePacket scorePacket = new ScoreUpdatePacket(teamScores, gameTimeTicks);

        // 同步玩家列表
        TeamPlayerListPacket playerListPacket = new TeamPlayerListPacket(mapName, getTeamPlayers());

        // 发送给地图内所有玩家
        for (PlayerData playerData : getMapTeams().getJoinedPlayers()) {
            playerData.getPlayer().ifPresent(player -> {
                ModNetworkChannel.sendToPlayer(phasePacket, player);
                ModNetworkChannel.sendToPlayer(scorePacket, player);
                ModNetworkChannel.sendToPlayer(playerListPacket, player);
            });
        }

        CodTdmRoomManager.getInstance().markRoomListDirty();
    }

    @Override
    public String getGameType() {
        return GAME_TYPE;
    }

    @Override
    public void startGame() {
        this.isStart = true;
        transitionToPhase(GamePhase.COUNTDOWN);
    }

    @Override
    public void victory() {
        transitionToPhase(GamePhase.ENDED);
    }

    @Override
    public boolean victoryGoal() {
        return ScoreService.hasReachedVictoryGoal(phase, gameTimeTicks, teamScores, CodTdmConfig.getConfig());
    }

    @Override
    public void resetGame() {
        this.isStart = false;

        // 重置所有状态
        phase = GamePhase.WAITING;
        phaseTimer = 0;
        gameTimeTicks = 0;
        teamScores.clear();
        respawnTimers.clear();
        invinciblePlayers.clear();
        deathCamPlayers.clear();
        playerKills.clear();
        playerDeaths.clear();
        voteCoordinator.clearAll();

        // 重置队伍分数
        for (BaseTeam team : getMapTeams().getTeams()) {
            teamScores.put(team.name, 0);
        }
    }

    /**
     * 玩家离开房间（不是换队）。
     * 会尝试传送到比赛结束点，然后移除房间内状态。
     */
    @Override
    public void leave(ServerPlayer player) {
        leaveRoom(player);
    }

    public void leaveRoom(ServerPlayer player) {
        UUID playerId = player.getUUID();
        clearTransientPlayerState(playerId);
        playerKills.remove(playerId);
        playerDeaths.remove(playerId);
        voteCoordinator.removePlayer(playerId);

        if (!teleportPlayerToMatchEndPoint(player)) {
            player.sendSystemMessage(Component.translatable("message.codpattern.game.warning_no_end_teleport", mapName));
        } else {
            getServerLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.0f);
        }

        ModNetworkChannel.sendToPlayer(new GamePhasePacket(GamePhase.WAITING.name(), 0), player);
        ModNetworkChannel.sendToPlayer(DeathCamPacket.clear(), player);
        ModNetworkChannel.sendToPlayer(new ScoreUpdatePacket(0, 0, 0), player);
        clearPlayerInventory(player);
        super.leave(player);
        syncToClient();
    }

    /**
     * 切换队伍（不会触发离房传送）。
     */
    public void switchTeam(ServerPlayer player, String teamName) {
        if (phase != GamePhase.WAITING) {
            player.sendSystemMessage(Component.translatable("message.codpattern.game.team_switch_locked"));
            return;
        }
        if (!getMapTeams().checkTeam(teamName)) {
            player.sendSystemMessage(Component.literal("§c队伍不存在: " + teamName));
            return;
        }
        Optional<BaseTeam> currentTeamOpt = getMapTeams().getTeamByPlayer(player);
        if (currentTeamOpt.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c当前未加入可切换的队伍"));
            return;
        }
        BaseTeam currentTeam = currentTeamOpt.get();
        if (currentTeam.name.equals(teamName)) {
            return;
        }
        if (getMapTeams().testTeamIsFull(teamName)) {
            player.sendSystemMessage(Component.literal("§c目标队伍已满"));
            return;
        }
        int maxTeamDiff = CodTdmConfig.getConfig().getMaxTeamDiff();
        if (!canSwitchWithBalance(currentTeam.name, teamName, maxTeamDiff)) {
            player.sendSystemMessage(Component.literal("§c切换后将超出队伍人数差限制"));
            return;
        }
        clearTransientPlayerState(player.getUUID());
        getMapTeams().leaveTeam(player);
        join(teamName, player);
        syncToClient();
    }

    // ========== 阶段转换 ==========

    /**
     * 切换到新阶段
     */
    private void transitionToPhase(GamePhase newPhase) {
        if (newPhase != phase) {
            voteService.clearActiveVoteSession();
        }

        PhaseStateMachine.EnterPhaseResult enterPhaseResult = PhaseStateMachine.enterPhase(
                newPhase,
                gameTimeTicks,
                CodTdmConfig.getConfig(),
                phaseStateHooks
        );
        this.phase = newPhase;
        this.phaseTimer = enterPhaseResult.phaseTimer();
        this.gameTimeTicks = enterPhaseResult.gameTimeTicks();

        if (newPhase != GamePhase.PLAYING) {
            clearDeathHudForAllPlayers();
        }

        // 同步新阶段给所有客户端
        syncToClient();
    }

    void transitionToEndedFromVote() {
        transitionToPhase(GamePhase.ENDED);
    }

    // ========== 击杀处理 ==========

    /**
     * 处理玩家击杀
     */
    public void onPlayerKill(ServerPlayer killer, ServerPlayer victim) {
        ScoreService.onPlayerKill(
                killer,
                victim,
                phase,
                playerKills,
                playerDeaths,
                teamScores,
                gameTimeTicks,
                scoreServiceHooks
        );
    }

    Optional<BaseTeam> findTeamByPlayer(ServerPlayer player) {
        return getMapTeams().getTeamByPlayer(player);
    }

    // ========== 死亡视角系统 ==========

    /**
     * 更新死亡视角
     */
    private void tickDeathCam() {
        DeathCamService.tickDeathCam(deathCamPlayers, getServerLevel());
    }

    // ========== 复活系统 ==========

    /**
     * 安排玩家复活
     */
    public void scheduleRespawn(ServerPlayer player) {
        RespawnService.scheduleRespawn(respawnTimers, player, CodTdmConfig.getConfig().getRespawnDelayTicks());
    }

    /**
     * 更新复活计时器
     */
    private void tickRespawn() {
        RespawnService.tickRespawn(respawnTimers, getServerLevel(), this::respawnPlayer);
    }

    /**
     * 复活玩家
     */
    private void respawnPlayer(ServerPlayer player) {
        RespawnService.respawnPlayer(
                player,
                this::teleportPlayerToReSpawnPoint,
                this::givePlayerKits,
                CodTdmConfig.getConfig().getInvincibilityTicks(),
                invinciblePlayers,
                invincibilityTimers
        );
    }

    /**
     * 发放玩家装备 (基于背包系统)
     */
    @Override
    public void givePlayerKits(ServerPlayer player) {
        KitDistributionService.distributePlayerKits(player);
    }

    /**
     * 处理玩家真实死亡逻辑 (被 Kill 或 致命伤)
     */
    public void onPlayerDead(ServerPlayer player, ServerPlayer killer) {
        PlayerDeathService.onPlayerDead(player, killer, CodTdmConfig.getConfig(), playerDeathHooks);
    }

    void clearPlayerInventoryForHooks(ServerPlayer player) {
        clearPlayerInventory(player);
    }

    /**
     * 清空所有玩家背包
     */
    public void clearAllPlayersInventory() {
        getMapTeams().getJoinedPlayers().forEach(pd -> pd.getPlayer().ifPresent(this::clearPlayerInventory));
    }

    /**
     * 更新无敌状态
     */
    private void tickInvincibility() {
        RespawnService.tickInvincibility(invincibilityTimers, invinciblePlayers);
    }

    // ========== 辅助方法 ==========

    private void clearTransientPlayerState(UUID playerId) {
        respawnTimers.remove(playerId);
        invinciblePlayers.remove(playerId);
        invincibilityTimers.remove(playerId);
        deathCamPlayers.remove(playerId);
    }

    private boolean canSwitchWithBalance(String currentTeam, String targetTeam, int maxTeamDiff) {
        return TeamBalanceService.canSwitchWithBalance(getMapTeams().getTeams(), currentTeam, targetTeam, maxTeamDiff);
    }

    public Optional<String> chooseAutoJoinTeam(int maxTeamDiff) {
        return TeamBalanceService.chooseAutoJoinTeam(
                getMapTeams().getTeams(),
                teamName -> getMapTeams().testTeamIsFull(teamName),
                maxTeamDiff
        );
    }

    public boolean canJoinWithBalance(String joiningTeam, int maxTeamDiff) {
        return TeamBalanceService.canJoinWithBalance(getMapTeams().getTeams(), joiningTeam, maxTeamDiff);
    }

    void restoreAllRoomPlayersToAdventure() {
        for (PlayerData playerData : getMapTeams().getJoinedPlayers()) {
            playerData.getPlayer().ifPresent(player -> player.setGameMode(GameType.ADVENTURE));
        }
        for (UUID playerId : getMapTeams().getSpecPlayers()) {
            Player player = getServerLevel().getPlayerByUUID(playerId);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.setGameMode(GameType.ADVENTURE);
            }
        }
    }

    boolean teleportPlayerToMatchEndPoint(ServerPlayer player) {
        if (matchEndTeleportPoint == null) {
            return false;
        }
        teleportToPoint(player, matchEndTeleportPoint);
        return true;
    }

    /**
     * 传送所有玩家到出生点
     */
    void teleportAllPlayersToSpawn() {
        // 先分配复活点给每个玩家
        for (BaseTeam team : getMapTeams().getTeams()) {
            if (!team.randomSpawnPoints()) {
                // 如果队伍没有复活点，打印警告
                getMapTeams().getJoinedPlayers().forEach(pd -> pd.getPlayer().ifPresent(p -> p.sendSystemMessage(
                        Component.translatable("message.codpattern.game.warning_no_spawn", team.name))));
            }
        }

        // 然后传送所有玩家
        for (PlayerData playerData : getMapTeams().getJoinedPlayers()) {
            playerData.getPlayer().ifPresent(this::teleportPlayerToReSpawnPoint);
        }
    }

    /**
     * 给所有玩家发放装备（基于背包系统）
     */
    public void giveAllPlayersKits() {
        for (PlayerData playerData : getMapTeams().getJoinedPlayers()) {
            playerData.getPlayer().ifPresent(this::givePlayerKits);
        }
    }

    /**
     * 热身期间能否造成伤害
     */
    public boolean canDealDamage() {
        return phase == GamePhase.PLAYING;
    }

    /**
     * 检查玩家是否无敌
     */
    public boolean isInvincible(UUID player) {
        return invinciblePlayers.contains(player);
    }

    /**
     * 获取当前游戏阶段
     */
    public GamePhase getPhase() {
        return phase;
    }

    public Map<String, Integer> getTeamScoresSnapshot() {
        return new HashMap<>(teamScores);
    }

    public int getRemainingTimeTicks() {
        return PhaseStateMachine.getRemainingTimeTicks(phase, phaseTimer, gameTimeTicks, CodTdmConfig.getConfig());
    }

    public boolean hasMatchEndTeleportPoint() {
        return matchEndTeleportPoint != null;
    }

    public SpawnPointData getMatchEndTeleportPoint() {
        return matchEndTeleportPoint;
    }

    /**
     * 获取队伍玩家列表（用于GUI显示）
     */
    public Map<String, List<PlayerInfo>> getTeamPlayers() {
        return TeamPlayerSnapshotService.buildTeamPlayers(
                getMapTeams().getTeams(),
                uuid -> getServerLevel().getPlayerByUUID(uuid),
                readyStates,
                playerKills,
                playerDeaths,
                respawnTimers.keySet()
        );
    }

    // ========== 投票系统 ==========

    /**
     * 发起开始投票（兼容旧接口）
     */
    public boolean voteToStart(UUID player) {
        return voteCoordinator.voteToStart(player);
    }

    /**
     * 发起结束投票（兼容旧接口）
     */
    public boolean voteToEnd(UUID player) {
        return voteCoordinator.voteToEnd(player);
    }

    /**
     * 发起开始投票（向当前房间所有玩家弹出接受/拒绝）
     */
    public boolean initiateStartVote(UUID initiator) {
        return voteCoordinator.initiateStartVote(initiator);
    }

    /**
     * 发起结束投票（向当前房间所有玩家弹出接受/拒绝）
     */
    public boolean initiateEndVote(UUID initiator) {
        return voteCoordinator.initiateEndVote(initiator);
    }

    /**
     * 玩家提交投票响应（接受/拒绝）
     */
    public boolean submitVoteResponse(UUID playerId, long voteId, boolean accepted) {
        return voteCoordinator.submitVoteResponse(playerId, voteId, accepted);
    }

    void broadcastToJoinedPlayers(Component message) {
        getMapTeams().getJoinedPlayers().forEach(pd -> pd.getPlayer().ifPresent(p -> p.sendSystemMessage(message)));
    }

    <T> void broadcastPacketToJoinedPlayers(T packet) {
        getMapTeams().getJoinedPlayers().forEach(pd -> pd.getPlayer().ifPresent(p -> ModNetworkChannel.sendToPlayer(packet, p)));
    }

    List<ServerPlayer> getJoinedServerPlayers() {
        List<ServerPlayer> players = new ArrayList<>();
        getMapTeams().getJoinedPlayers().forEach(pd -> pd.getPlayer().ifPresent(players::add));
        return players;
    }

    String mapNameView() {
        return mapName;
    }

    private void clearDeathHudForAllPlayers() {
        broadcastPacketToJoinedPlayers(DeathCamPacket.clear());
    }

    public void initializeReadyState(ServerPlayer player) {
        voteCoordinator.initializeReadyState(player);
    }

    public boolean setPlayerReady(ServerPlayer player, boolean ready) {
        return voteCoordinator.setPlayerReady(player, ready);
    }

    public boolean isPlayerReady(UUID playerId) {
        return voteCoordinator.isPlayerReady(playerId);
    }

    /**
     * 获取投票状态信息
     */
    public String getVoteStatus() {
        return voteCoordinator.getVoteStatus();
    }

    // ========== GiveStartKitsMap 实现 ==========

    @Override
    public ArrayList<ItemStack> getKits(BaseTeam team) {
        return startKits.computeIfAbsent(team.name, k -> new ArrayList<>());
    }

    @Override
    public void addKits(BaseTeam team, ItemStack itemStack) {
        getKits(team).add(itemStack);
    }

    @Override
    public void setStartKits(Map<String, ArrayList<ItemStack>> kits) {
        this.startKits = new HashMap<>(kits);
    }

    @Override
    public void setAllTeamKits(ItemStack itemStack) {
        for (BaseTeam team : getMapTeams().getTeams()) {
            addKits(team, itemStack);
        }
    }

    @Override
    public Map<String, List<ItemStack>> getStartKits() {
        return new HashMap<>(startKits);
    }

    @Override
    public CodTdmMap getMap() {
        return this;
    }

    // ========== EndTeleportMap 实现 ==========

    @Override
    public void setMatchEndTeleportPoint(SpawnPointData data) {
        this.matchEndTeleportPoint = data;
    }
}
