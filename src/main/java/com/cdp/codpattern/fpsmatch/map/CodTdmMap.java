package com.cdp.codpattern.fpsmatch.map;

import com.cdp.codpattern.config.TdmConfig.CodTdmConfig;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.handler.PacketHandler;
import com.cdp.codpattern.network.tdm.CountdownPacket;
import com.cdp.codpattern.network.tdm.GamePhasePacket;
import com.cdp.codpattern.network.tdm.ScoreUpdatePacket;
import com.cdp.codpattern.network.tdm.TeamPlayerListPacket;
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

    // ========== 投票系统 ==========
    private final Set<UUID> startVotes = new HashSet<>();
    private final Set<UUID> endVotes = new HashSet<>();

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

        // 同步分数
        int team1Score = 0;
        int team2Score = 0;
        List<BaseTeam> teams = getMapTeams().getTeams();
        if (teams.size() >= 1)
            team1Score = teamScores.getOrDefault(teams.get(0).name, 0);
        if (teams.size() >= 2)
            team2Score = teamScores.getOrDefault(teams.get(1).name, 0);

        ScoreUpdatePacket scorePacket = new ScoreUpdatePacket(team1Score, team2Score, gameTimeTicks);

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
        startVotes.clear();
        endVotes.clear();

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
        startVotes.remove(playerId);
        endVotes.remove(playerId);

        if (!teleportPlayerToMatchEndPoint(player)) {
            player.sendSystemMessage(Component.translatable("message.codpattern.game.warning_no_end_teleport", mapName));
        } else {
            getServerLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.0f);
        }

        PacketHandler.sendToPlayer(new GamePhasePacket(GamePhase.WAITING.name(), 0), player);
        PacketHandler.sendToPlayer(new ScoreUpdatePacket(0, 0, 0), player);
        clearPlayerInventory(player);
        super.leave(player);
        syncToClient();
    }

    /**
     * 切换队伍（不会触发离房传送）。
     */
    public void switchTeam(ServerPlayer player, String teamName) {
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
            }
            default -> {
            }
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
            int team1Score = 0;
            int team2Score = 0;
            List<BaseTeam> teams = getMapTeams().getTeams();
            if (teams.size() >= 1)
                team1Score = teamScores.getOrDefault(teams.get(0).name, 0);
            if (teams.size() >= 2)
                team2Score = teamScores.getOrDefault(teams.get(1).name, 0);

            ScoreUpdatePacket scorePacket = new ScoreUpdatePacket(team1Score, team2Score, gameTimeTicks);
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
                    playerData.getPlayer().ifPresent(player -> teleportPlayerToMatchEndPoint(player));
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
        if (phase != GamePhase.PLAYING) {
            return;
        }

        // 更新击杀/死亡统计
        playerKills.merge(killer.getUUID(), 1, Integer::sum);
        playerDeaths.merge(victim.getUUID(), 1, Integer::sum);

        // 更新队伍分数
        getMapTeams().getTeamByPlayer(killer).ifPresent(team -> {
            teamScores.merge(team.name, 1, Integer::sum);
            // 立即同步分数
            int team1Score = 0;
            int team2Score = 0;
            List<BaseTeam> teams = getMapTeams().getTeams();
            if (teams.size() >= 1)
                team1Score = teamScores.getOrDefault(teams.get(0).name, 0);
            if (teams.size() >= 2)
                team2Score = teamScores.getOrDefault(teams.get(1).name, 0);

            ScoreUpdatePacket scorePacket = new ScoreUpdatePacket(team1Score, team2Score, gameTimeTicks);
            getMapTeams().getJoinedPlayers()
                    .forEach(pd -> pd.getPlayer().ifPresent(p -> PacketHandler.sendToPlayer(scorePacket, p)));
        });

        // 取消这里的复活调用，统一由 onPlayerDead 处理
        // scheduleRespawn(victim);
    }

    // ========== 死亡视角系统 ==========

    /**
     * 启动死亡视角
     */
    // 移除 startDeathCam，合并到 onPlayerDead 处理

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
        int selectedId = playerData.getSelectedBackpack();
        BackpackConfig.Backpack backpack = playerData.getBackpacks_MAP().get(selectedId);

        // 获取武器过滤配置 (用于备弹倍率等)
        WeaponFilterConfig filterConfig = WeaponFilterConfig.getWeaponFilterConfig();
        // 默认备弹倍率为6，如果配置存在则使用配置值
        int ammoMultiple = (filterConfig != null && filterConfig.getAmmunitionPerMagazineMultiple() != null)
                ? filterConfig.getAmmunitionPerMagazineMultiple()
                : 6;
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
            ResourceLocation itemId = new ResourceLocation(itemData.getItem());
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item != null) {
                ItemStack stack = new ItemStack(item, itemData.getCount());
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
                    IGun iGun = (IGun) stack.getItem();
                    int dummyAmmo = iGun.getCurrentAmmoCount(stack) * ammoMultiple;
                    iGun.setDummyAmmoAmount(stack, dummyAmmo);
                }

                player.getInventory().setItem(slot, stack);
            }
        }
    }

    /**
     * 处理玩家真实死亡逻辑 (被 Kill 或 致命伤)
     */
    public void onPlayerDead(ServerPlayer player) {
        // 设置为旁观模式，避免移动和交互
        player.setGameMode(GameType.SPECTATOR);

        // 清空背包（防止死亡掉落，虽然已经取消了死亡事件，加上更保险）
        clearPlayerInventory(player);

        // 计算死亡视角位置：身后 2 格
        Vec3 look = player.getLookAngle();
        Vec3 pos = player.position();
        // 反向 2 格，稍微抬高一点视线
        Vec3 camPos = pos.add(look.scale(-2)).add(0, 0.5, 0);

        // 传送玩家并使其朝向死亡点
        player.teleportTo(getServerLevel(), camPos.x, camPos.y, camPos.z, Set.of(), player.getYRot(), player.getXRot());
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, pos);

        // 创建死亡视角数据，用于在 tickDeathCam 中锁定玩家位置
        CodTdmConfig config = CodTdmConfig.getConfig();
        // 如果没有 killer，就用自己作为 killer 位置（虽然不重要，因为我们现在是固定视角看尸体）
        DeathCamData camData = new DeathCamData(
                player.getUUID(),
                player.getUUID(), // killer unknown in this context, use self
                pos, // death pos
                pos, // killer pos (unused)
                camPos, // camera pos
                config.getRespawnDelayTicks() // 使用复活延迟时间
        );
        deathCamPlayers.put(player.getUUID(), camData);

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
                            phase == GamePhase.WAITING, // isReady (在WAITING阶段显示)
                            playerKills.getOrDefault(playerId, 0),
                            playerDeaths.getOrDefault(playerId, 0),
                            !respawnTimers.containsKey(playerId) // isAlive
                    );
                    playerInfos.add(info);
                }
            }

            result.put(team.name, playerInfos);
        }

        return result;
    }

    // ========== 投票系统 ==========

    /**
     * 投票开始游戏
     * 
     * @return 是否成功投票
     */
    public boolean voteToStart(UUID player) {
        // 只有在等待阶段才能投票开始
        if (phase != GamePhase.WAITING) {
            Player p = getServerLevel().getPlayerByUUID(player);
            if (p != null)
                p.sendSystemMessage(Component.translatable("message.codpattern.game.already_started"));
            return false;
        }

        // 检查是否已投票
        if (startVotes.contains(player)) {
            Player p = getServerLevel().getPlayerByUUID(player);
            if (p != null)
                p.sendSystemMessage(Component.translatable("message.codpattern.game.already_voted"));
            return false;
        }

        startVotes.add(player);

        int totalPlayers = getMapTeams().getJoinedPlayers().size();
        CodTdmConfig config = CodTdmConfig.getConfig();
        int requiredVotes = Math.max(1, (totalPlayers * config.getVotePercentageToStart()) / 100);

        // 广播投票信息
        getMapTeams().getJoinedPlayers().forEach(pd -> pd.getPlayer().ifPresent(p -> p.sendSystemMessage(Component
                .translatable("message.codpattern.game.vote_start", startVotes.size(), totalPlayers, requiredVotes))));

        return checkStartVote();
    }

    /**
     * 投票结束游戏
     * 
     * @return 是否成功投票
     */
    public boolean voteToEnd(UUID player) {
        // 只有在游戏中才能投票结束
        if (phase != GamePhase.PLAYING && phase != GamePhase.WARMUP) {
            Player p = getServerLevel().getPlayerByUUID(player);
            if (p != null)
                p.sendSystemMessage(Component.translatable("message.codpattern.game.not_started"));
            return false;
        }

        // 检查是否已投票
        if (endVotes.contains(player)) {
            Player p = getServerLevel().getPlayerByUUID(player);
            if (p != null)
                p.sendSystemMessage(Component.translatable("message.codpattern.game.already_voted"));
            return false;
        }

        endVotes.add(player);

        int totalPlayers = getMapTeams().getJoinedPlayers().size();
        CodTdmConfig config = CodTdmConfig.getConfig();
        int requiredVotes = Math.max(1, (totalPlayers * config.getVotePercentageToEnd()) / 100);

        // 广播投票信息
        getMapTeams().getJoinedPlayers().forEach(pd -> pd.getPlayer().ifPresent(p -> p.sendSystemMessage(Component
                .translatable("message.codpattern.game.vote_end", endVotes.size(), totalPlayers, requiredVotes))));

        return checkEndVote();
    }

    /**
     * 检查开始投票
     */
    public boolean checkStartVote() {
        int totalPlayers = getMapTeams().getJoinedPlayers().size();
        CodTdmConfig config = CodTdmConfig.getConfig();

        if (totalPlayers < config.getMinPlayersToStart()) {
            getMapTeams().getJoinedPlayers().forEach(pd -> pd.getPlayer().ifPresent(
                    p -> p.sendSystemMessage(Component.translatable("message.codpattern.game.min_players_warning",
                            config.getMinPlayersToStart(), totalPlayers))));
            return false;
        }

        int requiredVotes = Math.max(1, (totalPlayers * config.getVotePercentageToStart()) / 100);
        if (startVotes.size() >= requiredVotes) {
            getMapTeams().getJoinedPlayers().forEach(pd -> pd.getPlayer().ifPresent(
                    p -> p.sendSystemMessage(Component.translatable("message.codpattern.game.vote_passed"))));
            startGame();
            return true;
        }

        return false;
    }

    /**
     * 检查结束投票
     */
    public boolean checkEndVote() {
        int totalPlayers = getMapTeams().getJoinedPlayers().size();
        CodTdmConfig config = CodTdmConfig.getConfig();

        int requiredVotes = Math.max(1, (totalPlayers * config.getVotePercentageToEnd()) / 100);
        if (endVotes.size() >= requiredVotes) {
            getMapTeams().getJoinedPlayers().forEach(pd -> pd.getPlayer().ifPresent(
                    p -> p.sendSystemMessage(Component.translatable("message.codpattern.game.vote_passed_end"))));
            transitionToPhase(GamePhase.ENDED);
            return true;
        }

        return false;
    }

    /**
     * 获取投票状态信息
     */
    public String getVoteStatus() {
        int totalPlayers = getMapTeams().getJoinedPlayers().size();
        CodTdmConfig config = CodTdmConfig.getConfig();

        if (phase == GamePhase.WAITING) {
            int requiredVotes = Math.max(1, (totalPlayers * config.getVotePercentageToStart()) / 100);
            return Component.translatable("message.codpattern.game.status_vote_start", startVotes.size(), requiredVotes)
                    .getString();
        } else if (phase == GamePhase.PLAYING || phase == GamePhase.WARMUP) {
            int requiredVotes = Math.max(1, (totalPlayers * config.getVotePercentageToEnd()) / 100);
            return Component.translatable("message.codpattern.game.status_vote_end", endVotes.size(), requiredVotes)
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
