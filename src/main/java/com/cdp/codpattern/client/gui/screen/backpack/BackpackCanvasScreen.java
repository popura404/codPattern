package com.cdp.codpattern.client.gui.screen.backpack;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** A full-window canvas with the same equipment and type scale as the 1080p / GUI-2 design. */
public abstract class BackpackCanvasScreen extends Screen {
    private static final int REFERENCE_WIDTH = 960;
    private static final int REFERENCE_HEIGHT = 540;
    protected int canvasWidth;
    protected int canvasHeight;

    protected BackpackCanvasScreen(Component title) {
        super(title);
    }

    record Viewport(int width, int height, double zoom, double guiScale, double mouseUnitX, double mouseUnitY) {
        static Viewport current() {
            Window window = Minecraft.getInstance().getWindow();
            int w = window.getWidth(), h = window.getHeight();
            double zoom = Math.min(w / (double) REFERENCE_WIDTH, h / (double) REFERENCE_HEIGHT);
            // Projection uses exact framebuffer/guiScale; mouse dispatch uses rounded GUI dimensions.
            return new Viewport(Mth.ceil(w / zoom), Mth.ceil(h / zoom), zoom, window.getGuiScale(),
                    w / (double) window.getGuiScaledWidth(), h / (double) window.getGuiScaledHeight());
        }

        float scale() {
            return (float) (zoom / guiScale);
        }

        double canvasX(double mouseX) {
            return mouseX * mouseUnitX / zoom;
        }

        double canvasY(double mouseY) {
            return mouseY * mouseUnitY / zoom;
        }

        double pointerX(Minecraft mc) {
            Window window = mc.getWindow();
            return canvasX(mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
        }

        double pointerY(Minecraft mc) {
            Window window = mc.getWindow();
            return canvasY(mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
        }
    }

    @Override
    protected void init() {
        super.init();
        Viewport view = Viewport.current();
        canvasWidth = view.width;
        canvasHeight = view.height;
    }

    private boolean inside(double x, double y) {
        return x >= 0 && x < canvasWidth && y >= 0 && y < canvasHeight;
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
        return canvasX * view.zoom / view.mouseUnitX;
    }

    public final double screenY(double canvasY) {
        Viewport view = Viewport.current();
        return canvasY * view.zoom / view.mouseUnitY;
    }

    @Override
    public final ScreenRectangle getRectangle() {
        return new ScreenRectangle(0, 0, canvasWidth, canvasHeight);
    }

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Viewport view = Viewport.current();
        double x = view.pointerX(minecraft), y = view.pointerY(minecraft);
        boolean hovered = inside(x, y);
        var pose = g.pose();
        pose.pushPose();
        try {
            float scale = view.scale();
            // Project positions only, preserving normals and depth for the equipment model.
            pose.last().pose().scale(scale, scale, 1f);
            BackpackUi.background(g, canvasWidth, canvasHeight);
            renderCanvas(g, hovered ? Mth.floor(x) : -1, hovered ? Mth.floor(y) : -1, partialTick);
        } finally {
            g.flush();
            pose.popPose();
        }
    }

    protected abstract void renderCanvas(GuiGraphics g, int x, int y, float partialTick);

    protected boolean isTransitioning() {
        return false;
    }

    protected final void renderCanvasWidgets(GuiGraphics g, int x, int y, float partialTick) {
        super.render(g, x, y, partialTick);
    }

    protected final void renderCanvasClipped(GuiGraphics g, int left, int top, int right, int bottom,
                                             Runnable draw) {
        // GuiGraphics scissors use the native GUI scale and ignore the canvas pose.
        double zoom = Viewport.current().zoom();
        int x = (int) Math.round(left * zoom);
        int y = (int) Math.round(top * zoom);
        int rightPixel = (int) Math.round(right * zoom);
        int bottomPixel = (int) Math.round(bottom * zoom);
        g.flush();
        RenderSystem.enableScissor(x, minecraft.getWindow().getHeight() - bottomPixel, rightPixel - x, bottomPixel - y);
        try {
            draw.run();
        } finally {
            g.flush();
            RenderSystem.disableScissor();
        }
    }

    @Override
    public final void mouseMoved(double x, double y) {
        if (isTransitioning()) return;
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
        if (isTransitioning()) return true;
        Viewport view = Viewport.current();
        double canvasX = view.canvasX(x), canvasY = view.canvasY(y);
        return inside(canvasX, canvasY) && canvasMouseClicked(canvasX, canvasY, button);
    }

    protected boolean canvasMouseClicked(double x, double y, int button) {
        return super.mouseClicked(x, y, button);
    }

    @Override
    public final boolean mouseReleased(double x, double y, int button) {
        if (isTransitioning()) {
            setDragging(false);
            return true;
        }
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
        if (isTransitioning()) return true;
        Viewport view = Viewport.current();
        double canvasX = view.canvasX(x), canvasY = view.canvasY(y);
        return inside(canvasX, canvasY) && canvasMouseScrolled(canvasX, canvasY, delta);
    }

    protected boolean canvasMouseScrolled(double x, double y, double delta) {
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (isTransitioning()) return true;
        Viewport view = Viewport.current();
        return canvasMouseDragged(view.canvasX(x), view.canvasY(y), button,
                dx * view.mouseUnitX / view.zoom, dy * view.mouseUnitY / view.zoom);
    }

    protected boolean canvasMouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x, y, button, dx, dy);
    }
}
