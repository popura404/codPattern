package com.cdp.codpattern.client.gui.screen.match;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

abstract class LobbyButton extends Button {
    private ScreenRectangle bounds;

    protected LobbyButton(ScreenRectangle bounds, Component label, OnPress onPress) {
        super(LobbyCanvas.toGui(bounds.left()), LobbyCanvas.toGui(bounds.top()),
                LobbyCanvas.toGui(bounds.right()) - LobbyCanvas.toGui(bounds.left()),
                LobbyCanvas.toGui(bounds.bottom()) - LobbyCanvas.toGui(bounds.top()),
                label, onPress, DEFAULT_NARRATION);
        this.bounds = bounds;
    }

    protected static ScreenRectangle fromGui(int x, int y, int width, int height) {
        int left = referenceEdge(x);
        int top = referenceEdge(y);
        return new ScreenRectangle(left, top, referenceEdge(x + width) - left, referenceEdge(y + height) - top);
    }

    private static int referenceEdge(int guiEdge) {
        return (int) Math.round(LobbyCanvas.toReference(guiEdge));
    }

    public void setLobbyY(int referenceY) {
        bounds = new ScreenRectangle(bounds.left(), referenceY, bounds.width(), bounds.height());
        super.setY(LobbyCanvas.toGui(referenceY));
        height = LobbyCanvas.toGui(bounds.bottom()) - getY();
    }

    @Override
    public void setY(int y) {
        setLobbyY(referenceEdge(y));
    }

    @Override
    public void setX(int x) {
        bounds = new ScreenRectangle(referenceEdge(x), bounds.top(), bounds.width(), bounds.height());
        super.setX(LobbyCanvas.toGui(bounds.left()));
        super.setWidth(LobbyCanvas.toGui(bounds.right()) - getX());
    }

    @Override
    public void setWidth(int width) {
        bounds = new ScreenRectangle(bounds.left(), bounds.top(), referenceEdge(getX() + width) - bounds.left(), bounds.height());
        super.setWidth(LobbyCanvas.toGui(bounds.right()) - getX());
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        double x = LobbyCanvas.toReference(mouseX);
        double y = LobbyCanvas.toReference(mouseY);
        return active && visible && x >= bounds.left() && x < bounds.right() && y >= bounds.top() && y < bounds.bottom();
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        isHovered = isMouseOver(mouseX, mouseY);
        LobbyCanvas.begin(graphics);
        drawButton(graphics, bounds);
        graphics.pose().popPose();
    }

    protected abstract void drawButton(GuiGraphics graphics, ScreenRectangle bounds);
}
