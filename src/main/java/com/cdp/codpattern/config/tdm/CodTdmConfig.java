package com.cdp.codpattern.config.tdm;

import com.cdp.codpattern.config.path.ConfigPath;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * COD Team Deathmatch 配置文件
 * 存储于 serverconfig/codpattern/tdm_rules/config.json
 */
public class CodTdmConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(CodTdmConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CodTdmConfig INSTANCE = new CodTdmConfig();
    public static final String ENEMY_MARKER_STYLE_HEALTH_BAR = "HEALTH_BAR";
    public static final String ENEMY_MARKER_STYLE_DOT = "MARKER_DOT";

    // 游戏配置参数
    private int timeLimitSeconds = 420; // 7分钟
    private int scoreLimit = 75; // 75击杀获胜
    private int invincibilityTicks = 30; // 1.5秒无敌
    private int respawnDelayTicks = 40; // 2秒复活延迟
    private int warmupTimeTicks = 400; // 20秒热身
    private int preGameCountdownTicks = 200; // 10秒倒计时
    private int blackoutStartTicks = 60; // 最后3秒黑屏
    private int deathCamTicks = 30; // 1.5秒死亡视角
    private int minPlayersToStart = 1; // 最少1人开始(测试用途)
    private int votePercentageToStart = 60; // 60%投票开始
    private int votePercentageToEnd = 75; // 75%投票结束
    private int combatRegenDelayTicks = 120; // 受伤后多久开始回血
    private float combatRegenHalfHeartsPerSecond = 5.0f; // 每秒回复多少半颗心
    private int maxTeamDiff = 1; // 自动分队允许的最大人数差
    private String enemyMarkerStyle = ENEMY_MARKER_STYLE_HEALTH_BAR; // 敌方标识样式：HEALTH_BAR / MARKER_DOT
    private float markerFocusHalfAngleDegrees = 30.0f; // 敌方标识判定半角（度）
    private int markerFocusRequiredTicks = 20; // 视锥内累计显示标识所需 tick
    private double markerBarMaxDistance = 96.0D; // 敌方标识判定最远距离（格）
    private int markerVisibleGraceTicks = 3; // 可见状态下敌方标识防闪烁缓冲 tick

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

    public int getCombatRegenDelayTicks() {
        return combatRegenDelayTicks;
    }

    public float getCombatRegenHalfHeartsPerSecond() {
        return combatRegenHalfHeartsPerSecond;
    }

    public int getMaxTeamDiff() {
        return maxTeamDiff;
    }

    public String getEnemyMarkerStyle() {
        return normalizeEnemyMarkerStyle(enemyMarkerStyle);
    }

    public boolean isEnemyMarkerHealthBar() {
        return ENEMY_MARKER_STYLE_HEALTH_BAR.equals(getEnemyMarkerStyle());
    }

    public float getMarkerFocusHalfAngleDegrees() {
        return markerFocusHalfAngleDegrees;
    }

    public int getMarkerFocusRequiredTicks() {
        return markerFocusRequiredTicks;
    }

    public double getMarkerBarMaxDistance() {
        return markerBarMaxDistance;
    }

    public int getMarkerVisibleGraceTicks() {
        return markerVisibleGraceTicks;
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

    public void setCombatRegenDelayTicks(int value) {
        this.combatRegenDelayTicks = value;
    }

    public void setCombatRegenHalfHeartsPerSecond(float value) {
        this.combatRegenHalfHeartsPerSecond = value;
    }

    public void setMaxTeamDiff(int value) {
        this.maxTeamDiff = value;
    }

    public void setEnemyMarkerStyle(String value) {
        this.enemyMarkerStyle = normalizeEnemyMarkerStyle(value);
    }

    public void setMarkerFocusHalfAngleDegrees(float value) {
        this.markerFocusHalfAngleDegrees = value;
    }

    public void setMarkerFocusRequiredTicks(int value) {
        this.markerFocusRequiredTicks = value;
    }

    public void setMarkerBarMaxDistance(double value) {
        this.markerBarMaxDistance = value;
    }

    public void setMarkerVisibleGraceTicks(int value) {
        this.markerVisibleGraceTicks = value;
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
                CodTdmConfig loaded = GSON.fromJson(json, CodTdmConfig.class);
                INSTANCE = loaded == null ? new CodTdmConfig() : loaded;
                INSTANCE.normalizeAfterLoad();
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
            INSTANCE.normalizeAfterLoad();

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

    private void normalizeAfterLoad() {
        enemyMarkerStyle = normalizeEnemyMarkerStyle(enemyMarkerStyle);
    }

    private static String normalizeEnemyMarkerStyle(String value) {
        if (value == null || value.isBlank()) {
            return ENEMY_MARKER_STYLE_HEALTH_BAR;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (ENEMY_MARKER_STYLE_DOT.equals(normalized)) {
            return ENEMY_MARKER_STYLE_DOT;
        }
        return ENEMY_MARKER_STYLE_HEALTH_BAR;
    }
}
