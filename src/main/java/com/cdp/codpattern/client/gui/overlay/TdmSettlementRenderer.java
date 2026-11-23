package com.cdp.codpattern.client.gui.overlay;

import com.cdp.codpattern.app.tdm.model.TdmTeamNames;
import com.cdp.codpattern.app.tdm.service.TdmSettlementTiming;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TdmSettlementRenderer {
    private static final double INTRO_END = TdmSettlementTiming.BACKDROP_TICKS / 20.0;
    private static final double SCORES_START = INTRO_END + TdmSettlementTiming.RESULT_TICKS / 20.0;
    private static final double END = TdmSettlementTiming.TOTAL_TICKS / 20.0;

    private enum Verdict {
        VICTORY("victory", "VICTORY", 88, 40, 0xd5fcff, 0x99d7ff),
        DEFEAT("defeat", "DEFEAT", 89, 41, 0xffe7d7, 0xf1a39b),
        DRAW("draw", "DRAW", 87, 39, 0xffffff, 0xbdcdbf);

        final String id, caption;
        final int width, height, ink, accent;
        final ResourceLocation texture;

        Verdict(String id, String caption, int width, int height, int ink, int accent) {
            this.id = id;
            this.caption = caption;
            this.width = width;
            this.height = height;
            this.ink = ink;
            this.accent = accent;
            texture = new ResourceLocation("codpattern", "textures/gui/settlement/" + id + "-type.png");
        }
    }

    // Sample absolute elapsed time. Skipped pulses must never be queued for a later frame.
    private record Flash(double start, double end, float opacity, int replacementInk) {}

    private static final Flash[] FLASHES = {
            new Flash(.4002, .4008, .52f, 0),
            new Flash(.4008, .4013, .14f, 0xff7a7c),
            new Flash(.4013, .4025, .68f, 0),
            new Flash(.4025, .4042, .16f, 0x5aa3ff),
            new Flash(.4042, .4050, .46f, 0),
            new Flash(.4054, .4068, .73f, 0),
            new Flash(.4090, .4100, .84f, 0)
    };

    private TdmSettlementRenderer() {}

    public static void render(GuiGraphics graphics, Font font, int width, int height, double elapsedSeconds,
                              int kortacScore, int specgruScore, String localTeam, UUID localId,
                              Map<String, List<PlayerInfo>> rosters, String modeName) {
        boolean isKortac = TdmTeamNames.KORTAC.equalsIgnoreCase(localTeam);
        boolean affiliated = isKortac || TdmTeamNames.SPECGRU.equalsIgnoreCase(localTeam);
        int homeScore = isKortac ? kortacScore : specgruScore;
        int awayScore = isKortac ? specgruScore : kortacScore;
        boolean neutralWin = !affiliated && homeScore != awayScore;
        Verdict verdict = homeScore == awayScore || neutralWin ? Verdict.DRAW
                : homeScore > awayScore ? Verdict.VICTORY : Verdict.DEFEAT;
        String winningTeam = kortacScore > specgruScore ? TdmTeamNames.KORTAC : TdmTeamNames.SPECGRU;
        float scale = GuiTextHelper.referenceScale();
        int w = Math.round(width / scale), h = Math.round(height / scale);
        boolean reducedMotion = Minecraft.getInstance().options.hideLightningFlash().get();
        float growth = elapsedSeconds < INTRO_END ? -1f : reducedMotion ? .45f
                : (float) Mth.clamp((elapsedSeconds - INTRO_END) / (END - INTRO_END), 0, 1);
        int titleWidth = Math.round(w < 315 ? Mth.clamp(w * .54f, 77f, 132.5f)
                : Mth.clamp(Math.min(w * .40f, h * .56f), 85f, 295f));
        int titleHeight = Math.round(titleWidth * verdict.height / (float) verdict.width);
        int captionGap = Math.round(Mth.clamp(h * .03f, 8.5f, 17.5f));
        float captionScale = Mth.clamp(w * .016f, 7, 14) / font.lineHeight;
        int titleX = (w - titleWidth) / 2;
        int titleY = Math.round((h * 1.014f - titleHeight - captionGap - font.lineHeight * captionScale) / 2);

        graphics.pose().pushPose();
        try {
            graphics.pose().scale(scale, scale, 1);
            TdmResultBackdrop.draw(graphics, w, h, ease(elapsedSeconds / INTRO_END), growth,
                    verdict.id, titleX, titleY, titleWidth, titleHeight);
            if (elapsedSeconds < SCORES_START) {
                int ink = verdict.ink;
                float opacity = ease((SCORES_START - elapsedSeconds) / .19);
                if (elapsedSeconds < INTRO_END) {
                    opacity = 0;
                    if (!reducedMotion && elapsedSeconds >= .4 && elapsedSeconds < .41) {
                        for (Flash flash : FLASHES) {
                            if (elapsedSeconds >= flash.start && elapsedSeconds < flash.end) {
                                opacity = flash.opacity;
                                if (flash.replacementInk != 0) ink = flash.replacementInk;
                                break;
                            }
                        }
                    }
                }
                if (opacity >= .016f) {
                    if (neutralWin) {
                        String winner = I18n.get("hud.codpattern.tdm.result." + winningTeam + "_win_title");
                        float textScale = Math.min(titleWidth / (float) Math.max(1, font.width(winner)),
                                titleHeight / (float) font.lineHeight);
                        text(graphics, font, winner, (w - font.width(winner) * textScale) / 2,
                                titleY + (titleHeight - font.lineHeight * textScale) / 2, textScale, ink, opacity);
                    } else {
                        float glyphOpacity = verdict == Verdict.DRAW && ink == verdict.ink ? opacity * .92f : opacity;
                        drawTitle(graphics, verdict, titleX, titleY, titleWidth, titleHeight, ink, glyphOpacity);
                    }
                    if (elapsedSeconds >= INTRO_END) {
                        String caption = neutralWin ? winningTeam.toUpperCase(java.util.Locale.ROOT) : verdict.caption;
                        drawCaption(graphics, font, w, titleY + titleHeight + captionGap,
                                captionScale, caption, verdict.accent, opacity);
                    }
                }
            } else {
                TdmSettlementScoreboard.draw(graphics, font, w, h, elapsedSeconds - SCORES_START,
                        homeScore, awayScore, isKortac, affiliated, localId, rosters);
            }
            float modeAlpha = ease((elapsedSeconds - INTRO_END) / .22);
            if (modeAlpha >= .016f && modeName != null) {
                text(graphics, font, modeName, w - Mth.clamp(w * .042f, 10, 42) - font.width(modeName),
                        Mth.clamp(h * .045f, 9, 25), 1, 0xc6d2d0, modeAlpha);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void drawTitle(GuiGraphics g, Verdict verdict, int x, int y, int w, int h, int ink, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor((ink >> 16 & 255) / 255f, (ink >> 8 & 255) / 255f, (ink & 255) / 255f, alpha);
        g.blit(verdict.texture, x, y, w, h, 0f, 0f, verdict.width, verdict.height, verdict.width, verdict.height);
        g.setColor(1, 1, 1, 1);
    }

    private static void drawCaption(GuiGraphics g, Font font, int w, int y, float size,
                                    String caption, int ink, float alpha) {
        float spacing = font.lineHeight * size * .36f;
        float length = font.width(caption) * size + (caption.length() - 1) * spacing;
        float x = (w - length) / 2;
        int gap = Math.round(Mth.clamp(w * .017f, 7, 17));
        int rail = Math.round(Mth.clamp(w * .05f, 15, 44));
        int mid = Math.round(y + font.lineHeight * size * .4f);
        int leftEnd = Math.round(x) - gap, rightStart = Math.round(x + length) + gap;
        int ruleColor = argb(ink, alpha * .7f);
        g.fill(leftEnd - rail, mid, leftEnd, mid + 1, ruleColor);
        g.fill(rightStart, mid, rightStart + rail, mid + 1, ruleColor);
        g.fill(leftEnd - 2, mid - 1, leftEnd, mid + 1, ruleColor);
        g.fill(rightStart, mid - 1, rightStart + 2, mid + 1, ruleColor);
        for (int i = 0; i < caption.length(); i++) {
            String ch = caption.substring(i, i + 1);
            text(g, font, ch, x, y, size, ink, alpha);
            x += font.width(ch) * size + spacing;
        }
    }

    static int argb(int rgb, float alpha) {
        return (Math.round(alpha * 255) << 24) | rgb;
    }

    static float ease(double value) {
        float t = (float) Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    static void text(GuiGraphics graphics, Font font, String text, float x, float y,
                     float scale, int rgb, float alpha) {
        // Minecraft's font promotes alpha 0..3 to opaque.
        if (alpha < .016f || text.isEmpty()) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, argb(rgb, alpha), false);
        graphics.pose().popPose();
    }
}
