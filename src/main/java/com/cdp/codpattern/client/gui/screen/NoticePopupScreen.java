package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.client.gui.CodTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Generic single-action popup screen used for warning/info notices.
 */
public class NoticePopupScreen extends Screen {
    private static final int POPUP_MIN_WIDTH = 240;
    private static final int POPUP_MAX_WIDTH = 380;

    private final Screen parent;
    private final Component message;

    public NoticePopupScreen(Screen parent, Component title, Component message) {
        super(title == null ? Component.translatable("screen.codpattern.popup.title") : title);
        this.parent = parent;
        this.message = message == null ? Component.empty() : message;
    }

    public Screen parentScreen() {
        return parent;
    }

    @Override
    protected void init() {
        int popupWidth = popupWidth();
        int popupLeft = (this.width - popupWidth) / 2;
        int popupBottom = popupBottomY();
        int buttonWidth = 110;
        int buttonHeight = 20;

        addRenderableWidget(Button.builder(
                Component.translatable("screen.codpattern.common.confirm"),
                btn -> closeAndReturn())
                .bounds(
                        popupLeft + (popupWidth - buttonWidth) / 2,
                        popupBottom - 30,
                        buttonWidth,
                        buttonHeight)
                .build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xA0000000, 0xA0000000);

        int popupWidth = popupWidth();
        int popupHeight = popupHeight(popupWidth);
        int popupLeft = (this.width - popupWidth) / 2;
        int popupTop = (this.height - popupHeight) / 2;
        int popupRight = popupLeft + popupWidth;
        int popupBottom = popupTop + popupHeight;

        graphics.fillGradient(popupLeft, popupTop, popupRight, popupBottom, CodTheme.CARD_BG_TOP, CodTheme.CARD_BG_BOTTOM);
        graphics.fill(popupLeft, popupTop, popupRight, popupTop + 1, CodTheme.HOVER_BORDER_SEMI);
        graphics.fill(popupLeft, popupBottom - 1, popupRight, popupBottom, CodTheme.BORDER_SUBTLE);
        graphics.fill(popupLeft, popupTop, popupLeft + 1, popupBottom, CodTheme.BORDER_SUBTLE);
        graphics.fill(popupRight - 1, popupTop, popupRight, popupBottom, CodTheme.BORDER_SUBTLE);

        int titleY = popupTop + 14;
        graphics.drawCenteredString(this.font, this.title, this.width / 2, titleY, CodTheme.TEXT_PRIMARY);

        int textWidth = popupWidth - 30;
        int textX = popupLeft + 15;
        int textY = titleY + 20;
        List<FormattedCharSequence> lines = this.font.split(message, textWidth);
        int maxLines = Math.max(1, (popupBottom - 40 - textY) / 10);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            graphics.drawString(this.font, lines.get(i), textX, textY + i * 10, CodTheme.TEXT_SECONDARY, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        closeAndReturn();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void closeAndReturn() {
        Minecraft.getInstance().setScreen(parent);
    }

    private int popupWidth() {
        int widthByScreen = Math.max(POPUP_MIN_WIDTH, this.width - 80);
        return Math.min(POPUP_MAX_WIDTH, widthByScreen);
    }

    private int popupHeight(int popupWidth) {
        int lineCount = this.font.split(message, popupWidth - 30).size();
        int textHeight = Math.max(20, lineCount * 10);
        return Math.max(120, Math.min(this.height - 40, 58 + textHeight + 40));
    }

    private int popupBottomY() {
        int popupWidth = popupWidth();
        int popupHeight = popupHeight(popupWidth);
        int popupTop = (this.height - popupHeight) / 2;
        return popupTop + popupHeight;
    }
}
