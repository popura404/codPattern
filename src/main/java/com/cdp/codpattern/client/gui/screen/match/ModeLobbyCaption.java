package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public final class ModeLobbyCaption {
    private static final float CHINESE_SCALE = 1.5f;
    private static final float ENGLISH_SCALE = 4f;
    private static final int LINE_GAP = 3;

    private record Line(FormattedCharSequence text, float scale, int width, int y, int color) {}

    private final Font font;
    private final List<Line> lines = new ArrayList<>();
    private int height;

    private ModeLobbyCaption(Font font) {
        this.font = font;
    }

    public static ModeLobbyCaption layout(Font font, ModeDescriptor mode, int preferredWidth, int maxWidth, int maxHeight) {
        ModeLobbyCaption caption = new ModeLobbyCaption(font);
        if (maxWidth <= 0 || maxHeight <= 0) {
            return caption;
        }
        ModeLobbyTitles titles = ModeLobbyTitles.forMode(mode);
        Component description = ModePreviewPanel.resolveModeDescription(mode.gameType());
        int minHeight = lineHeight(font, CHINESE_SCALE) + LINE_GAP + lineHeight(font, ENGLISH_SCALE);
        boolean hasDescription = !description.getString().isBlank();
        if (hasDescription) {
            minHeight += LINE_GAP + font.lineHeight;
        }
        // Only compress as a group when even one line of each tier would not fit.
        float fit = Math.min(1f, maxHeight / (float) minHeight);
        int gap = (int) (LINE_GAP * fit);
        float chineseScale = CHINESE_SCALE * fit;
        float englishScale = ENGLISH_SCALE * fit;
        int width = preferredWidth;
        for (String word : titles.english().split("\\s+")) {
            Component boldWord = Component.literal(word).withStyle(ChatFormatting.BOLD);
            int wordWidth = (int) Math.ceil(font.width(boldWord) * englishScale);
            width = Math.max(width, wordWidth);
        }
        width = Math.min(maxWidth, width);
        int englishLine = lineHeight(font, englishScale);
        int bodyReserve = hasDescription ? gap + lineHeight(font, fit) : 0;

        caption.append(Component.literal(titles.chinese()), chineseScale, ModePreviewPanel.accentColor(mode.gameType()),
                width, maxHeight - gap - englishLine - bodyReserve);
        caption.height += gap;
        caption.append(Component.literal(titles.english()).withStyle(ChatFormatting.BOLD), englishScale, 0xFFFFFF,
                width, maxHeight - caption.height - bodyReserve);
        if (hasDescription) {
            caption.height += gap;
            caption.append(description, fit, 0xE3E7E7, width, maxHeight - caption.height);
        }
        return caption;
    }

    private void append(Component text, float scale, int color, int maxWidth, int availableHeight) {
        int lineHeight = lineHeight(font, scale);
        int width = Math.max(1, (int) Math.floor(maxWidth / scale));
        List<FormattedText> wrapped = font.getSplitter().splitLines(text, width, Style.EMPTY);
        int count = Math.min(wrapped.size(), Math.max(0, availableHeight / lineHeight));
        for (int i = 0; i < count; i++) {
            FormattedText line = wrapped.get(i);
            if (i == count - 1 && count < wrapped.size()) {
                FormattedText suffix = FormattedText.of("…", text.getStyle());
                line = FormattedText.composite(font.substrByWidth(line, Math.max(0, width - font.width(suffix))), suffix);
            }
            FormattedCharSequence visual = Language.getInstance().getVisualOrder(line);
            lines.add(new Line(visual, scale, Math.round(font.width(visual) * scale), height, color));
            height += lineHeight;
        }
    }

    private static int lineHeight(Font font, float scale) {
        return Math.max(1, Math.round(font.lineHeight * scale));
    }

    public int firstLineOverlapping(int menuRight, int captionRight, int captionBottom) {
        int top = captionBottom - height;
        for (Line line : lines) {
            if (captionRight - line.width() < menuRight) {
                return top + line.y();
            }
        }
        return captionBottom;
    }

    public void draw(GuiGraphics graphics, int right, int bottom) {
        int top = bottom - height;
        for (Line line : lines) {
            GuiTextHelper.drawScaledString(graphics, font, line.text(), right - line.width(), top + line.y(),
                    line.scale(), 0xFF000000 | line.color(), true);
        }
    }
}
