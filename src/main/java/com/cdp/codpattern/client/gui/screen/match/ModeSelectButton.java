package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public class ModeSelectButton extends LobbyButton {
    private static final String PREVIEW_ARROW = "›";
    private static final Component IN_ROOM = Component.translatable("screen.codpattern.mode_select.in_room");
    public static final int PRESS_DURATION_MS = 75;
    public static final float LABEL_SCALE = 1.5f;

    private final ModeDescriptor descriptor;
    private final int accentColor;
    private boolean joined;
    private boolean engaged;
    private final LobbyModeEffects.Motion motion = new LobbyModeEffects.Motion();
    private long pressStarted = -PRESS_DURATION_MS;

    public ModeSelectButton(
            int x,
            int y,
            int width,
            int height,
            ModeDescriptor descriptor,
            int accentColor,
            OnPress onPress) {
        this(fromGui(x, y, width, height), descriptor, accentColor, onPress);
    }

    public ModeSelectButton(ScreenRectangle bounds, ModeDescriptor descriptor, int accentColor, OnPress onPress) {
        super(bounds, Component.translatable(descriptor.displayNameKey()), onPress);
        this.descriptor = descriptor;
        this.accentColor = 0xFF000000 | accentColor;
    }

    public ModeDescriptor descriptor() {
        return descriptor;
    }

    public void setPreviewing(boolean previewing) {
        setEngaged(previewing);
    }

    public void setEngaged(boolean engaged) {
        if (this.engaged != engaged) {
            motion.update(engaged, Util.getMillis());
            this.engaged = engaged;
        }
    }

    public void setRoomJoined(boolean joined) {
        this.joined = joined;
    }

    public void beginPress() {
        pressStarted = Util.getMillis();
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        MutableComponent message = super.createNarrationMessage();
        return joined ? message.append(" ").append(IN_ROOM) : message;
    }

    @Override
    protected void drawButton(GuiGraphics graphics, ScreenRectangle bounds) {
        long now = Util.getMillis();
        motion.update(engaged, now);
        float press = 1f - Mth.clamp(((now - pressStarted) / (float) PRESS_DURATION_MS - .58f) / .42f, 0f, 1f);
        graphics.pose().pushPose();
        graphics.pose().translate(0f, press, 0f);
        LobbyModeEffects.drawFace(graphics, bounds, accentColor, joined, motion, 1f, 2);
        if (press > 0f) {
            int tint = joined ? 0 : 0xFFFFFF;
            graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), Math.round(32f * press) << 24 | tint);
        }
        drawLabels(graphics, bounds, motion.highlight());
        graphics.pose().popPose();
    }

    private void drawLabels(GuiGraphics graphics, ScreenRectangle bounds, float highlight) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        boolean keyboard = isFocused() && minecraft.getLastInputType().isKeyboard();
        int padding = bounds.width() < 125 ? 5 : 10;
        int arrowX = bounds.right() - padding - font.width(PREVIEW_ARROW);
        float labelX = bounds.left() + padding + 2f * highlight;
        int available = Math.max(0, arrowX - bounds.left() - padding - 6);
        float scale = Mth.clamp((float) available / Math.max(1, font.width(getMessage())), 1f, LABEL_SCALE);
        float titleHeight = font.lineHeight * scale;
        float labelY = bounds.top() + (bounds.height() - titleHeight - (joined ? font.lineHeight + 2 : 0)) / 2f;
        int ink = joined ? 0xFF111414 : FastColor.ARGB32.lerp(highlight, 0xFFE0E6E3, 0xFFFFFFFF);
        GuiTextHelper.drawScaledString(graphics, font, GuiTextHelper.ellipsizeFormatted(font, getMessage(), (int) (available / scale)),
                labelX, labelY, scale, ink, !joined);
        if (joined) {
            graphics.drawString(font, GuiTextHelper.ellipsizeFormatted(font, IN_ROOM, available),
                    bounds.left() + padding, Math.round(labelY + titleHeight) + 2, 0xFF25302D, false);
        }
        int alpha = keyboard ? 255 : Math.round(255 * highlight);
        if (alpha >= 4) { // Minecraft's font treats alpha values below four as opaque.
            int arrowY = bounds.top() + (bounds.height() - font.lineHeight) / 2;
            int color = joined ? 0xFF111414 : accentColor;
            if (keyboard) {
                graphics.fill(arrowX - 2, arrowY - 1, arrowX + font.width(PREVIEW_ARROW) + 2,
                        arrowY + font.lineHeight + 1, joined ? 0xFF161C1B : 0xFFF4F7F5);
                color = joined ? 0xFFFFFFFF : 0xFF121717;
            }
            graphics.drawString(font, PREVIEW_ARROW, arrowX - Math.round(2 * (1 - highlight)), arrowY,
                    (alpha << 24) | (color & 0xFFFFFF), false);
        }
    }
}
