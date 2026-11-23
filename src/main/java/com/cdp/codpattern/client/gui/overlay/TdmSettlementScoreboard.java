package com.cdp.codpattern.client.gui.overlay;

import com.cdp.codpattern.app.tdm.model.TdmTeamNames;
import com.cdp.codpattern.app.tdm.service.TdmSettlementTiming;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.cdp.codpattern.client.gui.overlay.TdmSettlementRenderer.*;

final class TdmSettlementScoreboard {
    private static final String LABEL = "hud.codpattern.tdm.result.column.";
    private static final int BODY = 0xe0e9e7, HEADER = 0xb1c1c2;
    private static final Comparator<PlayerInfo> RANK = Comparator.comparingInt(PlayerInfo::kills).reversed()
            .thenComparingInt(PlayerInfo::deaths)
            .thenComparing(Comparator.comparingInt(PlayerInfo::maxKillStreak).reversed())
            .thenComparing(PlayerInfo::name, String.CASE_INSENSITIVE_ORDER).thenComparing(PlayerInfo::uuid);

    private record Row(UUID id, String name, String kills, String deaths, String kd, String compact, String streak) {
        Row(PlayerInfo p) {
            this(p.uuid(), p.name(), Integer.toString(p.kills()), Integer.toString(p.deaths()),
                    String.format(Locale.ROOT, "%.2f", p.kills() / (double) Math.max(1, p.deaths())),
                    p.kills() + " / " + p.deaths(), Integer.toString(p.maxKillStreak()));
        }
    }

    private static Map<String, List<PlayerInfo>> lastRoster = Map.of();
    private static List<Row> kortac = List.of(), specgru = List.of();

    private TdmSettlementScoreboard() {}

    static void draw(GuiGraphics g, Font font, int w, int h, double age, int homeScore, int awayScore,
                     boolean homeIsKortac, boolean affiliated, UUID localId, Map<String, List<PlayerInfo>> rosters) {
        float alpha = ease(age / .25);
        if (alpha < .016f) return;
        if (!lastRoster.equals(rosters)) {
            kortac = rows(rosters, TdmTeamNames.KORTAC);
            specgru = rows(rosters, TdmTeamNames.SPECGRU);
            lastRoster = rosters;
        }

        int margin = w < 315 ? 6 : Math.round(Mth.clamp(w * .042f, 10, 42));
        int reportW = Math.min(540, w - 2 * margin);
        int gap = w < 315 ? 3 : Math.round(Mth.clamp(w * .021f, 5, 18));
        int boardW = (reportW - gap) / 2;
        boolean compact = boardW < 153;
        boolean shortScreen = h <= 290;
        int scoreGap = shortScreen ? 6 : Math.round(Mth.clamp(h * .048f, 9, 31));
        int headerH = shortScreen ? 12 : Math.round(Mth.clamp(h * .038f, 12, 20));
        int preferredRow = shortScreen ? 13 : Math.round(Mth.clamp(h * .046f, 14, 25));

        String left = Integer.toString(homeScore), right = Integer.toString(awayScore);
        float scoreHeight = shortScreen ? 26f : Mth.clamp(Math.min(w * .107f, h * .18f), 28, 83);
        float scoreScale = Math.min(scoreHeight / 8f, boardW * .61f / Math.max(1, Math.max(font.width(left), font.width(right))));
        int numberH = Math.round(scoreScale * 8);
        int topMargin = Math.max(21, Math.round(h * .085f)), bottomMargin = Math.max(5, h / 35);
        int available = Math.max(1, h - topMargin - bottomMargin - numberH - scoreGap - headerH);
        int totalRows = Math.max(1, Math.max(kortac.size(), specgru.size()));
        int rowH = Mth.clamp(available / totalRows, font.lineHeight + 4, preferredRow);
        int pageSize = Math.max(1, available / rowH);
        int pages = (totalRows + pageSize - 1) / pageSize;
        int page = Math.min(pages - 1, (int) (age * pages / (TdmSettlementTiming.SCOREBOARD_TICKS / 20.0)));
        int visibleRows = Math.min(totalRows, pageSize);
        int blockH = numberH + scoreGap + headerH + visibleRows * rowH;
        int y = topMargin + (h - topMargin - bottomMargin - blockH) / 2;
        int boardX = (w - reportW) / 2;
        int numberGap = Math.round(Mth.clamp(reportW * .025f, 6, 15));
        int middle = w / 2;
        float leftX = middle - numberGap - font.width(left) * scoreScale;
        float rightX = middle + numberGap;
        String home = homeIsKortac ? TdmTeamNames.KORTAC : TdmTeamNames.SPECGRU;
        String away = homeIsKortac ? TdmTeamNames.SPECGRU : TdmTeamNames.KORTAC;

        text(g, font, left, leftX, y, scoreScale, 0xc8eaff, alpha);
        text(g, font, right, rightX, y, scoreScale, 0xf1d1c8, alpha);
        int dot = Math.max(2, numberH / 18);
        for (int i = 1; i <= 2; i++) {
            int dotY = y + numberH * i / 3;
            g.fill(middle - dot / 2, dotY, middle - dot / 2 + dot, dotY + dot, argb(0x92a5a8, alpha));
        }
        faction(g, font, home, true, boardX, leftX - gap, y + numberH / 2f, w, alpha);
        faction(g, font, away, false, rightX + font.width(right) * scoreScale + gap,
                boardX + reportW, y + numberH / 2f, w, alpha);

        int boardY = y + numberH + scoreGap;
        int first = page * pageSize;
        board(g, font, boardX, boardY, boardW, headerH, rowH, compact, true,
                affiliated, localId, homeIsKortac ? kortac : specgru, first, visibleRows, alpha);
        board(g, font, boardX + boardW + gap, boardY, boardW, headerH, rowH, compact, false,
                affiliated, localId, homeIsKortac ? specgru : kortac, first, visibleRows, alpha);
    }

    private static List<Row> rows(Map<String, List<PlayerInfo>> rosters, String team) {
        return rosters.getOrDefault(team, List.of()).stream().sorted(RANK).map(Row::new).toList();
    }

    private static void faction(GuiGraphics g, Font font, String team, boolean home, float x0, float x1,
                                float centerY, int w, float alpha) {
        String code = team.toUpperCase(Locale.ROOT), name = I18n.get("hud.codpattern.tdm.team." + team);
        float available = Math.max(1, x1 - x0);
        float size = Math.min(Mth.clamp(w * .022f, 8, 19) / font.lineHeight, available / font.width(name));
        float small = Math.min(Mth.clamp(w * .0105f, 6, 9) / font.lineHeight, available / font.width(code));
        boolean bilingual = !code.equalsIgnoreCase(name);
        float linesH = font.lineHeight * size + (bilingual ? font.lineHeight * small + 2 : 0);
        float y = centerY - linesH / 2;
        if (bilingual) {
            text(g, font, code, home ? x1 - font.width(code) * small : x0, y, small, home ? 0xa8c4d0 : 0xd0b4ac, alpha);
            y += font.lineHeight * small + 2;
        }
        text(g, font, name, home ? x1 - font.width(name) * size : x0, y, size, 0xedf2ec, alpha);
    }

    private static void board(GuiGraphics g, Font font, int x, int y, int w, int headerH, int rowH,
                              boolean compact, boolean home, boolean affiliated, UUID localId,
                              List<Row> rows, int first, int pageRows, float alpha) {
        int count = Math.max(0, Math.min(pageRows, rows.size() - first));
        int lastY = y + headerH + Math.max(count, rows.isEmpty() ? 1 : 0) * rowH;
        g.fill(x, y, x + w, lastY, argb(0x10191c, .91f * alpha));
        g.fill(x, y, x + w, y + 1, argb(home ? 0x71979f : 0x9c7971, alpha));
        int pad = Math.max(3, w / 32), inner = w - 2 * pad;
        int nameEnd = x + pad + Math.round(inner * (compact ? .48f : .39f));
        int right = x + w - pad;
        int statW = (right - nameEnd) / (compact ? 2 : 4);
        // Keep CJK bitmap strokes on the reference pixel grid, including small HUD windows.
        float headingY = y + (headerH - font.lineHeight) / 2;
        String heading = I18n.get(LABEL + (!affiliated ? "players" : home ? "allies" : "opponents"));
        text(g, font, GuiTextHelper.ellipsize(font, heading, nameEnd - x - pad),
                x + pad, headingY, 1, HEADER, alpha);
        if (compact) {
            rightText(g, font, I18n.get(LABEL + "kills_deaths"), right - statW, headingY, statW, HEADER, alpha);
        } else {
            rightText(g, font, I18n.get(LABEL + "kills"), right - 3 * statW, headingY, statW, HEADER, alpha);
            rightText(g, font, I18n.get(LABEL + "deaths"), right - 2 * statW, headingY, statW, HEADER, alpha);
            rightText(g, font, "K/D", right - statW, headingY, statW, HEADER, alpha);
        }
        rightText(g, font, I18n.get(LABEL + "streak"), right, headingY, statW, HEADER, alpha);
        if (rows.isEmpty()) {
            text(g, font, I18n.get("hud.codpattern.tdm.result.no_player"), x + pad,
                    y + headerH + (rowH - font.lineHeight) / 2, 1, HEADER, alpha);
        }
        for (int i = 0; i < count; i++) {
            Row row = rows.get(first + i);
            int top = y + headerH + i * rowH;
            float ty = top + (rowH - font.lineHeight) / 2;
            boolean isLocal = row.id.equals(localId);
            if (isLocal) g.fill(x, top, x + w, top + rowH, argb(0x233d47, alpha));
            g.fill(x, top, x + w, top + 1, argb(0x93aaa6, .12f * alpha));
            String you = isLocal ? I18n.get(LABEL + "you") : "";
            int nameSpace = nameEnd - x - pad - (isLocal ? font.width(you) + 3 : 0);
            String name = GuiTextHelper.ellipsize(font, row.name, nameSpace);
            text(g, font, name, x + pad, ty, 1, BODY, alpha);
            if (isLocal) text(g, font, you, x + pad + font.width(name) + 2, ty, 1, 0xb5e2fb, alpha);
            if (compact) {
                rightText(g, font, row.compact, right - statW, ty, statW, BODY, alpha);
            } else {
                rightText(g, font, row.kills, right - 3 * statW, ty, statW, BODY, alpha);
                rightText(g, font, row.deaths, right - 2 * statW, ty, statW, BODY, alpha);
                rightText(g, font, row.kd, right - statW, ty, statW, BODY, alpha);
            }
            rightText(g, font, row.streak, right, ty, statW, BODY, alpha);
        }
    }

    private static void rightText(GuiGraphics g, Font font, String value, int right, float y, int cellWidth,
                                  int ink, float alpha) {
        float fit = Math.min(1, (cellWidth - 2f) / Math.max(1, font.width(value)));
        text(g, font, value, right - font.width(value) * fit, y, fit, ink, alpha);
    }
}
