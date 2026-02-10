package com.cdp.codpattern.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * 客户端TDM游戏状态管理器
 */
public class ClientTdmState {

    // 游戏阶段
    public static String currentPhase = "WAITING";
    public static int remainingTimeTicks = 0;

    // 分数
    public static int team1Score = 0;
    public static int team2Score = 0;
    public static int gameTimeTicks = 0;

    // 倒计时（旧字段保留兼容）
    public static int countdown = 0;
    public static boolean blackout = false;

    // ========== 黑屏渐变系统（三阶段：渐入 → 保持 → 渐出） ==========
    /**
     * 黑屏阶段枚举
     */
    public enum BlackoutPhase {
        NONE, // 无黑屏
        FADE_IN, // 渐入（透明 → 全黑）
        HOLD, // 保持全黑（等待传送完成）
        FADE_OUT // 渐出（全黑 → 透明）
    }

    public static BlackoutPhase blackoutPhase = BlackoutPhase.NONE;
    public static int blackoutTicksRemaining = 0; // 当前阶段剩余tick
    public static int blackoutTotalTicks = 0; // 黑屏总时长tick（渐入+保持的阶段）

    // 渐出专用
    public static int fadeOutTicksRemaining = 0; // 渐出剩余tick
    public static int fadeOutTotalTicks = 0; // 渐出总时长tick

    // 渐入占总黑屏时长的比例
    private static final float FADE_IN_RATIO = 0.4f;
    // 渐出时长 (tick)，独立于渐入时长
    private static final int FADE_OUT_DURATION = 30; // 1.5秒渐出

    // ========== 音效触发标志 ==========
    public static boolean playCountdownTickSound = false; // 倒计时每秒的滴答音效
    public static boolean playTeleportSound = false; // 传送音效
    public static String previousPhase = "WAITING"; // 上一次的阶段，用于检测阶段切换

    // 死亡视角
    public static boolean isDead = false;
    public static String killerName = "";
    public static int deathCamTicks = 0;

    // 更新游戏阶段
    public static void updatePhase(String phase, int time) {
        // 检测阶段切换：进入 WARMUP 或 PLAYING 时触发渐出 + 传送音效
        if (!phase.equals(previousPhase)) {
            if (phase.equals("WARMUP") || phase.equals("PLAYING")) {
                // 传送完成 → 开始渐出
                startFadeOut();
                // 触发传送音效
                playTeleportSound = true;
            }
        }
        previousPhase = currentPhase;
        currentPhase = phase;
        remainingTimeTicks = time;
    }

    // 更新分数
    public static void updateScore(int t1, int t2, int time) {
        team1Score = t1;
        team2Score = t2;
        gameTimeTicks = time;
    }

    // 更新倒计时（收到黑屏数据包时调用）
    public static void updateCountdown(int count, boolean black) {
        countdown = count;
        blackout = black;

        // 当收到 blackout=true 的数据包时，启动黑屏渐入
        if (black && count > 0) {
            blackoutTotalTicks = count;
            blackoutTicksRemaining = count;
            blackoutPhase = BlackoutPhase.FADE_IN;
        }

        // 倒计时音效：收到非黑屏倒计时数据包时播放一次"滴答"
        if (!black && count > 0) {
            playCountdownTickSound = true;
        }
    }

    // 设置死亡视角
    public static void setDeathCam(String killer, int duration) {
        isDead = true;
        killerName = killer;
        deathCamTicks = duration;
    }

    /**
     * 开始渐出阶段
     */
    private static void startFadeOut() {
        blackoutPhase = BlackoutPhase.FADE_OUT;
        fadeOutTotalTicks = FADE_OUT_DURATION;
        fadeOutTicksRemaining = FADE_OUT_DURATION;
        // 清除渐入阶段残留
        blackoutTicksRemaining = 0;
        blackoutTotalTicks = 0;
    }

    /**
     * 获取当前黑屏不透明度 (0.0 = 完全透明, 1.0 = 完全不透明)
     * FADE_IN: 从0渐变到1
     * HOLD: 保持1
     * FADE_OUT: 从1渐变到0
     */
    public static float getBlackoutAlpha() {
        switch (blackoutPhase) {
            case FADE_IN -> {
                if (blackoutTicksRemaining <= 0 || blackoutTotalTicks <= 0) {
                    return 0.0f;
                }
                // 进度：0.0（刚开始黑屏）→ 1.0（黑屏结束）
                float progress = 1.0f - ((float) blackoutTicksRemaining / blackoutTotalTicks);
                // 前40%时间用于淡入
                if (progress < FADE_IN_RATIO) {
                    // 使用 smoothstep 曲线使渐变更平滑
                    float t = progress / FADE_IN_RATIO;
                    return smoothstep(t);
                } else {
                    // 保持全黑
                    return 1.0f;
                }
            }
            case HOLD -> {
                return 1.0f;
            }
            case FADE_OUT -> {
                if (fadeOutTicksRemaining <= 0 || fadeOutTotalTicks <= 0) {
                    return 0.0f;
                }
                // 进度：1.0（全黑）→ 0.0（完全透明）
                float t = (float) fadeOutTicksRemaining / fadeOutTotalTicks;
                // smoothstep 使渐出更平滑
                return smoothstep(t);
            }
            default -> {
                return 0.0f;
            }
        }
    }

    /**
     * smoothstep 插值函数，使渐变更丝滑
     * 输入 t: 0.0 → 1.0
     * 输出: 0.0 → 1.0（带平滑曲线）
     */
    private static float smoothstep(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * 黑屏是否正在生效
     */
    public static boolean isBlackoutActive() {
        return blackoutPhase != BlackoutPhase.NONE;
    }

    // 每一Tick更新（减少倒计时等）
    public static void clientTick() {
        if (remainingTimeTicks > 0)
            remainingTimeTicks--;
        if (deathCamTicks > 0) {
            deathCamTicks--;
            if (deathCamTicks == 0)
                isDead = false;
        }

        // ========== 黑屏阶段 tick ==========
        switch (blackoutPhase) {
            case FADE_IN -> {
                if (blackoutTicksRemaining > 0) {
                    blackoutTicksRemaining--;
                } else {
                    // 渐入+保持阶段结束 → 进入 HOLD 等待服务端切换阶段
                    blackoutPhase = BlackoutPhase.HOLD;
                }
            }
            case HOLD -> {
                // 保持全黑，等待 updatePhase 检测到 WARMUP/PLAYING 触发渐出
                // 安全超时：如果 HOLD 超过 5 秒强制渐出（防止卡死）
            }
            case FADE_OUT -> {
                if (fadeOutTicksRemaining > 0) {
                    fadeOutTicksRemaining--;
                } else {
                    // 渐出完成
                    blackoutPhase = BlackoutPhase.NONE;
                    fadeOutTotalTicks = 0;
                }
            }
            default -> {
            }
        }

        // ========== 音效播放 ==========
        playPendingSounds();

        // ========== 倒计时每秒音效 ==========
        // 在 COUNTDOWN 阶段，每秒（20tick）播放一次倒计时声音
        if ("COUNTDOWN".equals(currentPhase) && remainingTimeTicks > 0 && remainingTimeTicks % 20 == 0) {
            playCountdownTickSound = true;
        }
    }

    /**
     * 播放待播放的音效
     */
    private static void playPendingSounds() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;

        // 倒计时滴答音效
        if (playCountdownTickSound) {
            playCountdownTickSound = false;
            int secondsLeft = remainingTimeTicks / 20;
            if (secondsLeft <= 5 && secondsLeft > 0) {
                // 最后5秒用更响亮、更高音调的音效
                float pitch = 1.0f + (5 - secondsLeft) * 0.15f; // 音调越来越高
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, pitch);
            } else {
                // 普通倒计时"嘀"声
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.get(), SoundSource.PLAYERS, 0.5f, 1.2f);
            }
        }

        // 传送音效
        if (playTeleportSound) {
            playTeleportSound = false;
            player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.0f);
        }
    }
}
