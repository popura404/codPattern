package com.cdp.codpattern.fpsmatch.map;

import com.cdp.codpattern.config.TdmConfig.CodTdmConfig;
import com.cdp.codpattern.core.handler.Weaponhandling;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.handler.PacketHandler;
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

import com.cdp.codpattern.config.BackPackConfig.BackpackConfig;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfigManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import com.tacz.guns.api.item.IGun;
import com.cdp.codpattern.config.WeaponFilterConfig.WeaponFilterConfig;

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

    // ========== 投票系统 ==========
    private VoteSession activeVoteSession = null;
    private long voteSessionSequence = 0L;

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
    private static final int VOTE_TIMEOUT_TICKS = 15 * 20;

    private enum VoteType {
        START,
        END
    }

    private static class VoteSession {
        private final long voteId;
        private final VoteType type;
        private final UUID initiator;
        private final Set<UUID> voters;
        private final Set<UUID> accepted = new HashSet<>();
        private final Set<UUID> rejected = new HashSet<>();
        private int timeoutTicksRemaining;

        private VoteSession(long voteId, VoteType type, UUID initiator, Set<UUID> voters) {
            this.voteId = voteId;
            this.type = type;
            this.initiator = initiator;
            this.voters = voters;
            this.timeoutTicksRemaining = VOTE_TIMEOUT_TICKS;
        }
    }

    /**
     * 构造函数
     */
    public CodTdmMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        super(serverLevel, mapName, areaData);

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
        // 根据当前阶段执行不同逻辑
        switch (phase) {
            case WAITING -> tickWaiting();
            case COUNTDOWN -> tickCountdown();
            case WARMUP -> tickWarmup();
            case PLAYING -> tickPlaying();
            case ENDED -> tickEnded();
        }

        // 更新投票超时
        tickVoteSession();

        // 更新死亡视角
        tickDeathCam();

        // 更新复活计时器
        tickRespawn();

        // 更新无敌状态
        tickInvincibility();
    }

    @Override
    public void syncToClient() {
        // 同步游戏阶段
        int remainingTime = 0;
        CodTdmConfig config = CodTdmConfig.getConfig();

        switch (phase) {
            case COUNTDOWN -> remainingTime = config.getPreGameCountdownTicks() - phaseTimer;
            case WARMUP -> remainingTime = config.getWarmupTimeTicks() - phaseTimer;
            case PLAYING -> remainingTime = (config.getTimeLimitSeconds() * 20) - gameTimeTicks;
            default -> remainingTime = 0;
        }

        GamePhasePacket phasePacket = new GamePhasePacket(phase.name(), remainingTime);

        // 同步分数（按队伍名）
        ScoreUpdatePacket scorePacket = new ScoreUpdatePacket(teamScores, gameTimeTicks);

        // 同步玩家列表
        TeamPlayerListPacket playerListPacket = new TeamPlayerListPacket(mapName, getTeamPlayers());

        // 发送给地图内所有玩家
        for (PlayerData playerData : getMapTeams().getJoinedPlayers()) {
            playerData.getPlayer().ifPresent(player -> {
                PacketHandler.sendToPlayer(phasePacket, player);
                PacketHandler.sendToPlayer(scorePacket, player);
                PacketHandler.sendToPlayer(playerListPacket, player);
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
        if (phase != GamePhase.PLAYING) {
            return false;
        }

        CodTdmConfig config = CodTdmConfig.getConfig();

        // 检查时间限制
        if (gameTimeTicks >= config.getTimeLimitSeconds() * 20) {
            return true;
        }

        // 检查分数限制
        for (Integer score : teamScores.values()) {
            if (score >= config.getScoreLimit()) {
                return true;
            }
        }

        return false;
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
        readyStates.clear();
        clearActiveVoteSession();

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
        readyStates.remove(playerId);
        removePlayerFromActiveVote(playerId);

        if (!teleportPlayerToMatchEndPoint(player)) {
            player.sendSystemMessage(Component.translatable("message.codpattern.game.warning_no_end_teleport", mapName));
        } else {
            getServerLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.0f);
        }

        PacketHandler.sendToPlayer(new GamePhasePacket(GamePhase.WAITING.name(), 0), player);
        PacketHandler.sendToPlayer(DeathCamPacket.clear(), player);
        PacketHandler.sendToPlayer(new ScoreUpdatePacket(0, 0, 0), player);
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
            clearActiveVoteSession();
        }
        this.phase = newPhase;
        this.phaseTimer = 0;

        switch (newPhase) {
            case COUNTDOWN -> {
                // 发送倒计时数据包给所有玩家
                int countdown = CodTdmConfig.getConfig().getPreGameCountdownTicks();
                CountdownPacket packet = new CountdownPacket(countdown, false);
                getMapTeams().getJoinedPlayers()
                        .forEach(pd -> pd.getPlayer().ifPresent(p -> PacketHandler.sendToPlayer(packet, p)));
            }
            case WARMUP -> {
                // 传送所有玩家到热身出生点
                teleportAllPlayersToSpawn();
                // 发放装备
                giveAllPlayersKits();
            }
            case PLAYING -> {
                // 正式比赛开始
                gameTimeTicks = 0;
                // 再次传送所有玩家到出生点 (重置位置)
                teleportAllPlayersToSpawn();
                // 再次发放装备 (重置状态/弹药)
                giveAllPlayersKits();
            }
            case ENDED -> {
                // 游戏结束，清空背包
                clearAllPlayersInventory();
                // 防止玩家停留在旁观模式（如死亡后尚未复活就结束）
                restoreAllRoomPlayersToAdventure();
                deathCamPlayers.clear();
                respawnTimers.clear();
            }
            default -> {
            }
        }

        if (newPhase != GamePhase.PLAYING) {
            clearDeathHudForAllPlayers();
        }

        // 同步新阶段给所有客户端
        syncToClient();
    }

    // ========== 阶段Tick方法 ==========

    private void tickWaiting() {
        // 等待玩家，不执行特殊逻辑
    }

    private void tickCountdown() {
        phaseTimer++;
        CodTdmConfig config = CodTdmConfig.getConfig();
        int totalTicks = config.getPreGameCountdownTicks();
        int remaining = totalTicks - phaseTimer;

        // 每秒发送一次倒计时数据包（非黑屏），让客户端播放倒计时音效
        if (remaining > 0 && remaining % 20 == 0) {
            int timeUntilBlackout = totalTicks - config.getBlackoutStartTicks();
            // 只在黑屏触发之前发送普通倒计时音效包
            if (phaseTimer < timeUntilBlackout) {
                CountdownPacket tickPacket = new CountdownPacket(remaining, false);
                getMapTeams().getJoinedPlayers()
                        .forEach(pd -> pd.getPlayer().ifPresent(p -> PacketHandler.sendToPlayer(tickPacket, p)));
            }
        }

        // 检查是否应该黑屏
        int timeUntilBlackout = totalTicks - config.getBlackoutStartTicks();
        if (phaseTimer == timeUntilBlackout) {
            CountdownPacket packet = new CountdownPacket(config.getBlackoutStartTicks(), true);
            getMapTeams().getJoinedPlayers()
                    .forEach(pd -> pd.getPlayer().ifPresent(p -> PacketHandler.sendToPlayer(packet, p)));
        }

        // 倒计时结束，进入热身
        if (phaseTimer >= totalTicks) {
            transitionToPhase(GamePhase.WARMUP);
        }
    }

    private void tickWarmup() {
        phaseTimer++;
        CodTdmConfig config = CodTdmConfig.getConfig();

        // 热身结束，进入正式比赛
        if (phaseTimer >= config.getWarmupTimeTicks()) {
            transitionToPhase(GamePhase.PLAYING);
        }
    }

    private void tickPlaying() {
        gameTimeTicks++;
        // 每秒同步一次分数和时间 (20 ticks)
        if (gameTimeTicks % 20 == 0) {
            ScoreUpdatePacket scorePacket = new ScoreUpdatePacket(teamScores, gameTimeTicks);
            getMapTeams().getJoinedPlayers()
                    .forEach(pd -> pd.getPlayer().ifPresent(p -> PacketHandler.sendToPlayer(scorePacket, p)));
        }
    }

    private void tickEnded() {
        phaseTimer++;

        // 5秒后传送到结束点
        if (phaseTimer >= 100) {
            if (hasMatchEndTeleportPoint()) {
                for (PlayerData playerData : getMapTeams().getJoinedPlayers()) {
                    playerData.getPlayer().ifPresent(this::teleportPlayerToMatchEndPoint);
                }
            } else {
                getMapTeams().getJoinedPlayers().forEach(pd -> pd.getPlayer().ifPresent(
                        p -> p.sendSystemMessage(
                                Component.translatable("message.codpattern.game.warning_no_end_teleport", mapName))));
            }
            resetGame();
        }
    }

    // ========== 击杀处理 ==========

    /**
     * 处理玩家击杀
     */
    public void onPlayerKill(ServerPlayer killer, ServerPlayer victim) {
        if (phase != GamePhase.PLAYING || killer == null || victim == null) {
            return;
        }

        // 更新击杀/死亡统计
        playerKills.merge(killer.getUUID(), 1, Integer::sum);
        playerDeaths.merge(victim.getUUID(), 1, Integer::sum);

        // 更新队伍分数
        getMapTeams().getTeamByPlayer(killer).ifPresent(team -> {
            teamScores.merge(team.name, 1, Integer::sum);
            // 立即同步分数
            ScoreUpdatePacket scorePacket = new ScoreUpdatePacket(teamScores, gameTimeTicks);
            getMapTeams().getJoinedPlayers()
                    .forEach(pd -> pd.getPlayer().ifPresent(p -> PacketHandler.sendToPlayer(scorePacket, p)));
        });
        CodTdmRoomManager.getInstance().markRoomListDirty();

        // 取消这里的复活调用，统一由 onPlayerDead 处理
        // scheduleRespawn(victim);
    }

    // ========== 死亡视角系统 ==========

    /**
     * 更新死亡视角
     */
    private void tickDeathCam() {
        Iterator<Map.Entry<UUID, DeathCamData>> iterator = deathCamPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DeathCamData> entry = iterator.next();
            if (entry.getValue().tick()) {
                iterator.remove();
            } else {
                // 强制锁定玩家位置
                UUID uuid = entry.getKey();
                DeathCamData data = entry.getValue();
                Player player = getServerLevel().getPlayerByUUID(uuid);

                if (player instanceof ServerPlayer sp && sp.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                    Vec3 camPos = data.getCameraPos();
                    Vec3 deathPos = data.getDeathPos();

                    // 如果距离太远（移动了），拉回来
                    if (sp.position().distanceToSqr(camPos) > 0.01) {
                        sp.teleportTo(getServerLevel(), camPos.x, camPos.y, camPos.z, Set.of(), sp.getYRot(),
                                sp.getXRot());
                    }
                    // 始终看向死亡点
                    sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, deathPos);
                }
            }
        }
    }

    // ========== 复活系统 ==========

    /**
     * 安排玩家复活
     */
    public void scheduleRespawn(ServerPlayer player) {
        CodTdmConfig config = CodTdmConfig.getConfig();
        respawnTimers.put(player.getUUID(), config.getRespawnDelayTicks());
    }

    /**
     * 更新复活计时器
     */
    private void tickRespawn() {
        Iterator<Map.Entry<UUID, Integer>> iterator = respawnTimers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int newTime = entry.getValue() - 1;

            if (newTime <= 0) {
                // 复活玩家
                UUID playerId = entry.getKey();
                Player player = getServerLevel().getPlayerByUUID(playerId);
                if (player instanceof ServerPlayer serverPlayer) {
                    respawnPlayer(serverPlayer);
                }
                iterator.remove();
            } else {
                entry.setValue(newTime);
            }
        }
    }

    /**
     * 复活玩家
     */
    private void respawnPlayer(ServerPlayer player) {
        // 恢复游戏模式为冒险模式
        player.setGameMode(GameType.ADVENTURE);

        // 传送到出生点
        teleportPlayerToReSpawnPoint(player);

        // 恢复状态
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.removeAllEffects(); // 清除药水效果

        // 发放装备
        givePlayerKits(player);

        // 添加无敌状态
        CodTdmConfig config = CodTdmConfig.getConfig();
        invinciblePlayers.add(player.getUUID());
        invincibilityTimers.put(player.getUUID(), config.getInvincibilityTicks());
        PacketHandler.sendToPlayer(DeathCamPacket.clear(), player);
    }

    /**
     * 发放玩家装备 (基于背包系统)
     */
    @Override
    public void givePlayerKits(ServerPlayer player) {
        // 清空背包
        player.getInventory().clearContent();

        // 获取背包配置
        BackpackConfig config = BackpackConfigManager.getConfig();
        if (config == null) {
            // 如果配置没加载，尝试使用默认逻辑（虽然可能也没东西）
            return;
        }

        // 获取玩家数据
        BackpackConfig.PlayerBackpackData playerData = config.getOrCreatePlayerData(player.getStringUUID());
        if (playerData == null)
            return;

        // 获取选中的背包
        BackpackConfig.Backpack backpack = resolveBackpack(playerData);

        // 获取武器过滤配置 (用于备弹倍率等)
        WeaponFilterConfig filterConfig = WeaponFilterConfig.getWeaponFilterConfig();
        // 默认备弹倍率为6，如果配置存在则使用配置值
        int ammoMultiple = (filterConfig != null && filterConfig.getAmmunitionPerMagazineMultiple() != null)
                ? filterConfig.getAmmunitionPerMagazineMultiple()
                : 6;
        ammoMultiple = Math.max(0, ammoMultiple);
        // 检查是否启用投掷物
        boolean throwablesEnabled = (filterConfig == null) || filterConfig.isThrowablesEnabled();

        if (backpack != null) {
            // 发放物品到指定槽位
            giveBackpackItem(player, backpack, "primary", 0, ammoMultiple); // 主武器 -> 槽位 0
            giveBackpackItem(player, backpack, "secondary", 1, ammoMultiple); // 副武器 -> 槽位 1

            if (throwablesEnabled) {
                giveBackpackItem(player, backpack, "tactical", 2, 0); // 战术 -> 槽位 2
                giveBackpackItem(player, backpack, "lethal", 3, 0); // 致命 -> 槽位 3
            }

            // 发送消息
            player.sendSystemMessage(
                    Component.translatable("message.codpattern.game.equipped_backpack", backpack.getName()));
        }

        // 刷新背包
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
    }

    private void giveBackpackItem(ServerPlayer player, BackpackConfig.Backpack backpack, String key, int slot,
            int ammoMultiple) {
        BackpackConfig.Backpack.ItemData itemData = backpack.getItem_MAP().get(key);
        if (itemData != null) {
            ResourceLocation itemId;
            try {
                itemId = new ResourceLocation(itemData.getItem());
            } catch (Exception e) {
                return;
            }
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                ItemStack stack = new ItemStack(item, Math.max(1, itemData.getCount()));
                if (itemData.getNbt() != null && !itemData.getNbt().isEmpty()) {
                    try {
                        CompoundTag tag = TagParser.parseTag(itemData.getNbt());
                        stack.setTag(tag);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // 处理备用弹药 (TACZ Guns)
                if (stack.getItem() instanceof IGun) {
                    Weaponhandling.configureGunAmmo(stack, (IGun) stack.getItem(), ammoMultiple);
                }

                player.getInventory().setItem(slot, stack);
            }
        }
    }

    private BackpackConfig.Backpack resolveBackpack(BackpackConfig.PlayerBackpackData playerData) {
        if (playerData == null || playerData.getBackpacks_MAP() == null || playerData.getBackpacks_MAP().isEmpty()) {
            return null;
        }
        BackpackConfig.Backpack selected = playerData.getBackpacks_MAP().get(playerData.getSelectedBackpack());
        if (selected != null) {
            return selected;
        }
        Integer fallbackId = playerData.getBackpacks_MAP().keySet().stream().min(Integer::compareTo).orElse(null);
        if (fallbackId == null) {
            return null;
        }
        playerData.setSelectedBackpack(fallbackId);
        return playerData.getBackpacks_MAP().get(fallbackId);
    }

    /**
     * 处理玩家真实死亡逻辑 (被 Kill 或 致命伤)
     */
    public void onPlayerDead(ServerPlayer player, ServerPlayer killer) {
        Vec3 deathPos = player.position();
        Vec3 deathVelocity = player.getDeltaMovement();

        // 先广播死亡快照，给 physicsmod 触发 ragdoll/mob 保留（含死者本人）
        PhysicsMobRetainPacket packet = new PhysicsMobRetainPacket(
                player.getId(),
                deathPos.x,
                deathPos.y,
                deathPos.z,
                player.getYRot(),
                player.getXRot(),
                player.getYHeadRot(),
                player.yBodyRot,
                deathVelocity.x,
                deathVelocity.y,
                deathVelocity.z);
        for (PlayerData playerData : getMapTeams().getJoinedPlayers()) {
            playerData.getPlayer().ifPresent(target -> PacketHandler.sendToPlayer(packet, target));
        }

        // 设置为旁观模式，避免移动和交互
        player.setGameMode(GameType.SPECTATOR);

        // 清空背包（防止死亡掉落，虽然已经取消了死亡事件，加上更保险）
        clearPlayerInventory(player);

        // 计算死亡视角位置：身后稍远、视角稍高
        Vec3 look = player.getLookAngle();
        Vec3 pos = deathPos;
        // 反向 2.6 格，抬高 0.75 格
        Vec3 camPos = pos.add(look.scale(-2.6)).add(0, 0.75, 0);

        // 传送玩家并使其朝向死亡点
        player.teleportTo(getServerLevel(), camPos.x, camPos.y, camPos.z, Set.of(), player.getYRot(), player.getXRot());
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, pos);

        // 创建死亡视角数据，用于在 tickDeathCam 中锁定玩家位置
        CodTdmConfig config = CodTdmConfig.getConfig();
        int deathCamTicks = Math.max(0, config.getDeathCamTicks());
        int respawnDelayTicks = Math.max(1, config.getRespawnDelayTicks());
        boolean hasRealKiller = killer != null && !killer.getUUID().equals(player.getUUID());
        UUID killerId = hasRealKiller ? killer.getUUID() : player.getUUID();
        String killerName = hasRealKiller ? killer.getGameProfile().getName() : "Unknown";

        PacketHandler.sendToPlayer(new DeathCamPacket(killerId, killerName, deathCamTicks, respawnDelayTicks), player);

        DeathCamData camData = new DeathCamData(
                player.getUUID(),
                killerId,
                pos,
                pos,
                camPos,
                deathCamTicks);
        if (deathCamTicks > 0) {
            deathCamPlayers.put(player.getUUID(), camData);
        }

        // 安排复活
        scheduleRespawn(player);
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
        Iterator<Map.Entry<UUID, Integer>> iterator = invincibilityTimers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int newTime = entry.getValue() - 1;

            if (newTime <= 0) {
                // 无敌时间结束，移除无敌状态
                invinciblePlayers.remove(entry.getKey());
                iterator.remove();
            } else {
                entry.setValue(newTime);
            }
        }
    }

    // ========== 辅助方法 ==========

    private void clearTransientPlayerState(UUID playerId) {
        respawnTimers.remove(playerId);
        invinciblePlayers.remove(playerId);
        invincibilityTimers.remove(playerId);
        deathCamPlayers.remove(playerId);
    }

    private boolean canSwitchWithBalance(String currentTeam, String targetTeam, int maxTeamDiff) {
        if (maxTeamDiff < 0) {
            return true;
        }
        int minPlayers = Integer.MAX_VALUE;
        int maxPlayers = Integer.MIN_VALUE;
        for (BaseTeam team : getMapTeams().getTeams()) {
            int size = team.getPlayerList().size();
            if (team.name.equals(currentTeam)) {
                size = Math.max(0, size - 1);
            }
            if (team.name.equals(targetTeam)) {
                size += 1;
            }
            minPlayers = Math.min(minPlayers, size);
            maxPlayers = Math.max(maxPlayers, size);
        }
        if (minPlayers == Integer.MAX_VALUE || maxPlayers == Integer.MIN_VALUE) {
            return true;
        }
        return (maxPlayers - minPlayers) <= maxTeamDiff;
    }

    private void restoreAllRoomPlayersToAdventure() {
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

    private boolean teleportPlayerToMatchEndPoint(ServerPlayer player) {
        if (matchEndTeleportPoint == null) {
            return false;
        }
        teleportToPoint(player, matchEndTeleportPoint);
        return true;
    }

    /**
     * 传送所有玩家到出生点
     */
    private void teleportAllPlayersToSpawn() {
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
        CodTdmConfig config = CodTdmConfig.getConfig();
        return switch (phase) {
            case COUNTDOWN -> Math.max(0, config.getPreGameCountdownTicks() - phaseTimer);
            case WARMUP -> Math.max(0, config.getWarmupTimeTicks() - phaseTimer);
            case PLAYING -> Math.max(0, (config.getTimeLimitSeconds() * 20) - gameTimeTicks);
            default -> 0;
        };
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
        Map<String, List<PlayerInfo>> result = new HashMap<>();

        for (BaseTeam team : getMapTeams().getTeams()) {
            List<PlayerInfo> playerInfos = new ArrayList<>();

            for (UUID playerId : team.getPlayerList()) {
                Player player = getServerLevel().getPlayerByUUID(playerId);
                if (player instanceof ServerPlayer serverPlayer) {
                    PlayerInfo info = new PlayerInfo(
                            playerId,
                            serverPlayer.getName().getString(),
                            readyStates.getOrDefault(playerId, false),
                            playerKills.getOrDefault(playerId, 0),
                            playerDeaths.getOrDefault(playerId, 0),
                            !respawnTimers.containsKey(playerId), // isAlive
                            Math.max(0, serverPlayer.latency)
                    );
                    playerInfos.add(info);
                }
            }

            playerInfos.sort((a, b) -> {
                int byKills = Integer.compare(b.kills(), a.kills());
                if (byKills != 0) {
                    return byKills;
                }
                int byDeaths = Integer.compare(a.deaths(), b.deaths());
                if (byDeaths != 0) {
                    return byDeaths;
                }
                return a.name().compareToIgnoreCase(b.name());
            });
            result.put(team.name, playerInfos);
        }

        return result;
    }

    // ========== 投票系统 ==========

    /**
     * 发起开始投票（兼容旧接口）
     */
    public boolean voteToStart(UUID player) {
        return initiateStartVote(player);
    }

    /**
     * 发起结束投票（兼容旧接口）
     */
    public boolean voteToEnd(UUID player) {
        return initiateEndVote(player);
    }

    /**
     * 发起开始投票（向当前房间所有玩家弹出接受/拒绝）
     */
    public boolean initiateStartVote(UUID initiator) {
        return initiateVote(VoteType.START, initiator);
    }

    /**
     * 发起结束投票（向当前房间所有玩家弹出接受/拒绝）
     */
    public boolean initiateEndVote(UUID initiator) {
        return initiateVote(VoteType.END, initiator);
    }

    /**
     * 玩家提交投票响应（接受/拒绝）
     */
    public boolean submitVoteResponse(UUID playerId, long voteId, boolean accepted) {
        if (activeVoteSession == null || activeVoteSession.voteId != voteId) {
            Player player = getServerLevel().getPlayerByUUID(playerId);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.codpattern.game.vote_expired"));
            }
            return false;
        }

        VoteSession session = activeVoteSession;
        if (!session.voters.contains(playerId)) {
            return false;
        }
        if (session.accepted.contains(playerId) || session.rejected.contains(playerId)) {
            Player player = getServerLevel().getPlayerByUUID(playerId);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.codpattern.game.already_voted"));
            }
            return false;
        }

        if (accepted) {
            session.accepted.add(playerId);
        } else {
            session.rejected.add(playerId);
        }

        broadcastVoteProgress(session);
        CodTdmRoomManager.getInstance().markRoomListDirty();
        return resolveVoteIfReady(session);
    }

    private boolean initiateVote(VoteType type, UUID initiator) {
        Player initiatorPlayer = getServerLevel().getPlayerByUUID(initiator);
        if (initiatorPlayer == null) {
            return false;
        }

        if (activeVoteSession != null) {
            initiatorPlayer.sendSystemMessage(Component.translatable("message.codpattern.game.vote_in_progress"));
            return false;
        }

        if (type == VoteType.START) {
            if (phase != GamePhase.WAITING) {
                initiatorPlayer.sendSystemMessage(Component.translatable("message.codpattern.game.already_started"));
                return false;
            }
        } else if (phase != GamePhase.PLAYING && phase != GamePhase.WARMUP) {
            initiatorPlayer.sendSystemMessage(Component.translatable("message.codpattern.game.not_started"));
            return false;
        }

        List<ServerPlayer> joinedPlayers = getJoinedServerPlayers();
        if (joinedPlayers.isEmpty()) {
            return false;
        }

        int totalPlayers = joinedPlayers.size();
        CodTdmConfig config = CodTdmConfig.getConfig();
        if (type == VoteType.START && totalPlayers < config.getMinPlayersToStart()) {
            initiatorPlayer.sendSystemMessage(Component.translatable("message.codpattern.game.min_players_warning",
                    config.getMinPlayersToStart(), totalPlayers));
            return false;
        }

        if (type == VoteType.START) {
            long unreadyCount = joinedPlayers.stream()
                    .filter(joinedPlayer -> !readyStates.getOrDefault(joinedPlayer.getUUID(), false))
                    .count();
            if (unreadyCount > 0) {
                initiatorPlayer.sendSystemMessage(Component.literal("§e仍有玩家未准备，无法发起开始投票。"));
                return false;
            }
        }

        Set<UUID> voters = new HashSet<>();
        for (ServerPlayer joinedPlayer : joinedPlayers) {
            voters.add(joinedPlayer.getUUID());
        }

        VoteSession session = new VoteSession(++voteSessionSequence, type, initiator, voters);
        activeVoteSession = session;

        String initiatorName = initiatorPlayer.getName().getString();
        Component startMessage = type == VoteType.START
                ? Component.translatable("message.codpattern.game.vote_initiated_start", initiatorName)
                : Component.translatable("message.codpattern.game.vote_initiated_end", initiatorName);
        broadcastToJoinedPlayers(startMessage);

        int requiredVotes = getRequiredVotes(type, totalPlayers);
        VoteDialogPacket dialogPacket = new VoteDialogPacket(mapName, session.voteId, type.name(), initiatorName,
                requiredVotes, totalPlayers);
        for (ServerPlayer joinedPlayer : joinedPlayers) {
            PacketHandler.sendToPlayer(dialogPacket, joinedPlayer);
        }

        CodTdmRoomManager.getInstance().markRoomListDirty();

        return true;
    }

    private boolean resolveVoteIfReady(VoteSession session) {
        if (activeVoteSession == null || activeVoteSession != session) {
            return false;
        }

        if (session.voters.isEmpty()) {
            clearActiveVoteSession();
            return false;
        }

        if (session.type == VoteType.START) {
            int totalPlayers = session.voters.size();
            CodTdmConfig config = CodTdmConfig.getConfig();
            if (totalPlayers < config.getMinPlayersToStart()) {
                broadcastToJoinedPlayers(Component.translatable("message.codpattern.game.min_players_warning",
                        config.getMinPlayersToStart(), totalPlayers));
                broadcastToJoinedPlayers(Component.translatable("message.codpattern.game.vote_failed"));
                clearActiveVoteSession();
                return false;
            }
        } else if (phase != GamePhase.PLAYING && phase != GamePhase.WARMUP) {
            clearActiveVoteSession();
            return false;
        }

        int totalPlayers = session.voters.size();
        int requiredVotes = getRequiredVotes(session.type, totalPlayers);
        int acceptCount = session.accepted.size();
        int rejectCount = session.rejected.size();
        int unresolvedCount = Math.max(0, totalPlayers - acceptCount - rejectCount);

        if (acceptCount >= requiredVotes) {
            if (session.type == VoteType.START) {
                broadcastToJoinedPlayers(Component.translatable("message.codpattern.game.vote_passed"));
                clearActiveVoteSession();
                startGame();
            } else {
                broadcastToJoinedPlayers(Component.translatable("message.codpattern.game.vote_passed_end"));
                clearActiveVoteSession();
                transitionToPhase(GamePhase.ENDED);
            }
            return true;
        }

        if (acceptCount + unresolvedCount < requiredVotes) {
            Component failMessage = session.type == VoteType.START
                    ? Component.translatable("message.codpattern.game.vote_failed")
                    : Component.translatable("message.codpattern.game.vote_failed_end");
            broadcastToJoinedPlayers(failMessage);
            clearActiveVoteSession();
            return false;
        }

        if (acceptCount + rejectCount >= totalPlayers) {
            Component failMessage = session.type == VoteType.START
                    ? Component.translatable("message.codpattern.game.vote_failed")
                    : Component.translatable("message.codpattern.game.vote_failed_end");
            broadcastToJoinedPlayers(failMessage);
            clearActiveVoteSession();
            return false;
        }

        return false;
    }

    private int getRequiredVotes(VoteType type, int totalPlayers) {
        CodTdmConfig config = CodTdmConfig.getConfig();
        int votePercent = type == VoteType.START ? config.getVotePercentageToStart() : config.getVotePercentageToEnd();
        int requiredVotes = (int) Math.ceil(totalPlayers * (votePercent / 100.0));
        return Math.max(1, Math.min(Math.max(1, totalPlayers), requiredVotes));
    }

    private void tickVoteSession() {
        if (activeVoteSession == null) {
            return;
        }
        VoteSession session = activeVoteSession;
        session.timeoutTicksRemaining--;
        if (session.timeoutTicksRemaining > 0) {
            return;
        }

        Component timeoutMessage = session.type == VoteType.START
                ? Component.translatable("message.codpattern.game.vote_timeout_start")
                : Component.translatable("message.codpattern.game.vote_timeout_end");
        broadcastToJoinedPlayers(timeoutMessage);
        clearActiveVoteSession();
        CodTdmRoomManager.getInstance().markRoomListDirty();
    }

    private void broadcastVoteProgress(VoteSession session) {
        int totalPlayers = session.voters.size();
        int requiredVotes = getRequiredVotes(session.type, totalPlayers);
        int acceptCount = session.accepted.size();
        int rejectCount = session.rejected.size();

        Component progressMessage = session.type == VoteType.START
                ? Component.translatable("message.codpattern.game.vote_progress_start", acceptCount, rejectCount,
                        totalPlayers, requiredVotes)
                : Component.translatable("message.codpattern.game.vote_progress_end", acceptCount, rejectCount,
                        totalPlayers, requiredVotes);
        broadcastToJoinedPlayers(progressMessage);
    }

    private void broadcastToJoinedPlayers(Component message) {
        getMapTeams().getJoinedPlayers().forEach(pd -> pd.getPlayer().ifPresent(p -> p.sendSystemMessage(message)));
    }

    private List<ServerPlayer> getJoinedServerPlayers() {
        List<ServerPlayer> players = new ArrayList<>();
        getMapTeams().getJoinedPlayers().forEach(pd -> pd.getPlayer().ifPresent(players::add));
        return players;
    }

    private void clearActiveVoteSession() {
        activeVoteSession = null;
    }

    private void removePlayerFromActiveVote(UUID playerId) {
        if (activeVoteSession == null) {
            return;
        }

        VoteSession session = activeVoteSession;
        if (!session.voters.remove(playerId)) {
            return;
        }
        session.accepted.remove(playerId);
        session.rejected.remove(playerId);
        resolveVoteIfReady(session);
        CodTdmRoomManager.getInstance().markRoomListDirty();
    }

    private void clearDeathHudForAllPlayers() {
        getMapTeams().getJoinedPlayers().forEach(pd -> pd.getPlayer()
                .ifPresent(p -> PacketHandler.sendToPlayer(DeathCamPacket.clear(), p)));
    }

    public void initializeReadyState(ServerPlayer player) {
        readyStates.put(player.getUUID(), false);
    }

    public boolean setPlayerReady(ServerPlayer player, boolean ready) {
        if (phase != GamePhase.WAITING) {
            return false;
        }
        UUID playerId = player.getUUID();
        if (!checkGameHasPlayer(playerId)) {
            return false;
        }
        readyStates.put(playerId, ready);
        syncToClient();
        return true;
    }

    public boolean isPlayerReady(UUID playerId) {
        return readyStates.getOrDefault(playerId, false);
    }

    /**
     * 获取投票状态信息
     */
    public String getVoteStatus() {
        if (activeVoteSession != null) {
            int requiredVotes = getRequiredVotes(activeVoteSession.type, activeVoteSession.voters.size());
            int acceptedVotes = activeVoteSession.accepted.size();
            if (activeVoteSession.type == VoteType.START) {
                return Component.translatable("message.codpattern.game.status_vote_start", acceptedVotes, requiredVotes)
                        .getString();
            }
            return Component.translatable("message.codpattern.game.status_vote_end", acceptedVotes, requiredVotes)
                    .getString();
        }
        return "";
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
