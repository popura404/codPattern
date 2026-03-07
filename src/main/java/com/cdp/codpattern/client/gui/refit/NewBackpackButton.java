package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.AddBackpackPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

/**
 * 新建背包按钮 - COD MWII 2022 风格
 */
public class NewBackpackButton extends Button {
    private static final long REVEAL_MS = 170L;
    private static final long IDLE_PULSE_MS = 1400L;

    private int focusedTimes = 0;
    private int hoverTicks = 0;
    private final int currentBackpackCount;
    private final long createdAtMs = System.currentTimeMillis();

    // cod2022 风格绿色主题
    private static final int ACTIVE_BG_TOP = 0xE8183018;
    private static final int ACTIVE_BG_BOTTOM = 0xF0102810;
    private static final int ACTIVE_HOVER_TOP = 0xE8204020;
    private static final int ACTIVE_HOVER_BOTTOM = 0xF0183818;

    public NewBackpackButton(int x, int y, int width, int height, int currentCount) {
        super(x, y, width, height,
                Component.translatable("screen.codpattern.backpack.add_new"),
                button -> {
                    ModNetworkChannel.sendToServer(new AddBackpackPacket());
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.playNotifySound(
                                    SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                                    SoundSource.PLAYERS, 0.5f, 1.2f);
                        }
                    });
                },
                DEFAULT_NARRATION);

        this.currentBackpackCount = currentCount;

        if (currentCount >= 10) {
            this.setTooltip(Tooltip.create(Component.translatable("screen.codpattern.backpack.limit_reached")));
            this.active = false;
        } else {
            this.setTooltip(
                    Tooltip.create(Component.translatable("screen.codpattern.backpack.add_hint", currentCount)));
        }
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float pPartialTick) {
        float revealFactor = revealProgress();
        // 悬停音效处理
        if (this.isHoveredOrFocused() && focusedTimes == 0 && this.active) {
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
        if (this.isHoveredOrFocused()) {
            hoverTicks = Math.min(6, hoverTicks + 1);
        } else {
            hoverTicks = Math.max(0, hoverTicks - 1);
        }

        renderCardShadow(graphics, revealFactor);

        // 根据是否可用渲染不同颜色
        if (this.active) {
            // 可用状态 - cod2022绿色调
            graphics.fillGradient(this.getX(), this.getY(),
                    this.getX() + this.width, this.getY() + this.height,
                    scaleAlpha(ACTIVE_BG_TOP, revealFactor),
                    scaleAlpha(ACTIVE_BG_BOTTOM, revealFactor));
        } else {
            // 禁用状态 - 灰色
            graphics.fillGradient(this.getX(), this.getY(),
                    this.getX() + this.width, this.getY() + this.height,
                    scaleAlpha(CodTheme.DISABLED_BG, revealFactor),
                    scaleAlpha(0xE0252525, revealFactor));
        }
        renderCardFrame(graphics, revealFactor);

        // 渲染左侧阴影
        graphics.fill(this.getX() - 3, this.getY(),
                this.getX(), this.getY() + this.height,
                scaleAlpha(CodTheme.SHADOW, revealFactor));
        // 渲染底部阴影
        graphics.fillGradient(this.getX(), this.getY() + this.height,
                this.getX() + this.width, this.getY() + this.height + 2,
                scaleAlpha(CodTheme.SHADOW, revealFactor),
                scaleAlpha(0x40000000, revealFactor));

        // 悬停效果
        if (isHoveredOrFocused() && this.active) {
            renderOnHoveredOrFocused(graphics, revealFactor);
        }

        // 渲染按钮文字
        renderButtonText(graphics, revealFactor);
    }

    private void renderCardShadow(GuiGraphics graphics, float revealFactor) {
        graphics.fill(this.getX() + 2, this.getY() + 2,
                this.getX() + this.width + 2, this.getY() + this.height + 2,
                scaleAlpha(0x38000000, revealFactor));
    }

    private void renderCardFrame(GuiGraphics graphics, float revealFactor) {
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1,
                scaleAlpha(0x14FFFFFF, revealFactor));
        graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height,
                scaleAlpha(0x10FFFFFF, revealFactor));
        float pulse = this.active ? 0.62f + (0.38f * oscillate(IDLE_PULSE_MS)) : 1.0f;
        graphics.fill(this.getX() + 4, this.getY() + 4,
                this.getX() + Math.max(5, this.width / 4), this.getY() + 5,
                scaleAlpha(this.active ? CodTheme.HOVER_BORDER_SEMI : 0x20FFFFFF, revealFactor * pulse));
    }

    protected void renderButtonText(GuiGraphics graphics, float revealFactor) {
        Minecraft minecraft = Minecraft.getInstance();
        String text = this.active ? Component.translatable("screen.codpattern.backpack.add_new").getString()
                : Component.translatable("screen.codpattern.backpack.limit_reached").getString();
        int textColor = this.active ? (this.isHoveredOrFocused() ? CodTheme.HOVER_BORDER : CodTheme.TEXT_PRIMARY)
                : CodTheme.DISABLED_TEXT;

        String countText = "已用 " + currentBackpackCount + "/10";
        int referenceLineHeight = GuiTextHelper.referenceLineHeight(minecraft.font);
        boolean showCount = this.height >= referenceLineHeight * 2 + 8;
        int totalTextHeight = showCount ? referenceLineHeight * 2 + 2 : referenceLineHeight;
        int baseY = this.getY() + (this.height - totalTextHeight) / 2;
        int textCenterX = this.getX() + this.width / 2;
        int textMaxWidth = this.width - 12;

        GuiTextHelper.drawReferenceCenteredEllipsizedString(
                graphics,
                minecraft.font,
                text,
                textCenterX,
                baseY,
                textMaxWidth,
                scaleAlpha(textColor, revealFactor),
                true
        );

        if (showCount) {
            GuiTextHelper.drawReferenceCenteredEllipsizedString(
                    graphics,
                    minecraft.font,
                    countText,
                    textCenterX,
                    baseY + referenceLineHeight + 2,
                    textMaxWidth,
                    scaleAlpha(CodTheme.TEXT_SECONDARY, revealFactor),
                    true
            );
        }
    }

    protected void renderOnHoveredOrFocused(GuiGraphics graphics, float revealFactor) {
        float hoverFactor = hoverTicks / 6.0f;
        // 悬停背景
        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                scaleAlpha(ACTIVE_HOVER_TOP, revealFactor * (0.55f + (0.2f * hoverFactor))),
                scaleAlpha(ACTIVE_HOVER_BOTTOM, revealFactor * (0.65f + (0.2f * hoverFactor))));

        // 顶部荧光绿边框
        graphics.fill(this.getX(), this.getY() - 1,
                this.getX() + this.width, this.getY(),
                scaleAlpha(CodTheme.HOVER_BORDER, revealFactor));

        // 底部荧光绿边框
        graphics.fill(this.getX(), this.getY() + this.height,
                this.getX() + this.width, this.getY() + this.height + 1,
                scaleAlpha(CodTheme.HOVER_BORDER_SEMI, revealFactor));
        graphics.fill(this.getX() + 1, this.getY() + 1,
                this.getX() + this.width - 1, this.getY() + this.height - 1,
                scaleAlpha(withAlpha(CodTheme.HOVER_BORDER, (int) (18.0f + (30.0f * hoverFactor))), revealFactor));
    }

    @Override
    public void playDownSound(@NotNull SoundManager pHandler) {
        if (this.active) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playNotifySound(
                            SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5f, 1.5f);
                }
            });
        }
    }

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private int scaleAlpha(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int scaled = Math.max(0, Math.min(255, (int) (alpha * Math.max(0.0f, Math.min(1.0f, factor)))));
        return (scaled << 24) | (color & 0x00FFFFFF);
    }

    private float revealProgress() {
        long elapsed = System.currentTimeMillis() - createdAtMs;
        float raw = Math.min(1.0f, Math.max(0.0f, elapsed / (float) REVEAL_MS));
        return 0.25f + (raw * 0.75f);
    }

    private float oscillate(long durationMs) {
        if (durationMs <= 0L) {
            return 1.0f;
        }
        double phase = ((System.currentTimeMillis() - createdAtMs) % durationMs) / (double) durationMs;
        return (float) ((Math.sin(phase * Math.PI * 2.0d) + 1.0d) * 0.5d);
    }
}
