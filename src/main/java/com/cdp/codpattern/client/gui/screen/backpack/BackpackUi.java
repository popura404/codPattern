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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Shared typography, row surfaces and equipment stage for the loadout screens. */
public final class BackpackUi {
    public static final int LOADOUT_MARGIN = 35;
    public static final int LOADOUT_COLUMN_GAP = 12;
    public static final int LOADOUT_CARD_GAP = 3;
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
    private static final ResourceLocation PRIMARY_CATALOG_INLAY =
            new ResourceLocation("codpattern", "textures/gui/backpack/primary_catalog_inlay.png");
    private static final ResourceLocation MISC_SLOT_INLAYS =
            new ResourceLocation("codpattern", "textures/gui/backpack/misc_catalog_inlays.png");
    private static final int MISC_INLAY_WIDTH = 260;
    private static final int MISC_INLAY_HEIGHT = 54;
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
        blitInlay(g, SCULK_INLAY, left, top, artWidth, artHeight, u, v, mirror ? -sampleWidth : sampleWidth,
                sampleHeight, 3 * INLAY_CELL_WIDTH, 2 * INLAY_CELL_HEIGHT, opacity);
    }

    private static void blitInlay(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h,
                                  int u, int v, int sampleW, int sampleH, int textureW, int textureH, float opacity) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, opacity);
        g.blit(texture, x, y, w, h, (float) u, (float) v, sampleW, sampleH, textureW, textureH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void slotInlay(GuiGraphics g, int x, int y, int w, int h, BackpackSlot slot, int variation,
                                  float opacity, boolean catalog) {
        if (!slot.isGunSlot()) {
            // Center the narrow strip on loadout tiles so short brush marks keep their proportions.
            int artHeight = Math.min(h, Math.max(1, Math.round(w * MISC_INLAY_HEIGHT / (float) MISC_INLAY_WIDTH)));
            int top = y + (h - artHeight) / 2;
            int v = slot == BackpackSlot.MELEE ? 0 : MISC_INLAY_HEIGHT;
            blitInlay(g, MISC_SLOT_INLAYS, x, top, w, artHeight, 0, v, MISC_INLAY_WIDTH, MISC_INLAY_HEIGHT,
                    MISC_INLAY_WIDTH, 2 * MISC_INLAY_HEIGHT, opacity);
        } else if (catalog && slot == BackpackSlot.PRIMARY) {
            blitInlay(g, PRIMARY_CATALOG_INLAY, x, y, w, h, 0, 0, 1, 1, 1, 1, opacity);
        } else {
            inlay(g, x, y, w, h, slot.ordinal(), variation, opacity);
        }
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
        drawRow(g, x, y, w, h, slot, variation, highlighted, false);
    }

    public static void candidateRow(GuiGraphics g, int x, int y, int w, int h, BackpackSlot slot, int index,
                                    boolean highlighted) {
        drawRow(g, x, y, w, h, slot, index, highlighted, true);
    }

    private static void drawRow(GuiGraphics g, int x, int y, int w, int h, BackpackSlot slot, int variation,
                                boolean highlighted, boolean catalog) {
        horizontalGradient(g, x, y, w, h, highlighted ? 0xEA1C3235 : 0xD4172225,
                highlighted ? 0xEF324E50 : 0xDD202D30);
        slotInlay(g, x, y, w, h, slot, variation, highlighted ? .65f : .55f, catalog);
        if (highlighted) {
            int length = Math.min(px(20), Math.max(px(6), h / 3));
            int top = y + (h - length) / 2;
            g.fill(x, y, x + w, y + 1, 0x4C82ACA8);
            g.fill(x, top, x + px(2), top + length, ACCENT);
        }
    }

    public static void readOnlySlot(GuiGraphics g, int x, int y, int w, int h, BackpackSlot slot) {
        horizontalGradient(g, x, y, w, h, 0x242D393A, 0x35435454);
        slotInlay(g, x, y, w, h, slot, 0, .41f, false);
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

    /** Allow equal whole-pixel margins on both sides of a centered bitmap. */
    public static int centeredExtent(int target, int outer) {
        return target % 2 == outer % 2 ? target : target - 1;
    }

    public static int loadoutColumnWidth(int canvasWidth) {
        int columns = canvasWidth - 2 * (LOADOUT_MARGIN + LOADOUT_COLUMN_GAP);
        int gearColumnWidth = Math.round(columns * 1.05f / 3.81f);
        // Match the gap's parity so the two equipment cards fill the row at equal widths.
        return centeredExtent(Math.round(gearColumnWidth * .88f), LOADOUT_CARD_GAP);
    }

    public static int loadoutCardWidth(int columnWidth, BackpackSlot slot) {
        return slot.isEquipmentSlot() ? (columnWidth - LOADOUT_CARD_GAP) / 2 : columnWidth;
    }

    public static int loadoutCardHeight(BackpackSlot slot) {
        return switch (slot) {
            case PRIMARY, SECONDARY -> 90;
            case MELEE -> 71;
            case ITEM1, ITEM2 -> 48;
        };
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
        drawCaption(g, captionCategory(slot, stack), stack, x, y, w);
    }

    static int captionTextWidth(BackpackSlot slot, ItemStack stack, int width) {
        var caption = captionText(captionCategory(slot, stack), stack, width);
        return Mth.ceil(Math.max(caption.category.width, Math.max(caption.name.width, caption.origin.width)));
    }

    private static Component captionCategory(BackpackSlot slot, ItemStack stack) {
        Component category = BackpackEquipmentRenderer.category(stack);
        return category.getString().isBlank() ? slotLabel(slot) : category;
    }

    private record CaptionText(CaptionLine category, CaptionLine name, CaptionLine origin, float nameScale) {}

    private record CaptionLine(FormattedCharSequence glyphs, float width, float glyphScale) {
        void render(GuiGraphics g, int right, int y, int color) {
            GuiTextHelper.drawScaledString(g, Minecraft.getInstance().font, glyphs, right - width, y, glyphScale, color, false);
        }
    }

    private static CaptionText captionText(Component category, ItemStack stack, int width) {
        var name = BackpackEquipmentRenderer.displayName(stack).copy().withStyle(ChatFormatting.BOLD);
        float nameScale = Math.min(weaponNameScale(), Math.max(1.25f, width / (float) Math.max(1, textWidth(name, 1f))));
        return new CaptionText(captionLine(category, width, 1f), captionLine(name, width, nameScale),
                captionLine(BackpackEquipmentRenderer.origin(stack), width, 1f), nameScale);
    }

    private static void drawCaption(GuiGraphics g, Component category, ItemStack stack, int x, int y, int w) {
        var caption = captionText(category, stack, w);
        int right = x + w;
        int nameBottom = y + lineHeight(1f) + px(6) + lineHeight(weaponNameScale());
        caption.category.render(g, right, y, 0xFFA9C8C8);
        caption.name.render(g, right, nameBottom - lineHeight(caption.nameScale), INK);
        caption.origin.render(g, right, nameBottom + px(3), MUTED);
    }

    private static CaptionLine captionLine(Component label, int width, float scale) {
        var font = Minecraft.getInstance().font;
        float glyphScale = BODY_SCALE * scale;
        var fitted = GuiTextHelper.ellipsizeFormatted(font, label, (int) (width / glyphScale));
        return new CaptionLine(fitted, font.width(fitted) * glyphScale, glyphScale);
    }

    public static void keycap(GuiGraphics g, Component key, int x, int y, int w, int h, int ink, int border) {
        g.fill(x, y, x + w, y + h, 0xE0202B2D);
        g.renderOutline(x, y, w, h, border);
        int labelWidth = textWidth(key, 1f);
        text(g, key, x + (w - labelWidth) / 2, y + (h - lineHeight(1f)) / 2, labelWidth, 1f, ink);
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
