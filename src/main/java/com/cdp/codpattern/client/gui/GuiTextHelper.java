package com.cdp.codpattern.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public final class GuiTextHelper {
    private static final float REFERENCE_GUI_SCALE = 2.0f;
    private GuiTextHelper() {
    }

    private static final String ELLIPSIS = "...";

    public static float referenceScale() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return 1.0f;
        }
        double guiScale = minecraft.getWindow().getGuiScale();
        if (guiScale <= 0.0d) {
            return 1.0f;
        }
        return (float) (REFERENCE_GUI_SCALE / guiScale);
    }

    public static int referenceScaled(int value) {
        if (value <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(value * referenceScale()));
    }

    public static int referenceLineHeight(Font font) {
        return Math.max(1, Math.round(font.lineHeight * referenceScale()));
    }

    public static int referenceWidth(Font font, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.round(font.width(text) * referenceScale());
    }

    public static int referenceWidth(Font font, Component text) {
        if (text == null) {
            return 0;
        }
        return Math.round(font.width(text) * referenceScale());
    }

    public static int referenceWidth(Font font, FormattedCharSequence text) {
        if (text == null) {
            return 0;
        }
        return Math.round(font.width(text) * referenceScale());
    }

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

    public static String ellipsizeReference(Font font, Component text, int maxWidth) {
        return ellipsizeReference(font, text == null ? "" : text.getString(), maxWidth);
    }

    public static String ellipsizeReference(Font font, String raw, int maxWidth) {
        return ellipsize(font, raw, toReferenceFontWidth(maxWidth));
    }

    public static FormattedCharSequence ellipsizeReferenceFormatted(Font font, Component text, int maxWidth) {
        return ellipsizeFormatted(font, text, toReferenceFontWidth(maxWidth));
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

    public static void drawReferenceString(
            GuiGraphics graphics,
            Font font,
            String text,
            float x,
            float y,
            int color,
            boolean shadow) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float scale = referenceScale();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    public static void drawReferenceString(
            GuiGraphics graphics,
            Font font,
            Component text,
            float x,
            float y,
            int color,
            boolean shadow) {
        if (text == null) {
            return;
        }
        drawReferenceString(graphics, font, text.getVisualOrderText(), x, y, color, shadow);
    }

    public static void drawReferenceCenteredString(
            GuiGraphics graphics,
            Font font,
            String text,
            float centerX,
            float y,
            int color,
            boolean shadow) {
        drawReferenceString(graphics, font, text, centerX - referenceWidth(font, text) / 2.0f, y, color, shadow);
    }

    public static void drawReferenceCenteredString(
            GuiGraphics graphics,
            Font font,
            Component text,
            float centerX,
            float y,
            int color,
            boolean shadow) {
        drawReferenceString(graphics, font, text, centerX - referenceWidth(font, text) / 2.0f, y, color, shadow);
    }

    public static void drawReferenceEllipsizedString(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int maxWidth,
            int color,
            boolean shadow) {
        drawReferenceString(graphics, font, ellipsizeReference(font, text, maxWidth), x, y, color, shadow);
    }

    public static void drawReferenceEllipsizedString(
            GuiGraphics graphics,
            Font font,
            Component text,
            int x,
            int y,
            int maxWidth,
            int color,
            boolean shadow) {
        drawReferenceString(graphics, font, ellipsizeReferenceFormatted(font, text, maxWidth), x, y, color, shadow);
    }

    public static void drawReferenceCenteredEllipsizedString(
            GuiGraphics graphics,
            Font font,
            String text,
            int centerX,
            int y,
            int maxWidth,
            int color,
            boolean shadow) {
        String fitted = ellipsizeReference(font, text, maxWidth);
        drawReferenceString(graphics, font, fitted, centerX - referenceWidth(font, fitted) / 2.0f, y, color, shadow);
    }

    public static void drawReferenceCenteredEllipsizedString(
            GuiGraphics graphics,
            Font font,
            Component text,
            int centerX,
            int y,
            int maxWidth,
            int color,
            boolean shadow) {
        FormattedCharSequence fitted = ellipsizeReferenceFormatted(font, text, maxWidth);
        drawReferenceString(graphics, font, fitted, centerX - referenceWidth(font, fitted) / 2.0f, y, color, shadow);
    }

    public static void drawReferenceRightAlignedEllipsizedString(
            GuiGraphics graphics,
            Font font,
            String text,
            int rightX,
            int y,
            int maxWidth,
            int color,
            boolean shadow) {
        String fitted = ellipsizeReference(font, text, maxWidth);
        drawReferenceString(graphics, font, fitted, rightX - referenceWidth(font, fitted), y, color, shadow);
    }

    private static void drawReferenceString(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            boolean shadow) {
        if (text == null) {
            return;
        }
        float scale = referenceScale();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    private static int toReferenceFontWidth(int maxWidth) {
        if (maxWidth <= 0) {
            return 0;
        }
        float scale = referenceScale();
        if (scale <= 0.0f) {
            return maxWidth;
        }
        return Math.max(1, (int) Math.floor(maxWidth / scale));
    }
}
