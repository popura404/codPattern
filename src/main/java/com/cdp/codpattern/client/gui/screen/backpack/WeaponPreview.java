package com.cdp.codpattern.client.gui.screen.backpack;

import com.cdp.codpattern.client.gui.BackNavigation;
import com.cdp.codpattern.compat.tacz.client.RefitPreviewCamera;
import com.cdp.codpattern.config.backpack.BackpackSlot;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterClientCache;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.locale.Language;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;

/** Shared weapon model stage and caption in the backpack canvas coordinate space. */
public final class WeaponPreview {
    private final ScreenRectangle model;
    private final int captionX, captionY, captionWidth;

    private WeaponPreview(ScreenRectangle model, int captionX, int captionY, int captionWidth) {
        this.model = model;
        this.captionX = captionX;
        this.captionY = captionY;
        this.captionWidth = captionWidth;
    }

    public void render(GuiGraphics g, BackpackSlot slot, ItemStack stack) {
        drawModel(g, stack, RefitPreviewCamera.Pose.OVERVIEW);
        drawCaption(g, slot, stack);
    }

    private void drawModel(GuiGraphics g, ItemStack stack, RefitPreviewCamera.Pose camera) {
        BackpackEquipmentRenderer.renderModel(g, stack, model.left(), model.top(), model.width(), model.height(), camera);
    }

    private void drawCaption(GuiGraphics g, BackpackSlot slot, ItemStack stack) {
        BackpackUi.caption(g, slot, stack, captionX, captionY, captionWidth);
    }

    public static NativeView nativeView(BackpackSlot slot, NativeView previous) {
        var view = BackpackCanvasScreen.Viewport.current();
        var filter = WeaponFilterClientCache.get();
        var language = Language.getInstance();
        // TaCZ rebuilds widgets on each install ACK and category change. Those do not move the preview.
        if (previous != null && previous.slot == slot && previous.viewport.equals(view)
                && previous.filter == filter && previous.language == language) return previous;
        int categoryCount = WeaponCatalog.load(slot, filter).size();
        var header = WeaponCatalog.header(view.width(), view.height(), categoryCount, filter);
        return new NativeView(view, layout(view.width(), view.height(), header), slot, filter, language);
    }

    /** The refit screen keeps native input coordinates and uses this cached canvas for presentation. */
    public static final class NativeView {
        private final BackpackCanvasScreen.Viewport viewport;
        private final WeaponPreview preview;
        private final BackpackSlot slot;
        private final WeaponFilterConfig filter;
        private final Language language;

        private NativeView(BackpackCanvasScreen.Viewport viewport, WeaponPreview preview, BackpackSlot slot,
                           WeaponFilterConfig filter, Language language) {
            this.viewport = viewport;
            this.preview = preview;
            this.slot = slot;
            this.filter = filter;
            this.language = language;
        }

        public void render(GuiGraphics g, ItemStack stack, RefitPreviewCamera.Pose camera) {
            var pose = g.pose();
            pose.pushPose();
            try {
                pose.last().pose().scale(viewport.scale(), viewport.scale(), 1f);
                BackpackUi.background(g, viewport.width(), viewport.height());
                if (stack.isEmpty()) return;
                preview.drawModel(g, stack, camera);
                // The close-up uses the full framebuffer. Start the caption and controls with fresh depth.
                RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                preview.drawCaption(g, slot, stack);
            } finally {
                g.flush();
                pose.popPose();
            }
        }

        public ScreenRectangle captionBounds(ItemStack stack) {
            int width = BackpackUi.captionTextWidth(slot, stack, preview.captionWidth);
            var caption = new ScreenRectangle(preview.captionX + preview.captionWidth - width, preview.captionY,
                    width, BackpackUi.captionHeight());
            return toNative(caption, viewport.scale());
        }

        public ScreenRectangle modelBounds() {
            return toNative(preview.model, viewport.scale());
        }
    }

    private static ScreenRectangle toNative(ScreenRectangle rect, float scale) {
        int left = Mth.floor(rect.left() * scale), top = Mth.floor(rect.top() * scale);
        return new ScreenRectangle(left, top, Mth.ceil(rect.right() * scale) - left, Mth.ceil(rect.bottom() * scale) - top);
    }

    public static WeaponPreview layout(int width, int height, WeaponCatalog.Header header) {
        int margin = header.margin();
        int x = margin + BackpackUi.loadoutColumnWidth(width) + Math.max(8, Math.round(width * .0125f));
        int stageWidth = width - x - margin;
        int stageTop = header.headingY();
        int footer = BackNavigation.bounds(width, height).top();
        int stageHeight = footer - stageTop;
        int captionY = footer - BackpackUi.captionHeight();
        int modelInset = Math.max(2, Math.round(stageWidth * .016f));
        int modelY = stageTop + Math.round(stageHeight * .04f);
        int modelBottom = captionY - Math.max(6, Math.round(stageHeight * .045f));
        var model = new ScreenRectangle(x + modelInset, modelY, stageWidth - 2 * modelInset, modelBottom - modelY);
        return new WeaponPreview(model, x, captionY, stageWidth);
    }
}
