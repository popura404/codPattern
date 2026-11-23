package com.cdp.codpattern.client.gui.screen.backpack;

import com.cdp.codpattern.app.tdm.model.TdmClientModePresentations;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.client.gui.screen.match.LobbyModeEffects;
import com.cdp.codpattern.config.backpack.BackpackSlot;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Shared typography, row surfaces and equipment stage for the loadout screens. */
public final class BackpackUi {
    public static final int INK = 0xFFF1F3EE;
    public static final int MUTED = 0xFFB3BEB8;
    public static final int ACCENT = TdmClientModePresentations.TEAM_DEATHMATCH_ACCENT_COLOR;
    public static final int JOINED_MINT = TdmClientModePresentations.FRONTLINE_ACCENT_COLOR;
    public static final int JOINED_INK = 0xFF111414;
    public static final int JOINED_MUTED = 0xFF25302D;
    public static final int PRIMARY_INK = 0xFF111B19;
    private static final ResourceLocation STUDIO_WASH =
            new ResourceLocation("codpattern", "textures/gui/backpack/studio_wash.png");
    private static final ResourceLocation SCULK_CORNER =
            new ResourceLocation("codpattern", "textures/gui/backpack/sculk_corner.png");
    private static final ResourceLocation SCULK_INLAY =
            new ResourceLocation("codpattern", "textures/gui/backpack/sculk_inlay.png");
    // The first five 256 x 128 atlas cells follow BackpackSlot's loadout order; the sixth is neutral.
    private static final int INLAY_CELL_WIDTH = 256;
    private static final int INLAY_CELL_HEIGHT = 128;
    private static final int NEUTRAL_CELL = 5;
    private static final int LINE = 0xFF3B4946;

    private BackpackUi() {}

    public static Component slotLabel(BackpackSlot slot) {
        return Component.translatable("screen.codpattern.loadout.slot." + slot.key());
    }

    public static void background(GuiGraphics g, int width, int height) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, .9f);
        g.blit(STUDIO_WASH, 0, 0, width, height, 0f, 0f, 192, 108, 192, 108);
        // The baked corner lives in the lower-left quarter; sample it right-to-left at the screen edge.
        int cornerWidth = Math.round(width * .31f * .5f);
        int cornerHeight = cornerWidth / 2;
        // Add the corner highlight without obscuring a second share of the world behind it.
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        g.blit(SCULK_CORNER, width - cornerWidth, height - cornerHeight, cornerWidth, cornerHeight,
                256f, 128f, -256, 128, 512, 256);
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** The mode/room list owns the artwork; the bag widget owns its animation clock. */
    public static void bagFace(GuiGraphics g, Button b, int bagId, boolean joined, boolean engaged) {
        PaintedButton bag = (PaintedButton) b;
        if (bag.motion == null) bag.motion = new LobbyModeEffects.Motion();
        bag.motion.update(engaged, Util.getMillis());
        LobbyModeEffects.drawBagFace(g, b.getRectangle(), joined ? JOINED_MINT : ACCENT,
                joined, bag.motion, px(2));
        buttonInlay(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), bagId - 1,
                joined ? .48f : engaged ? .65f : .54f);
    }

    public static void buttonInlay(GuiGraphics g, int x, int y, int w, int h, int index, float opacity) {
        inlay(g, x, y, w, h, 3 + index % 3, index / 3, opacity);
    }

    private static void inlay(GuiGraphics g, int x, int y, int w, int h, int cell, int variation, float opacity) {
        int variant = variation % 4;
        boolean mirror = (variant & 1) != 0;
        int artHeight = Math.round((h - 2) * (variant >= 2 ? .88f : 1f));
        int artWidth = Math.min(w - 2, Math.round(artHeight * 2.8f));
        if (artWidth < 1 || artHeight < 1) return;

        // Keep left-side melee and fiber artwork on the left even in a wide card.
        float anchor = switch (cell) {
            case 2, 4 -> .08f;
            case 1, 3 -> .88f;
            default -> .50f;
        };
        int left = x + 1 + Math.round((w - 2 - artWidth) * (mirror ? 1f - anchor : anchor));
        int top = y + 1 + (h - 2 - artHeight) / 2;
        // Stay within one atlas cell under bilinear filtering. Adjacent candidates also vary crop and handedness.
        int crop = 2 + variant * 5;
        int sampleWidth = INLAY_CELL_WIDTH - 2 * crop;
        int sampleHeight = INLAY_CELL_HEIGHT - 2 * crop;
        int u = cell % 3 * INLAY_CELL_WIDTH + crop + (mirror ? sampleWidth : 0);
        int v = cell / 3 * INLAY_CELL_HEIGHT + crop;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, opacity);
        g.blit(SCULK_INLAY, left, top, artWidth, artHeight, u, v, mirror ? -sampleWidth : sampleWidth,
                sampleHeight, 3 * INLAY_CELL_WIDTH, 2 * INLAY_CELL_HEIGHT);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static void horizontalGradient(GuiGraphics g, int x, int y, int w, int h, int left, int right) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.mulPose(Axis.ZN.rotationDegrees(90));
        g.fillGradient(-h, 0, 0, w, left, right);
        pose.popPose();
    }

    public static void row(GuiGraphics g, int x, int y, int w, int h, BackpackSlot slot, int variation,
                           boolean highlighted) {
        horizontalGradient(g, x, y, w, h, highlighted ? 0xEA1C3235 : 0xD4172225,
                highlighted ? 0xEF324E50 : 0xDD202D30);
        inlay(g, x, y, w, h, slot.ordinal(), variation, highlighted ? .65f : .55f);
        if (highlighted) {
            int length = Math.min(px(20), Math.max(px(6), h / 3));
            int top = y + (h - length) / 2;
            g.fill(x, y, x + w, y + 1, 0x4C82ACA8);
            g.fill(x, top, x + px(2), top + length, ACCENT);
        }
    }

    public static void readOnlySlot(GuiGraphics g, int x, int y, int w, int h, BackpackSlot slot) {
        horizontalGradient(g, x, y, w, h, 0x242D393A, 0x35435454);
        inlay(g, x, y, w, h, slot.ordinal(), 0, .41f);
        g.fill(x, y + h - 1, x + w, y + h, 0x247E918E);
    }

    public static void actionFace(GuiGraphics g, int x, int y, int w, int h, boolean primary, boolean highlighted) {
        horizontalGradient(g, x, y, w, h, primary ? 0xFF73A5A2 : 0xCA233034,
                primary ? 0xFF7DB2AF : 0xCA2C3B3E);
        inlay(g, x, y, w, h, NEUTRAL_CELL, 0, highlighted ? .65f : .50f);
        if (highlighted) {
            g.fill(x, y, x + w, y + h, 0x20FFFFFF);
            g.renderOutline(x, y, w, h, 0x9FBECFCD);
        }
        if (!primary) g.fill(x, y + h - 1, x + w, y + h, 0x5A6F9798);
    }

    // 1920x1080 at GUI scale 2 uses an 18-pixel body glyph and a 2.5-em title.
    private static final float BODY_SCALE = 18f / 16f;
    private static final float TITLE_SCALE = 2.5f;

    public static int px(int referenceSize) {
        return referenceSize;
    }

    public static int textWidth(Component label, float scale) {
        return Mth.ceil(Minecraft.getInstance().font.width(label) * (BODY_SCALE * scale));
    }

    public static int lineHeight(float scale) {
        return Math.max(1, Math.round(Minecraft.getInstance().font.lineHeight * (BODY_SCALE * scale)));
    }

    public static int titleHeight() {
        return lineHeight(TITLE_SCALE);
    }

    private static float weaponNameScale() {
        return TITLE_SCALE * 1.30f;
    }

    public static int captionHeight() {
        return lineHeight(weaponNameScale()) + lineHeight(1f) * 2 + px(6) + px(3);
    }

    public static void text(GuiGraphics g, Component label, int x, int y, int width, float scale, int color) {
        if (width <= 0) return;
        var font = Minecraft.getInstance().font;
        float glyphScale = BODY_SCALE * scale;
        var fitted = GuiTextHelper.ellipsizeFormatted(font, label, Math.max(1, (int) (width / glyphScale)));
        GuiTextHelper.drawScaledString(g, font, fitted, x, y, glyphScale, color, false);
    }

    public static void title(GuiGraphics g, int x, int y, int width, Component label) {
        text(g, label, x, y, width, TITLE_SCALE, INK);
    }

    public static void heading(GuiGraphics g, Component label, int x, int y, int width) {
        text(g, label, x, y, width, 1f, MUTED);
        int ruleY = y + lineHeight(1f) + px(4);
        g.fill(x, ruleY, x + width, ruleY + 1, LINE);
    }

    public static void loadoutCaption(GuiGraphics g, BackpackSlot slot, ItemStack stack, int x, int y, int w) {
        var label = slotLabel(slot).copy();
        var category = BackpackEquipmentRenderer.category(stack);
        if (!category.getString().isBlank()) label.append("  /  ").append(category);
        drawCaption(g, label, stack, x, y, w);
    }

    public static void caption(GuiGraphics g, BackpackSlot slot, ItemStack stack, int x, int y, int w) {
        Component category = BackpackEquipmentRenderer.category(stack);
        if (category.getString().isBlank()) category = slotLabel(slot);
        drawCaption(g, category, stack, x, y, w);
    }

    private static void drawCaption(GuiGraphics g, Component category, ItemStack stack, int x, int y, int w) {
        var name = BackpackEquipmentRenderer.displayName(stack).copy().withStyle(ChatFormatting.BOLD);
        float nameScale = Math.min(weaponNameScale(), Math.max(1.25f, w / (float) Math.max(1, textWidth(name, 1f))));
        int right = x + w;
        int nameBottom = y + lineHeight(1f) + px(6) + lineHeight(weaponNameScale());
        rightText(g, category, right, y, w, 1f, 0xFFA9C8C8);
        rightText(g, name, right, nameBottom - lineHeight(nameScale), w, nameScale, INK);
        rightText(g, BackpackEquipmentRenderer.origin(stack), right, nameBottom + px(3), w, 1f, MUTED);
    }

    private static void rightText(GuiGraphics g, Component label, int right, int y, int width, float scale, int color) {
        var font = Minecraft.getInstance().font;
        float glyphScale = BODY_SCALE * scale;
        var fitted = GuiTextHelper.ellipsizeFormatted(font, label, (int) (width / glyphScale));
        GuiTextHelper.drawScaledString(g, font, fitted, right - font.width(fitted) * glyphScale,
                y, glyphScale, color, false);
    }

    public static void keycap(GuiGraphics g, Component key, int x, int y, int w, int h, int ink, int border) {
        g.fill(x, y, x + w, y + h, 0xE0202B2D);
        g.renderOutline(x, y, w, h, border);
        int labelWidth = textWidth(key, 1f);
        text(g, key, x + (w - labelWidth) / 2, y + (h - lineHeight(1f)) / 2, labelWidth, 1f, ink);
    }

    public static Button backButton(int x, int y, Runnable action) {
        var esc = Component.literal("ESC");
        var label = Component.translatable("screen.codpattern.common.back");
        int keyWidth = textWidth(esc, 1f) + 2 * px(4);
        int gap = px(5);
        int height = lineHeight(1f) + 2 * px(4);
        int width = keyWidth + gap + textWidth(label, 1f) + 1;
        return button(x, y, width, height, label, action, (g, b) -> {
            int left = b.getX(), top = b.getY();
            boolean highlighted = b.isHovered() || (b.isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard());
            int color = highlighted ? INK : 0xFFC1C9C7;
            keycap(g, esc, left, top, keyWidth, height, color, highlighted ? ACCENT : 0xFF788B89);
            int labelX = left + keyWidth + gap;
            text(g, label, labelX, top + (height - lineHeight(1f)) / 2, width - keyWidth - gap, 1f, color);
        });
    }

    @FunctionalInterface
    public interface Painter {
        void draw(GuiGraphics graphics, Button button);
    }

    public static Button button(int x, int y, int w, int h, Component label, Runnable action, Painter painter) {
        return new PaintedButton(x, y, w, h, label, action, painter);
    }

    private static final class PaintedButton extends Button {
        private final Painter painter;
        private LobbyModeEffects.Motion motion;

        PaintedButton(int x, int y, int w, int h, Component label, Runnable action, Painter painter) {
            super(x, y, w, h, label, b -> action.run(), DEFAULT_NARRATION);
            this.painter = painter;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
            painter.draw(graphics, this);
        }
    }
}
