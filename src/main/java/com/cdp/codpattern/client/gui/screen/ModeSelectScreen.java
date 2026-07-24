package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.client.gui.refit.ModeRoomActionButton;
import com.cdp.codpattern.client.gui.screen.match.ModePreviewPanel;
import com.cdp.codpattern.client.gui.screen.match.ModeSelectButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ModeSelectScreen extends Screen {
    private static final int BASE_PAGE_PADDING = 16;
    private static final int BASE_HEADER_TOP = 18;
    private static final int BASE_INFO_TOP = 66;
    private static final int BASE_INFO_PANEL_WIDTH = 260;
    private static final int BASE_INFO_PANEL_HEIGHT = 92;
    private static final int BASE_MODE_BUTTON_WIDTH = 170;
    private static final int BASE_MODE_BUTTON_HEIGHT = 50;
    private static final int BASE_MODE_BUTTON_GAP = 8;
    private static final int BASE_SCROLL_BUTTON_WIDTH = 22;
    private static final int BASE_SCROLL_BUTTON_HEIGHT = 50;
    private static final int BASE_MODE_BAND_BOTTOM = 48;
    private static final int BASE_BACK_BUTTON_WIDTH = 68;
    private static final int BASE_BACK_BUTTON_HEIGHT = 18;
    private static final int BASE_BACK_BUTTON_BOTTOM = 16;
    private static final int BASE_BAND_BACKDROP_PADDING = 14;
    private static final long SCREEN_REVEAL_MS = 180L;
    private static final long BACKGROUND_CROSS_FADE_MS = 220L;

    private final Screen previousScreen;
    private final List<ModeDescriptor> availableModes = new ArrayList<>();
    private final List<ModeSelectButton> modeButtons = new ArrayList<>();

    private ModeRoomActionButton scrollLeftButton;
    private ModeRoomActionButton scrollRightButton;

    private int scrollOffset;
    private int maxScrollOffset;
    private int visibleModeCount;
    private int modeButtonWidth;
    private int modeButtonHeight;
    private int modeButtonGap;
    private int scrollButtonWidth;
    private int scrollButtonHeight;
    private int modeBandY;
    private int bandVisibleWidth;
    private int infoPanelX;
    private int infoPanelY;
    private int infoPanelWidth;
    private int infoPanelHeight;
    private long openedAtMs;

    private ModeDescriptor currentBackgroundMode;
    private ModeDescriptor previousBackgroundMode;
    private long backgroundTransitionStartedAtMs;

    public ModeSelectScreen(Screen previousScreen) {
        super(Component.translatable("screen.codpattern.mode_select.title"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        availableModes.clear();
        availableModes.addAll(GameModeRegistry.orderedModes());
        modeButtons.clear();
        scrollOffset = 0;
        openedAtMs = System.currentTimeMillis();

        currentBackgroundMode = availableModes.isEmpty() ? null : availableModes.get(0);
        previousBackgroundMode = null;
        backgroundTransitionStartedAtMs = 0L;

        computeLayout();
        addScrollButtons();
        rebuildModeButtons();
        addBackButton();
        refreshScrollButtons();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        finishBackgroundTransitionIfComplete();
        updateBackgroundPreview(currentHoveredMode(mouseX, mouseY));

        float revealFactor = revealProgress();
        renderModeBackground(graphics, revealFactor);
        renderHeader(graphics, revealFactor);
        renderModeBandBackdrop(graphics, revealFactor);
        renderModeSpotlight(graphics, revealFactor);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (availableModes.isEmpty()) {
            renderEmptyState(graphics, revealFactor);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScrollOffset <= 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int bandTop = modeBandY - scaled(BASE_BAND_BACKDROP_PADDING);
        int bandBottom = modeBandY + modeButtonHeight + scaled(BASE_BAND_BACKDROP_PADDING);
        if (mouseY >= bandTop && mouseY <= bandBottom) {
            if (delta > 0.0d) {
                scrollLeft();
            } else if (delta < 0.0d) {
                scrollRight();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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

    private void computeLayout() {
        modeButtonWidth = Math.max(scaled(142), scaled(BASE_MODE_BUTTON_WIDTH));
        modeButtonHeight = scaled(BASE_MODE_BUTTON_HEIGHT);
        modeButtonGap = scaled(BASE_MODE_BUTTON_GAP);
        scrollButtonWidth = scaled(BASE_SCROLL_BUTTON_WIDTH);
        scrollButtonHeight = scaled(BASE_SCROLL_BUTTON_HEIGHT);
        modeBandY = this.height - scaled(BASE_MODE_BAND_BOTTOM) - modeButtonHeight;

        int pagePadding = scaled(BASE_PAGE_PADDING);
        int availableBandWidth = Math.max(
                modeButtonWidth,
                this.width - pagePadding * 2 - scrollButtonWidth * 2 - modeButtonGap * 4);
        visibleModeCount = availableModes.isEmpty()
                ? 0
                : Math.max(1, Math.min(availableModes.size(), (availableBandWidth + modeButtonGap) / (modeButtonWidth + modeButtonGap)));
        bandVisibleWidth = visibleModeCount <= 0
                ? 0
                : visibleModeCount * modeButtonWidth + Math.max(0, visibleModeCount - 1) * modeButtonGap;
        maxScrollOffset = visibleModeCount <= 0
                ? 0
                : Math.max(0, availableModes.size() - visibleModeCount);
        scrollOffset = clamp(scrollOffset, 0, maxScrollOffset);

        infoPanelX = pagePadding;
        infoPanelY = scaled(BASE_INFO_TOP);
        infoPanelWidth = clamp(this.width / 3, scaled(178), scaled(BASE_INFO_PANEL_WIDTH));
        infoPanelHeight = scaled(BASE_INFO_PANEL_HEIGHT);
    }

    private void addScrollButtons() {
        int bandStartX = bandStartX();
        int scrollY = modeBandY + Math.max(0, (modeButtonHeight - scrollButtonHeight) / 2);

        scrollLeftButton = addRenderableWidget(createScrollButton(
                bandStartX - modeButtonGap - scrollButtonWidth,
                scrollY,
                Component.translatable("screen.codpattern.mode_select.scroll_left"),
                "<",
                button -> scrollLeft()));
        scrollRightButton = addRenderableWidget(createScrollButton(
                bandStartX + bandVisibleWidth + modeButtonGap,
                scrollY,
                Component.translatable("screen.codpattern.mode_select.scroll_right"),
                ">",
                button -> scrollRight()));
    }

    private void addBackButton() {
        int pagePadding = scaled(BASE_PAGE_PADDING);
        int backWidth = scaled(BASE_BACK_BUTTON_WIDTH);
        int backHeight = scaled(BASE_BACK_BUTTON_HEIGHT);
        int backY = this.height - scaled(BASE_BACK_BUTTON_BOTTOM) - backHeight;
        addRenderableWidget(new ModeRoomActionButton(
                pagePadding,
                backY,
                backWidth,
                backHeight,
                Component.translatable("screen.codpattern.common.back"),
                button -> onClose(),
                CodTheme.SELECTED_BORDER));
    }

    private ModeRoomActionButton createScrollButton(int x, int y, Component tooltip, String glyph, ModeRoomActionButton.OnPress onPress) {
        ModeRoomActionButton button = new ModeRoomActionButton(
                x,
                y,
                scrollButtonWidth,
                scrollButtonHeight,
                Component.empty(),
                onPress,
                CodTheme.SELECTED_BORDER);
        button.setPrimaryStyle(true);
        button.setPrimaryGlyph(glyph);
        button.setTooltipText(tooltip);
        return button;
    }

    private void rebuildModeButtons() {
        for (ModeSelectButton button : modeButtons) {
            removeWidget(button);
        }
        modeButtons.clear();

        if (availableModes.isEmpty() || visibleModeCount <= 0) {
            return;
        }

        int startIndex = clamp(scrollOffset, 0, maxScrollOffset);
        int endIndex = Math.min(availableModes.size(), startIndex + visibleModeCount);
        int visibleCount = Math.max(0, endIndex - startIndex);
        int totalWidth = visibleCount <= 0
                ? 0
                : visibleCount * modeButtonWidth + Math.max(0, visibleCount - 1) * modeButtonGap;
        int startX = (this.width - totalWidth) / 2;

        for (int index = startIndex; index < endIndex; index++) {
            ModeDescriptor descriptor = availableModes.get(index);
            int visibleIndex = index - startIndex;
            int x = startX + visibleIndex * (modeButtonWidth + modeButtonGap);
            ModeSelectButton button = new ModeSelectButton(
                    x,
                    modeBandY,
                    modeButtonWidth,
                    modeButtonHeight,
                    descriptor,
                    ModePreviewPanel.accentColor(descriptor.gameType()),
                    btn -> openModeRooms(descriptor));
            modeButtons.add(addRenderableWidget(button));
        }
        refreshButtonPreviewState();
        refreshScrollButtons();
    }

    private void renderModeBackground(GuiGraphics graphics, float revealFactor) {
        renderDirtBackground(graphics);
        ModePreviewPanel.renderFullscreenBase(
                graphics,
                this.width,
                this.height,
                0.84f + (0.16f * revealFactor));

        float atmosphereAlpha = 0.45f + (0.55f * revealFactor);
        if (currentBackgroundMode == null) {
            renderGlobalScrim(graphics, revealFactor);
            return;
        }

        if (isBackgroundTransitionActive()) {
            float transition = backgroundTransitionProgress();
            ModePreviewPanel.renderFullscreenModeLayer(
                    graphics,
                    this.width,
                    this.height,
                    previousBackgroundMode,
                    atmosphereAlpha * (1.0f - transition));
            ModePreviewPanel.renderFullscreenModeLayer(
                    graphics,
                    this.width,
                    this.height,
                    currentBackgroundMode,
                    atmosphereAlpha * transition);
        } else {
            ModePreviewPanel.renderFullscreenModeLayer(
                    graphics,
                    this.width,
                    this.height,
                    currentBackgroundMode,
                    atmosphereAlpha);
        }

        renderGlobalScrim(graphics, revealFactor);
    }

    private void renderGlobalScrim(GuiGraphics graphics, float revealFactor) {
        int topShadowBottom = Math.max(scaled(82), this.height / 3);
        graphics.fillGradient(
                0,
                0,
                this.width,
                topShadowBottom,
                scaleAlpha(0x7A050607, revealFactor),
                0x00000000);
        graphics.fillGradient(
                0,
                this.height / 2,
                this.width,
                this.height,
                0x00000000,
                scaleAlpha(0xD8101010, revealFactor));
    }

    private void renderHeader(GuiGraphics graphics, float revealFactor) {
        Minecraft mc = Minecraft.getInstance();
        int pagePadding = scaled(BASE_PAGE_PADDING);
        int titleY = scaled(BASE_HEADER_TOP);
        int titleLineHeight = GuiTextHelper.referenceLineHeight(mc.font);
        int headerRight = this.width - pagePadding;

        graphics.fill(
                pagePadding - scaled(6),
                titleY - 1,
                pagePadding - scaled(4),
                titleY + titleLineHeight + 1,
                scaleAlpha(CodTheme.HOVER_BORDER, 0.80f * revealFactor));
        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.mode_select.header"),
                pagePadding,
                titleY,
                scaleAlpha(CodTheme.TEXT_PRIMARY, revealFactor),
                true);
        GuiTextHelper.drawReferenceString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.mode_select.available_modes", availableModes.size()),
                pagePadding,
                titleY + titleLineHeight + scaled(8),
                scaleAlpha(CodTheme.TEXT_HOVER, revealFactor),
                false);

        int hintWidth = Math.max(scaled(96), this.width / 3);
        GuiTextHelper.drawReferenceRightAlignedEllipsizedString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.mode_select.hint").getString(),
                headerRight,
                titleY,
                hintWidth,
                scaleAlpha(CodTheme.TEXT_SECONDARY, revealFactor),
                false);

        if (maxScrollOffset > 0 && visibleModeCount > 0) {
            int start = scrollOffset + 1;
            int end = Math.min(availableModes.size(), scrollOffset + visibleModeCount);
            String windowLabel = start + "-" + end + " / " + availableModes.size();
            GuiTextHelper.drawReferenceRightAlignedEllipsizedString(
                    graphics,
                    mc.font,
                    windowLabel,
                    headerRight,
                    titleY + titleLineHeight + scaled(8),
                    hintWidth,
                    scaleAlpha(CodTheme.SELECTED_TEXT, revealFactor),
                    false);
        }
    }

    private void renderModeBandBackdrop(GuiGraphics graphics, float revealFactor) {
        int bandTop = modeBandY - scaled(BASE_BAND_BACKDROP_PADDING);
        int bandBottom = modeBandY + modeButtonHeight + scaled(BASE_BAND_BACKDROP_PADDING);
        graphics.fillGradient(
                0,
                bandTop,
                this.width,
                bandBottom,
                0x00000000,
                scaleAlpha(0xC4141414, revealFactor));
        graphics.fillGradient(
                0,
                bandTop,
                this.width,
                bandTop + scaled(18),
                scaleAlpha(0x28FFFFFF, revealFactor),
                0x00000000);
        graphics.fill(
                0,
                bandTop,
                this.width,
                bandTop + 1,
                scaleAlpha(CodTheme.BORDER_SUBTLE, revealFactor));
    }

    private void renderModeSpotlight(GuiGraphics graphics, float revealFactor) {
        if (currentBackgroundMode == null) {
            return;
        }
        if (isBackgroundTransitionActive()) {
            float transition = backgroundTransitionProgress();
            renderModeSpotlightCard(graphics, previousBackgroundMode, revealFactor * (1.0f - transition));
            renderModeSpotlightCard(graphics, currentBackgroundMode, revealFactor * transition);
            return;
        }
        renderModeSpotlightCard(graphics, currentBackgroundMode, revealFactor);
    }

    private void renderModeSpotlightCard(GuiGraphics graphics, ModeDescriptor descriptor, float alphaFactor) {
        if (descriptor == null || alphaFactor <= 0.0f) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int accentColor = ModePreviewPanel.accentColor(descriptor.gameType());
        int left = infoPanelX;
        int top = infoPanelY;
        int right = left + infoPanelWidth;
        int bottom = top + infoPanelHeight;
        int innerLeft = left + scaled(14);
        int innerRight = right - scaled(12);
        int textWidth = Math.max(scaled(80), innerRight - innerLeft);
        int titleY = top + scaled(24);
        int titleHeight = GuiTextHelper.referenceLineHeight(mc.font, 1.8f);
        int bodyY = titleY + titleHeight + scaled(6);
        int footerY = bottom - GuiTextHelper.referenceLineHeight(mc.font) - scaled(10);

        graphics.fillGradient(
                left,
                top,
                right,
                bottom,
                scaleAlpha(0x52111214, alphaFactor),
                scaleAlpha(0xA0141416, alphaFactor));
        graphics.fill(left, top, left + scaled(3), bottom, scaleAlpha(withAlpha(accentColor, 220), alphaFactor));
        graphics.fill(left, top, right, top + 1, scaleAlpha(withAlpha(0xFFFFFFFF, 126), alphaFactor));
        graphics.fill(left, bottom - 1, right, bottom, scaleAlpha(withAlpha(accentColor, 185), alphaFactor));
        graphics.fill(left, top, right, bottom, scaleAlpha(withAlpha(accentColor, 18), alphaFactor));
        graphics.fillGradient(
                left + scaled(3),
                top + 1,
                right - 1,
                top + scaled(22),
                scaleAlpha(withAlpha(accentColor, 52), alphaFactor),
                0x00000000);

        GuiTextHelper.drawReferenceEllipsizedString(
                graphics,
                mc.font,
                descriptor.gameType().toUpperCase(),
                innerLeft,
                top + scaled(8),
                textWidth,
                scaleAlpha(withAlpha(accentColor, 235), alphaFactor),
                false);
        GuiTextHelper.drawReferenceScaledEllipsizedString(
                graphics,
                mc.font,
                Component.translatable(descriptor.displayNameKey()),
                innerLeft,
                titleY,
                textWidth,
                1.8f,
                scaleAlpha(CodTheme.TEXT_PRIMARY, alphaFactor),
                true);
        GuiTextHelper.drawReferenceEllipsizedString(
                graphics,
                mc.font,
                ModePreviewPanel.resolveModeDescription(descriptor.gameType()),
                innerLeft,
                bodyY,
                textWidth,
                scaleAlpha(0xFFE7E7E7, alphaFactor),
                false);
        GuiTextHelper.drawReferenceRightAlignedEllipsizedString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.mode_select.preview_hint").getString(),
                innerRight,
                footerY,
                textWidth,
                scaleAlpha(CodTheme.TEXT_SECONDARY, alphaFactor),
                false);
    }

    private void renderEmptyState(GuiGraphics graphics, float revealFactor) {
        Minecraft mc = Minecraft.getInstance();
        GuiTextHelper.drawReferenceCenteredString(
                graphics,
                mc.font,
                Component.translatable("mode.codpattern.unknown"),
                this.width / 2.0f,
                this.height / 2.0f - scaled(8),
                scaleAlpha(CodTheme.TEXT_PRIMARY, revealFactor),
                false);
        GuiTextHelper.drawReferenceCenteredString(
                graphics,
                mc.font,
                Component.translatable("screen.codpattern.mode_select.preview_hint"),
                this.width / 2.0f,
                this.height / 2.0f + scaled(6),
                scaleAlpha(CodTheme.TEXT_SECONDARY, revealFactor),
                false);
    }

    private void openModeRooms(ModeDescriptor descriptor) {
        currentBackgroundMode = descriptor;
        previousBackgroundMode = null;
        backgroundTransitionStartedAtMs = 0L;
        Minecraft.getInstance().setScreen(new ModeRoomScreen(descriptor.gameType(), this));
    }

    private void scrollLeft() {
        if (scrollOffset <= 0) {
            return;
        }
        scrollOffset--;
        rebuildModeButtons();
    }

    private void scrollRight() {
        if (scrollOffset >= maxScrollOffset) {
            return;
        }
        scrollOffset++;
        rebuildModeButtons();
    }

    private void refreshScrollButtons() {
        int bandStartX = bandStartX();
        int scrollY = modeBandY + Math.max(0, (modeButtonHeight - scrollButtonHeight) / 2);
        boolean showScrollButtons = maxScrollOffset > 0 && visibleModeCount > 0;

        if (scrollLeftButton != null) {
            scrollLeftButton.visible = showScrollButtons;
            scrollLeftButton.active = showScrollButtons && scrollOffset > 0;
            scrollLeftButton.setX(bandStartX - modeButtonGap - scrollButtonWidth);
            scrollLeftButton.setY(scrollY);
        }
        if (scrollRightButton != null) {
            scrollRightButton.visible = showScrollButtons;
            scrollRightButton.active = showScrollButtons && scrollOffset < maxScrollOffset;
            scrollRightButton.setX(bandStartX + bandVisibleWidth + modeButtonGap);
            scrollRightButton.setY(scrollY);
        }
    }

    private void updateBackgroundPreview(ModeDescriptor hoveredMode) {
        if (hoveredMode == null) {
            refreshButtonPreviewState();
            return;
        }
        if (sameMode(hoveredMode, currentBackgroundMode)) {
            refreshButtonPreviewState();
            return;
        }

        finishBackgroundTransitionIfComplete();
        if (isBackgroundTransitionActive()) {
            ModeDescriptor interruptedMode = dominantTransitionMode();
            if (sameMode(hoveredMode, interruptedMode)) {
                currentBackgroundMode = interruptedMode;
                previousBackgroundMode = null;
                backgroundTransitionStartedAtMs = 0L;
                refreshButtonPreviewState();
                return;
            }
            previousBackgroundMode = interruptedMode;
        } else {
            previousBackgroundMode = currentBackgroundMode;
        }
        currentBackgroundMode = hoveredMode;
        backgroundTransitionStartedAtMs = System.currentTimeMillis();
        refreshButtonPreviewState();
    }

    private void refreshButtonPreviewState() {
        String currentType = currentBackgroundMode == null ? "" : currentBackgroundMode.gameType();
        for (ModeSelectButton button : modeButtons) {
            button.setPreviewing(button.descriptor().gameType().equals(currentType));
        }
    }

    private ModeDescriptor currentHoveredMode(int mouseX, int mouseY) {
        for (ModeSelectButton button : modeButtons) {
            if (mouseX >= button.getX()
                    && mouseX < button.getX() + button.getWidth()
                    && mouseY >= button.getY()
                    && mouseY < button.getY() + button.getHeight()) {
                return button.descriptor();
            }
        }
        return null;
    }

    private void finishBackgroundTransitionIfComplete() {
        if (previousBackgroundMode == null || backgroundTransitionStartedAtMs <= 0L) {
            return;
        }
        if (System.currentTimeMillis() - backgroundTransitionStartedAtMs >= BACKGROUND_CROSS_FADE_MS) {
            previousBackgroundMode = null;
            backgroundTransitionStartedAtMs = 0L;
        }
    }

    private boolean isBackgroundTransitionActive() {
        return previousBackgroundMode != null && backgroundTransitionStartedAtMs > 0L;
    }

    private float backgroundTransitionProgress() {
        if (!isBackgroundTransitionActive()) {
            return 1.0f;
        }
        long elapsed = System.currentTimeMillis() - backgroundTransitionStartedAtMs;
        return Math.min(1.0f, Math.max(0.0f, elapsed / (float) BACKGROUND_CROSS_FADE_MS));
    }

    private ModeDescriptor dominantTransitionMode() {
        if (!isBackgroundTransitionActive()) {
            return currentBackgroundMode;
        }
        return backgroundTransitionProgress() < 0.5f ? previousBackgroundMode : currentBackgroundMode;
    }

    private float revealProgress() {
        if (openedAtMs <= 0L) {
            return 1.0f;
        }
        long elapsed = System.currentTimeMillis() - openedAtMs;
        float raw = Math.min(1.0f, Math.max(0.0f, elapsed / (float) SCREEN_REVEAL_MS));
        return 0.22f + (raw * 0.78f);
    }

    private int bandStartX() {
        return (this.width - bandVisibleWidth) / 2;
    }

    private static boolean sameMode(ModeDescriptor left, ModeDescriptor right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.gameType().equals(right.gameType());
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int scaleAlpha(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int scaledAlpha = Math.max(0, Math.min(255, (int) (alpha * Math.max(0.0f, Math.min(1.0f, factor)))));
        return (scaledAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int scaled(int value) {
        return GuiTextHelper.referenceScaled(value);
    }
}
