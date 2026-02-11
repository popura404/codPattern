package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.refit.AttachmentRefitClientState;
import com.cdp.codpattern.network.RequestAttachmentPresetPacket;
import com.cdp.codpattern.network.handler.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

/**
 * 配件配置按钮
 */
public class AttachmentConfigButton extends Button {

    private final int bagId;
    private final String slot;
    private int focusedTimes = 0;

    public AttachmentConfigButton(int x, int y, int width, int height, int bagId, String slot) {
        super(x, y, width, height, Component.literal("attachment config"),
                button -> {
                    AttachmentRefitClientState.setParentScreen(Minecraft.getInstance().screen);
                    PacketHandler.sendToServer(new RequestAttachmentPresetPacket(bagId, slot));
                },
                DEFAULT_NARRATION);
        this.bagId = bagId;
        this.slot = slot;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.isHoveredOrFocused() && focusedTimes == 0) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playNotifySound(
                            SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                            SoundSource.PLAYERS, 0.5f, 1.2f);
                }
            });
            focusedTimes = 1;
        } else if (!this.isHoveredOrFocused()) {
            focusedTimes = 0;
        }

        // 基础背景 - MWII 风格
        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                CodTheme.CARD_BG_TOP, CodTheme.CARD_BG_BOTTOM);

        if (isHoveredOrFocused()) {
            renderOnHoveredOrFocused(graphics);
        }
        renderTitle(graphics);
    }

    protected void renderOnHoveredOrFocused(GuiGraphics graphics) {
        // 悬停背景
        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                CodTheme.HOVER_BG_TOP, CodTheme.HOVER_BG_BOTTOM);
        // 金色边框
        renderGoldBorder(graphics);
    }

    protected void renderTitle(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        String idText = "更换配件";
        int textX = this.getX() + 6;
        int textY = this.getY() + (this.height - minecraft.font.lineHeight) / 2;

        int textColor = this.isHoveredOrFocused() ? CodTheme.SELECTED_TEXT : CodTheme.TEXT_PRIMARY;
        graphics.drawString(minecraft.font, idText, textX, textY, textColor, true);
    }

    private void renderGoldBorder(GuiGraphics graphics) {
        int borderWidth = CodTheme.BORDER_WIDTH;
        int color = CodTheme.SELECTED_BORDER;
        // 上
        graphics.fill(this.getX() - borderWidth, this.getY() - borderWidth,
                this.getX() + this.width + borderWidth, this.getY(), color);
        // 下
        graphics.fill(this.getX() - borderWidth, this.getY() + this.height,
                this.getX() + this.width + borderWidth, this.getY() + this.height + borderWidth, color);
        // 左
        graphics.fill(this.getX() - borderWidth, this.getY(),
                this.getX(), this.getY() + this.height, color);
        // 右
        graphics.fill(this.getX() + this.width, this.getY(),
                this.getX() + this.width + borderWidth, this.getY() + this.height, color);
    }

    public int getBagId() {
        return bagId;
    }

    public String getSlot() {
        return slot;
    }
}
