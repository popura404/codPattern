package com.cdp.codpattern.config.TdmConfig;

import com.cdp.codpattern.core.ConfigPath.ConfigPath;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * COD Team Deathmatch 配置文件
 * 存储于 serverconfig/codpattern/tdmconfig/config.json
 */
public class CodTdmConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(CodTdmConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CodTdmConfig INSTANCE = new CodTdmConfig();

    // 游戏配置参数
    private int timeLimitSeconds = 420; // 7分钟
    private int scoreLimit = 75; // 75击杀获胜
    private int invincibilityTicks = 10; // 0.5秒无敌
    private int respawnDelayTicks = 40; // 2秒复活延迟
    private int warmupTimeTicks = 400; // 20秒热身
    private int preGameCountdownTicks = 200; // 10秒倒计时
    private int blackoutStartTicks = 60; // 最后3秒黑屏
    private int deathCamTicks = 30; // 1.5秒死亡视角
    private int minPlayersToStart = 1; // 最少1人开始(测试用途)
    private int votePercentageToStart = 60; // 60%投票开始
    private int votePercentageToEnd = 75; // 75%投票结束
    private boolean allowJoinDuringPlaying = true; // 对局进行中是否允许加入
    private boolean joinAsSpectatorWhenPlaying = true; // 对局中加入是否旁观
    private int maxTeamDiff = 1; // 自动分队允许的最大人数差

    // Getters
    public static CodTdmConfig getConfig() {
        return INSTANCE;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public int getScoreLimit() {
        return scoreLimit;
    }

    public int getInvincibilityTicks() {
        return invincibilityTicks;
    }

    public int getRespawnDelayTicks() {
        return respawnDelayTicks;
    }

    public int getWarmupTimeTicks() {
        return warmupTimeTicks;
    }

    public int getPreGameCountdownTicks() {
        return preGameCountdownTicks;
    }

    public int getBlackoutStartTicks() {
        return blackoutStartTicks;
    }

    public int getDeathCamTicks() {
        return deathCamTicks;
    }

    public int getMinPlayersToStart() {
        return minPlayersToStart;
    }

    public int getVotePercentageToStart() {
        return votePercentageToStart;
    }

    public int getVotePercentageToEnd() {
        return votePercentageToEnd;
    }

    public boolean isAllowJoinDuringPlaying() {
        return allowJoinDuringPlaying;
    }

    public boolean isJoinAsSpectatorWhenPlaying() {
        return joinAsSpectatorWhenPlaying;
    }

    public int getMaxTeamDiff() {
        return maxTeamDiff;
    }

    // Setters
    public void setTimeLimitSeconds(int value) {
        this.timeLimitSeconds = value;
    }

    public void setScoreLimit(int value) {
        this.scoreLimit = value;
    }

    public void setInvincibilityTicks(int value) {
        this.invincibilityTicks = value;
    }

    public void setRespawnDelayTicks(int value) {
        this.respawnDelayTicks = value;
    }

    public void setWarmupTimeTicks(int value) {
        this.warmupTimeTicks = value;
    }

    public void setPreGameCountdownTicks(int value) {
        this.preGameCountdownTicks = value;
    }

    public void setBlackoutStartTicks(int value) {
        this.blackoutStartTicks = value;
    }

    public void setDeathCamTicks(int value) {
        this.deathCamTicks = value;
    }

    public void setMinPlayersToStart(int value) {
        this.minPlayersToStart = value;
    }

    public void setVotePercentageToStart(int value) {
        this.votePercentageToStart = value;
    }

    public void setVotePercentageToEnd(int value) {
        this.votePercentageToEnd = value;
    }

    public void setAllowJoinDuringPlaying(boolean value) {
        this.allowJoinDuringPlaying = value;
    }

    public void setJoinAsSpectatorWhenPlaying(boolean value) {
        this.joinAsSpectatorWhenPlaying = value;
    }

    public void setMaxTeamDiff(int value) {
        this.maxTeamDiff = value;
    }

    /**
     * 加载配置文件
     */
    public static void load(MinecraftServer server) {
        try {
            Path configDir = ConfigPath.SERVER_TDM_CONFIG.getPath(server);
            Path configFile = configDir.resolve("config.json");

            // 创建目录
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            // 读取配置
            if (Files.exists(configFile)) {
                String json = Files.readString(configFile);
                INSTANCE = GSON.fromJson(json, CodTdmConfig.class);
                LOGGER.info("TDM配置已加载: {}", configFile);
            } else {
                // 首次创建默认配置
                save(server);
                LOGGER.info("TDM配置文件不存在，已创建默认配置: {}", configFile);
            }
        } catch (IOException e) {
            LOGGER.error("加载TDM配置失败", e);
        }
    }

    /**
     * 保存配置文件
     */
    public static void save(MinecraftServer server) {
        try {
            Path configDir = ConfigPath.SERVER_TDM_CONFIG.getPath(server);
            Path configFile = configDir.resolve("config.json");

            // 创建目录
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            // 写入配置
            String json = GSON.toJson(INSTANCE);
            Files.writeString(configFile, json);
            LOGGER.info("TDM配置已保存: {}", configFile);
        } catch (IOException e) {
            LOGGER.error("保存TDM配置失败", e);
        }
    }
}
