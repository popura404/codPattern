package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.client.gui.refit.TdmRoomActionButton;
import com.cdp.codpattern.client.gui.screen.tdm.TdmModePreviewPanel;
import com.cdp.codpattern.client.gui.screen.tdm.TdmModeSelectButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TdmModeSelectScreen extends Screen {
    private static final int BASE_PAGE_PADDING = 16;
    private static final int BASE_HEADER_TOP = 18;
    private static final int BASE_SECTION_GAP = 12;
    private static final int BASE_GRID_PANEL_PADDING = 10;
    private static final int BASE_CARD_HEIGHT = 52;
    private static final int BASE_CARD_GAP = 8;
    private static final int BASE_PREVIEW_HEIGHT = 168;
    private static final int BASE_FOOTER_HEIGHT = 24;
    private static final long SCREEN_REVEAL_MS = 180L;
    private static final int MAX_COLUMNS = 3;

    private final Screen previousScreen;
    private final List<ModeDescriptor> availableModes = new ArrayList<>();
    private final List<TdmModeSelectButton> modeButtons = new ArrayList<>();

    private ModeDescriptor defaultPreviewMode;
    private int previewX;
    private int previewY;
    private int previewWidth;
    private int previewHeight;
    private int gridX;
    private int gridY;
    private int gridWidth;
    private int gridHeight;
    private long openedAtMs;

    public TdmModeSelectScreen(Screen previousScreen) {
        super(Component.translatable("screen.codpattern.mode_select.title"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        availableModes.clear();
        availableModes.addAll(GameModeRegistry.orderedModes());
        defaultPreviewMode = availableModes.isEmpty() ? null : availableModes.get(0);
        modeButtons.clear();
        openedAtMs = System.currentTimeMillis();

        int pagePadding = scaled(BASE_PAGE_PADDING);
        int headerY = scaled(BASE_HEADER_TOP);
        int titleLineHeight = GuiTextHelper.referenceLineHeight(Minecraft.getInstance().font);
        int headerBottom = headerY + titleLineHeight + scaled(8);

        previewX = pagePadding;
        previewY = headerBottom + scaled(BASE_SECTION_GAP);
        previewWidth = Math.max(scaled(240), this.width - pagePadding * 2);

        int footerHeight = scaled(BASE_FOOTER_HEIGHT);
        int gridPanelPadding = scaled(BASE_GRID_PANEL_PADDING);
        int cardHeight = scaled(BASE_CARD_HEIGHT);
        int cardGap = scaled(BASE_CARD_GAP);
        int modeCount = Math.max(1, availableModes.size());
        int columns = determineColumns(modeCount, previewWidth - gridPanelPadding * 2, scaled(168), cardGap);
        int rows = (int) Math.ceil(modeCount / (float) columns);
        gridHeight = rows * cardHeight + Math.max(0, rows - 1) * cardGap + gridPanelPadding * 2;
        previewHeight = Math.max(scaled(112), this.height - previewY - footerHeight - gridHeight - scaled(BASE_SECTION_GAP * 2));
        previewHeight = Math.min(previewHeight, scaled(BASE_PREVIEW_HEIGHT));

        gridX = pagePadding;
        gridY = previewY + previewHeight + scaled(BASE_SECTION_GAP);
        gridWidth = previewWidth;

        addModeButtons(columns, cardHeight, cardGap, gridPanelPadding);

        int backWidth = scaled(68);
        int backHeight = scaled(18);
        int backY = this.height - footerHeight;
        addRenderableWidget(new TdmRoomActionButton(
                pagePadding,
                backY,
                backWidth,
                backHeight,
                Component.translatable("screen.codpattern.common.back"),
                button -> onClose(),
                CodTheme.SELECTED_BORDER));
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float reveal = revealProgress();
        renderBackground(graphics);
        renderHeaderBar(graphics, reveal);
        renderModeGridBackdrop(graphics, reveal);

        ModeDescriptor previewMode = currentPreviewMode(mouseX, mouseY);
        updatePreviewState(previewMode);
        if (previewMode != null) {
            TdmModePreviewPanel.render(
                    graphics,
                    Minecraft.getInstance(),
                    previewX,
                    previewY,
                    previewWidth,
                    previewHeight,
                    previewMode,
                    accentColor(previewMode.gameType()),
                    reveal);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(previousScreen);
            return;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void addModeButtons(int columns, int cardHeight, int cardGap, int gridPanelPadding) {
        if (availableModes.isEmpty()) {
            return;
        }

        int availableWidth = gridWidth - gridPanelPadding * 2;
        int totalGapWidth = Math.max(0, columns - 1) * cardGap;
        int buttonWidth = Math.max(scaled(150), (availableWidth - totalGapWidth) / columns);
        int contentWidth = buttonWidth * columns + totalGapWidth;
        int startX = gridX + gridPanelPadding + Math.max(0, (availableWidth - contentWidth) / 2);
        int startY = gridY + gridPanelPadding;

        for (int index = 0; index < availableModes.size(); index++) {
            ModeDescriptor descriptor = availableModes.get(index);
            int column = index % columns;
            int row = index / columns;
            int x = startX + column * (buttonWidth + cardGap);
            int y = startY + row * (cardHeight + cardGap);

            TdmModeSelectButton button = new TdmModeSelectButton(
                    x,
                    y,
                    buttonWidth,
                    cardHeight,
                    descriptor,
                    accentColor(descriptor.gameType()),
                    btn -> openModeRooms(descriptor));
            modeButtons.add(addRenderableWidget(button));
        }
    }

    private void openModeRooms(ModeDescriptor descriptor) {
        defaultPreviewMode = descriptor;
        Minecraft.getInstance().setScreen(new TdmRoomScreen(descriptor.gameType()));
    }

    private void renderHeaderBar(GuiGraphics graphics, float revealFactor) {
        Minecraft mc = Minecraft.getInstance();
        int titleX = scaled(24);
        int titleY = scaled(BASE_HEADER_TOP);
        int titleLineHeight = GuiTextHelper.referenceLineHeight(mc.font);
        int accentX = Math.max(4, titleX - scaled(6));
        int accentBottom = titleY + titleLineHeight + 1;

        graphics.fill(
                accentX,
                titleY - 1,
                accentX + scaled(2),
                accentBottom,
                scaleAlpha(CodTheme.HOVER_BORDER, 0.72f + (0.28f * revealFactor)));
        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.mode_select.header"),
                titleX,
                titleY,
                scaleAlpha(CodTheme.TEXT_PRIMARY, revealFactor),
                true);
        graphics.fill(
                titleX,
                titleY + titleLineHeight + scaled(4),
                this.width - titleX,
                titleY + titleLineHeight + scaled(5),
                scaleAlpha(CodTheme.DIVIDER, revealFactor));

        int hintRightX = this.width - titleX;
        int hintMaxWidth = Math.max(scaled(72), this.width / 3);
        GuiTextHelper.drawReferenceRightAlignedEllipsizedString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.mode_select.hint").getString(),
                hintRightX,
                titleY,
                hintMaxWidth,
                scaleAlpha(CodTheme.TEXT_SECONDARY, revealFactor),
                false);

        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.mode_select.available_modes", availableModes.size()),
                titleX,
                titleY + titleLineHeight + scaled(8),
                scaleAlpha(CodTheme.TEXT_HOVER, revealFactor),
                false);
    }

    private void renderModeGridBackdrop(GuiGraphics graphics, float revealFactor) {
        int left = gridX;
        int top = gridY;
        int right = gridX + gridWidth;
        int bottom = gridY + gridHeight;
        graphics.fillGradient(
                left,
                top,
                right,
                bottom,
                scaleAlpha(0x2A202020, revealFactor),
                scaleAlpha(0x3A101010, revealFactor));
        graphics.fillGradient(
                left + 1,
                top + 1,
                right - 1,
                top + scaled(18),
                scaleAlpha(0x18FFFFFF, revealFactor),
                scaleAlpha(0x04000000, revealFactor));
        graphics.fill(left, top, right, top + 1, scaleAlpha(CodTheme.BORDER_SUBTLE, revealFactor));
        graphics.fill(left, bottom - 1, right, bottom, scaleAlpha(CodTheme.BORDER_SUBTLE, revealFactor));
        graphics.fill(left, top, left + 1, bottom, scaleAlpha(CodTheme.BORDER_SUBTLE, revealFactor));
        graphics.fill(right - 1, top, right, bottom, scaleAlpha(CodTheme.BORDER_SUBTLE, revealFactor));
    }

    private void updatePreviewState(ModeDescriptor previewMode) {
        String previewType = previewMode == null ? "" : previewMode.gameType();
        for (TdmModeSelectButton button : modeButtons) {
            button.setPreviewing(button.descriptor().gameType().equals(previewType));
        }
    }

    private ModeDescriptor currentPreviewMode(int mouseX, int mouseY) {
        for (TdmModeSelectButton button : modeButtons) {
            if (button.isMouseOver(mouseX, mouseY)) {
                return button.descriptor();
            }
        }
        return defaultPreviewMode;
    }

    private float revealProgress() {
        if (openedAtMs <= 0L) {
            return 1.0f;
        }
        long elapsed = System.currentTimeMillis() - openedAtMs;
        float raw = Math.min(1.0f, Math.max(0.0f, elapsed / (float) SCREEN_REVEAL_MS));
        return 0.2f + (raw * 0.8f);
    }

    private static int determineColumns(int modeCount, int contentWidth, int minButtonWidth, int cardGap) {
        int maxColumns = Math.min(MAX_COLUMNS, Math.max(1, modeCount));
        for (int columns = maxColumns; columns >= 1; columns--) {
            int totalGapWidth = Math.max(0, columns - 1) * cardGap;
            int buttonWidth = (contentWidth - totalGapWidth) / columns;
            if (buttonWidth >= minButtonWidth) {
                return columns;
            }
        }
        return 1;
    }

    private static int accentColor(String gameType) {
        String canonical = TdmGameTypes.canonicalize(gameType);
        if (TdmGameTypes.FRONTLINE.equals(canonical)) {
            return 0xFF62F08A;
        }
        if (TdmGameTypes.TEAM_DEATHMATCH.equals(canonical)) {
            return 0xFF5FC7E6;
        }
        return CodTheme.SELECTED_BORDER;
    }

    private static int scaleAlpha(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int scaledAlpha = Math.max(0, Math.min(255, (int) (alpha * Math.max(0.0f, Math.min(1.0f, factor)))));
        return (scaledAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static int scaled(int value) {
        return GuiTextHelper.referenceScaled(value);
    }
}
