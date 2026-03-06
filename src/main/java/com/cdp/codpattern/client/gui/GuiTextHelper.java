package com.cdp.codpattern.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public final class GuiTextHelper {
    private GuiTextHelper() {
    }

    private static final String ELLIPSIS = "...";

    public static String ellipsize(Font font, Component text, int maxWidth) {
        return ellipsize(font, text == null ? "" : text.getString(), maxWidth);
    }

    public static String ellipsize(Font font, String raw, int maxWidth) {
        if (raw == null || raw.isBlank() || maxWidth <= 0) {
            return "";
        }
        if (font.width(raw) <= maxWidth) {
            return raw;
        }

        int ellipsisWidth = font.width(ELLIPSIS);
        if (ellipsisWidth >= maxWidth) {
            return ELLIPSIS;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            builder.append(raw.charAt(i));
            if (font.width(builder.toString()) + ellipsisWidth > maxWidth) {
                builder.setLength(Math.max(0, builder.length() - 1));
                break;
            }
        }
        return builder + ELLIPSIS;
    }

    public static FormattedCharSequence ellipsizeFormatted(Font font, Component text, int maxWidth) {
        if (text == null || maxWidth <= 0) {
            return FormattedCharSequence.EMPTY;
        }
        if (font.width(text) <= maxWidth) {
            return text.getVisualOrderText();
        }

        int ellipsisWidth = font.width(ELLIPSIS);
        if (ellipsisWidth >= maxWidth) {
            Style style = text.getStyle().isEmpty() ? Style.EMPTY : text.getStyle();
            return Language.getInstance().getVisualOrder(FormattedText.of(ELLIPSIS, style));
        }

        FormattedText prefix = font.substrByWidth(text, maxWidth - ellipsisWidth);
        Style ellipsisStyle = text.getStyle().isEmpty() ? Style.EMPTY : text.getStyle();
        FormattedText composite = FormattedText.composite(prefix, FormattedText.of(ELLIPSIS, ellipsisStyle));
        return Language.getInstance().getVisualOrder(composite);
    }

    public static void drawEllipsizedString(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int maxWidth,
            int color,
            boolean shadow) {
        graphics.drawString(font, ellipsize(font, text, maxWidth), x, y, color, shadow);
    }

    public static void drawEllipsizedString(
            GuiGraphics graphics,
            Font font,
            Component text,
            int x,
            int y,
            int maxWidth,
            int color,
            boolean shadow) {
        graphics.drawString(font, ellipsizeFormatted(font, text, maxWidth), x, y, color, shadow);
    }

    public static void drawCenteredEllipsizedString(
            GuiGraphics graphics,
            Font font,
            String text,
            int centerX,
            int y,
            int maxWidth,
            int color,
            boolean shadow) {
        String fitted = ellipsize(font, text, maxWidth);
        graphics.drawString(font, fitted, centerX - font.width(fitted) / 2, y, color, shadow);
    }

    public static void drawCenteredEllipsizedString(
            GuiGraphics graphics,
            Font font,
            Component text,
            int centerX,
            int y,
            int maxWidth,
            int color,
            boolean shadow) {
        FormattedCharSequence fitted = ellipsizeFormatted(font, text, maxWidth);
        graphics.drawString(font, fitted, centerX - font.width(fitted) / 2, y, color, shadow);
    }

    public static void drawRightAlignedEllipsizedString(
            GuiGraphics graphics,
            Font font,
            String text,
            int rightX,
            int y,
            int maxWidth,
            int color,
            boolean shadow) {
        String fitted = ellipsize(font, text, maxWidth);
        graphics.drawString(font, fitted, rightX - font.width(fitted), y, color, shadow);
    }
}
