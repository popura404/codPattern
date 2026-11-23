package com.cdp.codpattern.client.gui.screen.backpack;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** A centered 1080p / GUI-2 canvas above the player's native Minecraft UI. */
public abstract class BackpackCanvasScreen extends Screen {
    protected static final int CANVAS_WIDTH = 960;
    protected static final int CANVAS_HEIGHT = 540;
    private static final ScreenRectangle CANVAS = new ScreenRectangle(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

    protected BackpackCanvasScreen(Component title) {
        super(title);
    }

    private record Viewport(double zoom, double left, double top, double guiScale,
                            double mouseUnitX, double mouseUnitY, int framebufferHeight) {
        static Viewport current() {
            Window window = Minecraft.getInstance().getWindow();
            int w = window.getWidth(), h = window.getHeight();
            double zoom = Math.min(w / (double) CANVAS_WIDTH, h / (double) CANVAS_HEIGHT);
            // Projection uses exact framebuffer/guiScale; mouse dispatch uses rounded GUI dimensions.
            return new Viewport(zoom, (w - CANVAS_WIDTH * zoom) / 2, (h - CANVAS_HEIGHT * zoom) / 2,
                    window.getGuiScale(), w / (double) window.getGuiScaledWidth(),
                    h / (double) window.getGuiScaledHeight(), h);
        }

        double canvasX(double mouseX) {
            return (mouseX * mouseUnitX - left) / zoom;
        }

        double canvasY(double mouseY) {
            return (mouseY * mouseUnitY - top) / zoom;
        }

        double pointerX(Minecraft mc) {
            Window window = mc.getWindow();
            return canvasX(mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
        }

        double pointerY(Minecraft mc) {
            Window window = mc.getWindow();
            return canvasY(mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
        }

        void scissor(GuiGraphics g, int x0, int y0, int x1, int y1) {
            int x = (int) Math.round(left + Mth.clamp(x0, 0, CANVAS_WIDTH) * zoom);
            int y = (int) Math.round(top + Mth.clamp(y0, 0, CANVAS_HEIGHT) * zoom);
            int right = (int) Math.round(left + Mth.clamp(x1, 0, CANVAS_WIDTH) * zoom);
            int bottom = (int) Math.round(top + Mth.clamp(y1, 0, CANVAS_HEIGHT) * zoom);
            g.flush();
            RenderSystem.enableScissor(x, framebufferHeight - bottom, right - x, bottom - y);
        }
    }

    private static boolean inside(double x, double y) {
        return x >= 0 && x < CANVAS_WIDTH && y >= 0 && y < CANVAS_HEIGHT;
    }

    protected final double pointerCanvasX() {
        return Viewport.current().pointerX(minecraft);
    }

    protected final double pointerCanvasY() {
        return Viewport.current().pointerY(minecraft);
    }

    /** Inverse of MouseHandler's native GUI coordinates, including its GUI-size rounding. */
    public final double screenX(double canvasX) {
        Viewport view = Viewport.current();
        return (canvasX * view.zoom + view.left) / view.mouseUnitX;
    }

    public final double screenY(double canvasY) {
        Viewport view = Viewport.current();
        return (canvasY * view.zoom + view.top) / view.mouseUnitY;
    }

    @Override
    public final ScreenRectangle getRectangle() {
        return CANVAS;
    }

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Viewport view = Viewport.current();
        Window window = minecraft.getWindow();
        double x = view.pointerX(minecraft), y = view.pointerY(minecraft);
        boolean hovered = inside(x, y);
        var pose = g.pose();
        pose.pushPose();
        try {
            float scale = (float) (view.zoom / view.guiScale);
            // Project positions only, preserving normals and depth for the equipment model.
            pose.last().pose().scale(scale, scale, 1f);
            // The full-bleed wash and corner share the canvas pixel scale, including in the gutters.
            BackpackUi.background(g, Mth.ceil(window.getWidth() / view.zoom), Mth.ceil(window.getHeight() / view.zoom));
            view.scissor(g, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
            pose.translate(view.left / view.zoom, view.top / view.zoom, 0);
            renderCanvas(g, hovered ? Mth.floor(x) : -1, hovered ? Mth.floor(y) : -1, partialTick);
        } finally {
            g.flush();
            pose.popPose();
            RenderSystem.disableScissor();
        }
    }

    protected abstract void renderCanvas(GuiGraphics g, int x, int y, float partialTick);

    protected final void renderCanvasWidgets(GuiGraphics g, int x, int y, float partialTick) {
        super.render(g, x, y, partialTick);
    }

    // GuiGraphics.enableScissor ignores its PoseStack in 1.20.1. The nested model clip and the
    // enclosing canvas clip both use physical pixels so they agree at every native GUI scale.
    static void clipModel(GuiGraphics g, int x, int y, int width, int height) {
        Viewport.current().scissor(g, x, y, x + width, y + height);
    }

    static void restoreCanvasClip(GuiGraphics g) {
        Viewport.current().scissor(g, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    @Override
    public final void mouseMoved(double x, double y) {
        Viewport view = Viewport.current();
        double canvasX = view.canvasX(x), canvasY = view.canvasY(y);
        if (inside(canvasX, canvasY)) canvasMouseMoved(canvasX, canvasY);
        else canvasMouseMoved(-1, -1);
    }

    protected void canvasMouseMoved(double x, double y) {
        super.mouseMoved(x, y);
    }

    @Override
    public final boolean mouseClicked(double x, double y, int button) {
        Viewport view = Viewport.current();
        double canvasX = view.canvasX(x), canvasY = view.canvasY(y);
        return inside(canvasX, canvasY) && canvasMouseClicked(canvasX, canvasY, button);
    }

    protected boolean canvasMouseClicked(double x, double y, int button) {
        return super.mouseClicked(x, y, button);
    }

    @Override
    public final boolean mouseReleased(double x, double y, int button) {
        Viewport view = Viewport.current();
        double canvasX = view.canvasX(x), canvasY = view.canvasY(y);
        return inside(canvasX, canvasY) ? canvasMouseReleased(canvasX, canvasY, button)
                : canvasMouseReleased(-1, -1, button);
    }

    protected boolean canvasMouseReleased(double x, double y, int button) {
        return super.mouseReleased(x, y, button);
    }

    @Override
    public final boolean mouseScrolled(double x, double y, double delta) {
        Viewport view = Viewport.current();
        double canvasX = view.canvasX(x), canvasY = view.canvasY(y);
        return inside(canvasX, canvasY) && canvasMouseScrolled(canvasX, canvasY, delta);
    }

    protected boolean canvasMouseScrolled(double x, double y, double delta) {
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        Viewport view = Viewport.current();
        return canvasMouseDragged(view.canvasX(x), view.canvasY(y), button,
                dx * view.mouseUnitX / view.zoom, dy * view.mouseUnitY / view.zoom);
    }

    protected boolean canvasMouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x, y, button, dx, dy);
    }
}
