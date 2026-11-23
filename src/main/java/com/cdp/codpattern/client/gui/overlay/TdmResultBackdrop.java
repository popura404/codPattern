package com.cdp.codpattern.client.gui.overlay;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

final class TdmResultBackdrop {
    private static final ResourceLocation STUDIO = new ResourceLocation("codpattern", "textures/gui/backpack/studio_wash.png");
    private static final ResourceLocation RIM = new ResourceLocation("codpattern", "textures/gui/settlement/rim.png");
    private static final ResourceLocation SCULK = new ResourceLocation("codpattern", "dynamic/settlement_sculk");
    private static TdmResultBackdrop cached;

    private final TdmSculkField.Geometry geometry;
    private final TdmSculkField.Tile[] tiles;
    private final DynamicTexture texture;
    private int lastStep = -1;

    private TdmResultBackdrop(TdmSculkField.Geometry geometry) {
        this.geometry = geometry;
        tiles = TdmSculkField.build(geometry);
        texture = new DynamicTexture(geometry.columns(), geometry.rows(), true);
        // One manager-owned slot. Replacing it closes the previous NativeImage and GL texture.
        Minecraft.getInstance().getTextureManager().register(SCULK, texture);
    }

    static void draw(GuiGraphics g, int w, int h, float washOpacity, float growth, String outcome,
                     int titleX, int titleY, int titleWidth, int titleHeight) {
        if (w <= 0 || h <= 0 || washOpacity <= 0f) return;
        float alpha = Math.min(washOpacity, 1f);
        TdmSculkField.Outcome theme = TdmSculkField.Outcome.from(outcome);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, alpha * .955f);
        g.blit(STUDIO, 0, 0, w, h, 0f, 0f, 192, 108, 192, 108);
        int rim = theme.rimColor;
        RenderSystem.setShaderColor((rim >> 16 & 255) / 255f, (rim >> 8 & 255) / 255f, (rim & 255) / 255f, alpha);
        g.blit(RIM, 0, 0, w, h, 0f, 0f, 256, 144, 256, 144);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        if (growth < 0 || titleWidth <= 0 || titleHeight <= 0) return;

        // A texel stays at least two display pixels across on narrow windows.
        // A larger viewport preserves the preview's approximately 290 x 160 working grid.
        double displayScale = Minecraft.getInstance().getWindow().getWidth() / (double) w;
        double unit = Math.max(2.1 / Math.max(displayScale, .01), Math.min(w / 290.0, h / 160.0));
        unit = Math.max(unit, Math.max(w, h) / 768.0);
        int cols = Math.max(1, (int) Math.round(w / unit));
        int rows = Math.max(1, (int) Math.round(h / unit));
        var next = new TdmSculkField.Geometry(w, h, cols, rows, titleX, titleY, titleWidth, titleHeight, theme);
        if (cached == null || !cached.geometry.equals(next) || cached.texture.getPixels() == null) {
            cached = new TdmResultBackdrop(next);
        }
        cached.update(Math.round(600 * Mth.clamp(growth, 0f, 1f)));
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        g.blit(SCULK, 0, 0, w, h, 0f, 0f, cols, rows, cols, rows);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private void update(int step) {
        if (step == lastStep) return;
        NativeImage pixels = texture.getPixels();
        if (pixels == null) return;
        double progress = step / 600.0;
        for (TdmSculkField.Tile tile : tiles) {
            int alpha = tile.alphaAt(progress);
            if (tile.previousAlpha == alpha) continue;
            pixels.setPixelRGBA(tile.x, tile.y, tile.rgba(alpha));
            tile.previousAlpha = alpha;
        }
        texture.upload();
        lastStep = step;
    }
}
