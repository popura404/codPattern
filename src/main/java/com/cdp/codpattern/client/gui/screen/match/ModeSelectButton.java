package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public class ModeSelectButton extends LobbyButton {
    private static final String PREVIEW_ARROW = "›";
    private static final Component IN_ROOM = Component.translatable("screen.codpattern.mode_select.in_room");
    private static final int HOVER_DURATION_MS = 220;
    private static final float HOVER_GRADIENT_SLOPE = (float) Math.tan(Math.toRadians(2));
    public static final int PRESS_DURATION_MS = 75;
    public static final float LABEL_SCALE = 1.5f;

    private final ModeDescriptor descriptor;
    private final int accentColor;
    private boolean joined;
    private boolean engaged;
    private long lastMotionMs = Util.getMillis();
    private long hoveredMillis;
    private long pressStarted = -PRESS_DURATION_MS;
    private float hover, rail, growth, glow;

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
            advanceMotion(Util.getMillis());
            this.engaged = engaged;
        }
    }

    public void setRoomJoined(boolean joined) {
        this.joined = joined;
    }

    public void beginPress() {
        pressStarted = Util.getMillis();
    }

    private void advanceMotion(long now) {
        long elapsed = Math.max(0, now - lastMotionMs);
        lastMotionMs = now;
        if (engaged) hoveredMillis += elapsed;
        float target = engaged ? 1f : 0f;
        hover = Mth.approach(hover, target, elapsed / (float) HOVER_DURATION_MS);
        rail = Mth.approach(rail, target, elapsed / 260f);
        growth = Mth.approach(growth, target, elapsed / (engaged ? 1450f : 1100f));
        glow = Mth.approach(glow, target, elapsed / 320f);
    }

    private static float ease(float t) {
        return t * t * (3f - 2f * t);
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        MutableComponent message = super.createNarrationMessage();
        return joined ? message.append(" ").append(IN_ROOM) : message;
    }

    @Override
    protected void drawButton(GuiGraphics graphics, ScreenRectangle bounds) {
        long now = Util.getMillis();
        advanceMotion(now);
        float press = 1f - Mth.clamp(((now - pressStarted) / (float) PRESS_DURATION_MS - .58f) / .42f, 0f, 1f);
        float highlight = ease(hover);
        int edge = joined ? Math.min(2, bounds.width() / 2) : 0;
        graphics.pose().pushPose();
        graphics.pose().translate(0f, press, 0f);
        drawSurface(graphics, bounds, edge, highlight);
        LobbyModeEffects.draw(graphics, bounds, accentColor, joined, growth, ease(glow), hoveredMillis);
        int railHeight = Math.max(1, Math.round(bounds.height() * .82f * (.52f + .48f * ease(rail))));
        int railY = bounds.top() + (bounds.height() - railHeight) / 2;
        int railColor = joined ? FastColor.ARGB32.lerp(.32f, 0xFF101918, accentColor) : accentColor;
        graphics.fill(bounds.left() + edge, railY, bounds.left() + edge + 2, railY + railHeight,
                (Math.round(143 + 112 * ease(rail)) << 24) | (railColor & 0xFFFFFF));
        if (press > 0f) {
            int tint = joined ? 0 : 0xFFFFFF;
            graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), Math.round(32f * press) << 24 | tint);
        }
        drawLabels(graphics, bounds, highlight);
        graphics.pose().popPose();
    }

    private void drawSurface(GuiGraphics graphics, ScreenRectangle bounds, int edge, float highlight) {
        int x = bounds.left();
        int y = bounds.top();
        if (joined) {
            graphics.fill(x, y, bounds.right(), bounds.bottom(), 0xFF080A09);
            graphics.fill(x + edge, y, bounds.right() - edge, bounds.bottom(),
                    FastColor.ARGB32.lerp(.07f * highlight, accentColor, 0xFFFFFFFF));
            return;
        }
        // GuiGraphics supplies a vertical gradient. Rotate its local axes to shade the row left to right.
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().mulPose(Axis.ZN.rotationDegrees(90));
        graphics.fillGradient(-bounds.height(), 0, 0, bounds.width(), 0xED101719, 0xCF11181A);
        graphics.pose().popPose();
        if (highlight > 0f && bounds.width() > 0) drawDarkHover(graphics, bounds, highlight);
    }

    private void drawDarkHover(GuiGraphics graphics, ScreenRectangle bounds, float highlight) {
        // The 92-degree overlay reaches its final translucent color at the 75% stop.
        float skew = Math.min(bounds.width() / 3f, bounds.height() * HOVER_GRADIENT_SLOPE);
        float stop = .75f * (bounds.width() + skew);
        int left = bounds.left(), right = bounds.right();
        int top = bounds.top(), bottom = bounds.bottom();
        int accentMix = FastColor.ARGB32.lerp(.22f, 0xFF141F20, accentColor);
        int start = (Math.round(255 * highlight) << 24) | (accentMix & 0xFFFFFF);
        int end = (Math.round(201 * highlight) << 24) | 0x293539;
        int bottomLeft = FastColor.ARGB32.lerp(skew / stop, start, end);

        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer vertices = graphics.bufferSource().getBuffer(RenderType.gui());
        vertices.vertex(pose, left, top, 0).color(start).endVertex();
        vertices.vertex(pose, left, bottom, 0).color(bottomLeft).endVertex();
        vertices.vertex(pose, left + stop - skew, bottom, 0).color(end).endVertex();
        vertices.vertex(pose, left + stop, top, 0).color(end).endVertex();
        vertices.vertex(pose, left + stop, top, 0).color(end).endVertex();
        vertices.vertex(pose, left + stop - skew, bottom, 0).color(end).endVertex();
        vertices.vertex(pose, right, bottom, 0).color(end).endVertex();
        vertices.vertex(pose, right, top, 0).color(end).endVertex();
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
