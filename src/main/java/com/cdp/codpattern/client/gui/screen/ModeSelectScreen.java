package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.client.gui.screen.match.LobbyBackButton;
import com.cdp.codpattern.client.gui.screen.match.LobbyCanvas;
import com.cdp.codpattern.client.gui.screen.match.ModeLobbyCaption;
import com.cdp.codpattern.client.gui.screen.match.ModePreviewPanel;
import com.cdp.codpattern.client.gui.screen.match.ModeSelectButton;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ModeSelectScreen extends Screen {
    private static final int CROSS_FADE_MS = 220;
    private static final Component HEADER = Component.translatable("screen.codpattern.mode_select.header");

    private static final int ROW_HEIGHT = 38;
    private static final int ROW_GAP = 2;
    private static final int SCROLL_HIT_WIDTH = 7;

    private final Screen previousScreen;
    private final List<ModeSelectButton> rows = new ArrayList<>();
    private final List<ModeLobbyCaption> captions = new ArrayList<>();

    private int canvasWidth, canvasHeight;
    private int menuX, menuWidth, headerY, listY;
    private int captionRight, captionBottom;
    private int firstVisible, visibleCount;
    private int previewIndex, previousIndex = -1;
    private long transitionStart;
    private boolean checkInitialHover;

    public ModeSelectScreen(Screen previousScreen) {
        super(Component.translatable("screen.codpattern.mode_select.title"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        rows.clear();
        captions.clear();
        List<ModeDescriptor> modes = GameModeRegistry.orderedModes();
        canvasWidth = GuiTextHelper.referenceViewportSize(minecraft.getWindow().getWidth());
        canvasHeight = GuiTextHelper.referenceViewportSize(minecraft.getWindow().getHeight());
        boolean compact = canvasWidth <= 350 || canvasWidth < canvasHeight;
        int pageInset = Math.round(canvasWidth * 0.04f);
        menuX = Math.round(canvasWidth * 0.03f);
        headerY = Math.round(canvasHeight * (compact ? 0.01f : 0.03f));
        // Leave room for the built-in six-character labels and the preview arrow at either breakpoint.
        int idealWidth = compact ? Math.min(Math.round(canvasWidth * 0.47f), 135) : Math.round(canvasWidth * 0.26f);
        menuWidth = Math.min(canvasWidth - 2 * menuX, Math.max(108, idealWidth));
        listY = headerY + Math.round(font.lineHeight * ModeSelectButton.LABEL_SCALE) + ROW_GAP;

        int backHeight = 16;
        int backBottom = Math.round(canvasHeight * (compact ? 0.96f : 0.95f));
        int backY = backBottom - backHeight;
        captionRight = canvasWidth - pageInset;
        captionBottom = Math.round(canvasHeight * (compact ? 0.88f : 0.93f));
        if (compact) {
            captionBottom = Math.min(captionBottom, backY - ROW_GAP);
        }
        int preferredWidth = Math.round(canvasWidth * (compact ? 0.90f : 0.61f));
        int maxWidth = canvasWidth - pageInset * 2;
        int menuRight = menuX + menuWidth + SCROLL_HIT_WIDTH;
        int firstRowBottom = listY + ROW_HEIGHT + ROW_GAP;
        int listBottom = backY - ROW_GAP;
        for (ModeDescriptor mode : modes) {
            ModeLobbyCaption caption = ModeLobbyCaption.layout(font, mode, preferredWidth, maxWidth,
                    captionBottom - headerY);
            if (caption.firstLineOverlapping(menuRight, captionRight, captionBottom) < firstRowBottom) {
                caption = ModeLobbyCaption.layout(font, mode, preferredWidth, maxWidth,
                        Math.max(1, captionBottom - firstRowBottom));
            }
            listBottom = Math.min(listBottom,
                    caption.firstLineOverlapping(menuRight, captionRight, captionBottom) - ROW_GAP);
            captions.add(caption);
            rows.add(addRenderableWidget(new ModeSelectButton(
                    new ScreenRectangle(menuX, listY, menuWidth, ROW_HEIGHT), mode,
                    ModePreviewPanel.accentColor(mode.gameType()),
                    button -> minecraft.setScreen(new ModeRoomScreen(mode.gameType(), this)))));
        }

        visibleCount = Math.min(rows.size(), Math.max(1, (listBottom - listY + ROW_GAP) / (ROW_HEIGHT + ROW_GAP)));
        previewIndex = Mth.clamp(previewIndex, 0, Math.max(0, rows.size() - 1));
        previousIndex = -1;
        moveWindowToPreview(previewIndex);
        if (!rows.isEmpty()) {
            rows.get(previewIndex).setPreviewing(true);
        }
        addRenderableWidget(new LobbyBackButton(new ScreenRectangle(pageInset, backY, 66, backHeight), button -> onClose()));
        if (!rows.isEmpty() && minecraft.getLastInputType().isKeyboard()) {
            setFocused(rows.get(previewIndex));
        }
        checkInitialHover = true;
    }

    private void moveWindowToPreview(int index) {
        if (visibleCount > 0) {
            positionRows(Mth.clamp(firstVisible, index - visibleCount + 1, index));
        } else {
            firstVisible = 0;
        }
    }

    private void positionRows(int offset) {
        firstVisible = Mth.clamp(offset, 0, rows.size() - visibleCount);
        for (int index = 0; index < rows.size(); index++) {
            ModeSelectButton row = rows.get(index);
            int slot = index - firstVisible;
            row.visible = slot >= 0 && slot < visibleCount;
            row.active = row.visible;
            if (row.visible) {
                row.setLobbyY(listY + slot * (ROW_HEIGHT + ROW_GAP));
            } else if (getFocused() == row) {
                setFocused(null);
            }
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (checkInitialHover) {
            checkInitialHover = false;
            if (!minecraft.getLastInputType().isKeyboard()) {
                previewUnderPointer(mouseX, mouseY);
            }
        }

        ModePreviewPanel.renderFullscreenBase(graphics, width, height, 1.0f);
        if (!rows.isEmpty()) {
            float progress = transitionProgress();
            if (progress < 1.0f) {
                ModePreviewPanel.renderFullscreenModeLayer(graphics, width, height,
                        rows.get(previousIndex).descriptor(), 1.0f - progress);
            } else {
                previousIndex = -1;
            }
            ModePreviewPanel.renderFullscreenModeLayer(graphics, width, height,
                    rows.get(previewIndex).descriptor(), progress);
        }
        ModePreviewPanel.renderLobbyShade(graphics, width, height);
        LobbyCanvas.begin(graphics);
        GuiTextHelper.drawScaledString(graphics, font, HEADER.getVisualOrderText(), menuX, headerY,
                ModeSelectButton.LABEL_SCALE, CodTheme.TEXT_PRIMARY, true);
        if (rows.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.codpattern.mode_select.empty"),
                    canvasWidth / 2, (canvasHeight - font.lineHeight) / 2, CodTheme.TEXT_PRIMARY);
        } else {
            captions.get(previewIndex).draw(graphics, captionRight, captionBottom);
        }
        if (rows.size() > visibleCount) {
            renderScrollbar(graphics);
        }
        graphics.pose().popPose();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int x = menuX + menuWidth + ROW_GAP;
        int height = listHeight();
        int thumb = Math.max(5, height * visibleCount / rows.size());
        int y = listY + (height - thumb) * firstVisible / (rows.size() - visibleCount);
        graphics.fill(x, listY, x + 2, listY + height, 0x800B0F12);
        graphics.fill(x, y, x + 2, y + thumb, 0xCCDEE4E4);
    }

    private int listHeight() {
        return visibleCount * (ROW_HEIGHT + ROW_GAP) - ROW_GAP;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        previewUnderPointer(mouseX, mouseY);
    }

    private void previewUnderPointer(double mouseX, double mouseY) {
        for (ModeSelectButton row : rows) {
            if (row.isMouseOver(mouseX, mouseY)) {
                setFocused(row);
                return;
            }
        }
    }

    private void scrollTo(int offset, double mouseX, double mouseY) {
        positionRows(offset);
        previewUnderPointer(mouseX, mouseY);
        if (!rows.get(previewIndex).visible) {
            setFocused(rows.get(Mth.clamp(previewIndex, firstVisible, firstVisible + visibleCount - 1)));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double x = LobbyCanvas.toReference(mouseX);
        double y = LobbyCanvas.toReference(mouseY);
        if (rows.size() > visibleCount && delta != 0 && x >= menuX && x < menuX + menuWidth + SCROLL_HIT_WIDTH
                && y >= listY && y < listY + listHeight()) {
            scrollTo(firstVisible + (delta > 0 ? -1 : 1), mouseX, mouseY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int trackX = menuX + menuWidth;
        double x = LobbyCanvas.toReference(mouseX);
        double y = LobbyCanvas.toReference(mouseY);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && rows.size() > visibleCount
                && x >= trackX && x < trackX + SCROLL_HIT_WIDTH && y >= listY && y < listY + listHeight()) {
            int slot = (int) ((y - listY) * rows.size() / listHeight());
            scrollTo(slot - visibleCount / 2, mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!rows.isEmpty() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            int index = getFocused() instanceof ModeSelectButton
                    ? previewIndex + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1) : previewIndex;
            index = Mth.clamp(index, 0, rows.size() - 1);
            moveWindowToPreview(index);
            setFocused(rows.get(index));
            return true;
        }
        if (!rows.isEmpty() && getFocused() == null
                && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            setFocused(rows.get(previewIndex));
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void setFocused(GuiEventListener listener) {
        super.setFocused(listener);
        if (listener instanceof ModeSelectButton row) {
            int index = rows.indexOf(row);
            if (index >= 0 && index != previewIndex) {
                changePreview(index);
            }
        }
    }

    private void changePreview(int index) {
        long now = Util.getMillis();
        float progress = transitionProgress();
        int from = previewIndex;
        if (index == previousIndex) {
            // Reversing the same pair should continue from the current blend.
            transitionStart = now - Math.round((1.0f - progress) * CROSS_FADE_MS);
        } else {
            if (progress < 0.5f) {
                from = previousIndex;
            }
            transitionStart = now;
        }
        rows.get(previewIndex).setPreviewing(false);
        previousIndex = from;
        previewIndex = index;
        rows.get(previewIndex).setPreviewing(true);
    }

    private float transitionProgress() {
        return previousIndex < 0 ? 1.0f
                : Mth.clamp((Util.getMillis() - transitionStart) / (float) CROSS_FADE_MS, 0.0f, 1.0f);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(previousScreen);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
