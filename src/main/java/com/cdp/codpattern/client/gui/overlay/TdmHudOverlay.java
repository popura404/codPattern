package com.cdp.codpattern.client.gui.overlay;

import com.cdp.codpattern.client.ClientTdmState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class TdmHudOverlay implements IGuiOverlay {

    public static final TdmHudOverlay INSTANCE = new TdmHudOverlay();

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        // 只在TDM模式且游戏进行中或倒计时/热身/结束时显示
        // 这里简化判断，只要 ClientTdmState 有有效阶段就显示
        // 实际应该检查玩家是否在一场TDM游戏中，但这需要在客户端知道自己是否在游戏中
        // 可以简单判断分数是否非0或者阶段是否为特殊值

        if (ClientTdmState.currentPhase.equals("WAITING") && ClientTdmState.gameTimeTicks == 0
                && ClientTdmState.team1Score == 0) {
            // 可能是未加入游戏，暂不显示
            return;
        }

        Font font = Minecraft.getInstance().font;
        int centerX = screenWidth / 2;
        int topY = 10;

        // ========== 渲染黑屏效果（三阶段渐变） ==========
        if (ClientTdmState.isBlackoutActive()) {
            float alpha = ClientTdmState.getBlackoutAlpha();
            int alphaInt = (int) (alpha * 255);
            if (alphaInt > 0) {
                // 限制 alpha 在有效范围内
                alphaInt = Math.min(255, Math.max(0, alphaInt));
                int color = (alphaInt << 24); // ARGB: alpha通道 + 纯黑
                guiGraphics.fill(0, 0, screenWidth, screenHeight, color);

                // 黑屏足够明显时显示提示文字
                if (alpha > 0.5f) {
                    // 根据当前阶段显示不同文字
                    if (ClientTdmState.blackoutPhase == ClientTdmState.BlackoutPhase.FADE_OUT) {
                        // 渐出阶段显示 "准备战斗!"
                        String text = "§e准备战斗!";
                        drawCenteredString(guiGraphics, font, text, centerX, screenHeight / 2, 0xFFFFFF);
                    } else {
                        // 渐入/保持阶段显示 "传送中..."
                        String text = "§e传送中...";
                        drawCenteredString(guiGraphics, font, text, centerX, screenHeight / 2, 0xFFFFFF);
                    }
                }
            }
        }

        // 1. 显示比分
        String scoreText = "Red: " + ClientTdmState.team1Score + "  vs  Blue: " + ClientTdmState.team2Score;
        int scoreColor = 0xFFFFFF;
        drawCenteredString(guiGraphics, font, scoreText, centerX, topY, scoreColor);

        // 2. 显示倒计时/时间
        String timeText = formatTime(ClientTdmState.remainingTimeTicks);
        if (ClientTdmState.currentPhase.equals("PLAYING")) {
            // 游戏中显示剩余时间
            drawCenteredString(guiGraphics, font, timeText, centerX, topY + 12, 0xFFFF00);
        } else if (ClientTdmState.currentPhase.equals("COUNTDOWN")) {
            drawCenteredString(guiGraphics, font, "Starting in: " + timeText, centerX, topY + 12, 0x00FF00);
        } else if (ClientTdmState.currentPhase.equals("WARMUP")) {
            drawCenteredString(guiGraphics, font, "Warmup: " + timeText, centerX, topY + 12, 0xFFA500);
        } else if (ClientTdmState.currentPhase.equals("ENDED")) {
            drawCenteredString(guiGraphics, font, "GAME OVER", centerX, topY + 12, 0xFF0000);
        }

        // 3. 死亡视角提示
        if (ClientTdmState.isDead) {
            drawCenteredString(guiGraphics, font, "YOU DIED", centerX, screenHeight / 2 - 20, 0xFF0000);
            drawCenteredString(guiGraphics, font, "Killed by: " + ClientTdmState.killerName, centerX, screenHeight / 2,
                    0xFFFFFF);
            drawCenteredString(guiGraphics, font,
                    "Respawn in: " + String.format("%.1f", ClientTdmState.deathCamTicks / 20.0), centerX,
                    screenHeight / 2 + 20, 0xAAAAAA);
        }
    }

    private void drawCenteredString(GuiGraphics guiGraphics, Font font, String text, int x, int y, int color) {
        guiGraphics.drawString(font, text, x - font.width(text) / 2, y, color, true);
    }

    private String formatTime(int ticks) {
        int seconds = ticks / 20;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
