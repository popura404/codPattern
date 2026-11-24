package com.cdp.codpattern.compat.tacz.client;

import com.cdp.codpattern.client.gui.screen.backpack.BackpackUi;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.modifier.IAttachmentModifier.DiagramsData;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class RefitStatsPanel {
    private static final String TACZ_STATS = "gui.tacz.gun_refit.property_diagrams.";
    private static final Set<String> SUMMARY = Set.of(TACZ_STATS + "damage", TACZ_STATS + "rpm", TACZ_STATS + "ads");
    private static final float TEXT_SCALE = .9f;
    private static final int GAP = 2;
    private static final int INSET = 7;
    private static final int SCROLLBAR_SPACE = 5;
    private static final int GOOD = 0xFF8FD9BE;
    private static final int BAD = 0xFFE8A093;

    record Row(Component title, Component value, double baseBar, double changeBar, int changeColor) {}
    private record Stat(Row row, boolean common, boolean changed) {}
    private record Reading(String text, double value, double baseBar, double endBar) {}

    private RefitStatsPanel() {}

    static List<Row> rows(ItemStack gun, boolean detailed, AttachmentType slot, @Nullable ItemStack candidate) {
        IGun item = IGun.getIGunOrNull(gun);
        if (item == null) return List.of();
        var index = TimelessAPI.getCommonGunIndex(item.getGunId(gun)).orElse(null);
        if (index == null) return List.of();
        GunData data = index.getGunData();
        boolean comparing = candidate != null && slot != AttachmentType.NONE;
        // Run both sides of a hover comparison through the same TaCZ calculation, on private stacks.
        ItemStack current = comparing ? gun.copy() : gun;
        ItemStack preview = current;
        if (comparing && !ItemStack.isSameItemSameTags(item.getAttachment(current, slot), candidate)) {
            preview = current.copy();
            if (candidate.isEmpty()) item.unloadAttachment(preview, slot);
            else item.installAttachment(preview, candidate.copy());
        }
        AttachmentCacheProperty before = properties(current, data);
        AttachmentCacheProperty after = preview == current ? before : properties(preview, data);

        var stats = new ArrayList<Stat>();
        if (detailed) {
            String mode = item.getFireMode(current).name().toLowerCase(Locale.ROOT);
            stats.add(new Stat(new Row(title("fire_mode"), title(mode), Double.NaN, 0, GOOD), false, false));
        }
        stats.add(ammoRow(current, preview, item, data, comparing));
        if (detailed) {
            float sprintTime = data.getSprintTime();
            stats.add(new Stat(new Row(title("sprint_time"), Component.literal(String.format(Locale.ROOT, "%.2fs", sprintTime)),
                    sprintTime / .5, 0, GOOD), false, false));
        }
        for (var modifier : AttachmentPropertyManager.getModifiers().values()) {
            var equipped = modifier.getPropertyDiagramsData(current, data, before);
            var projected = preview == current ? equipped : modifier.getPropertyDiagramsData(preview, data, after);
            var byTitle = new LinkedHashMap<String, DiagramsData>();
            if (comparing) equipped.forEach(metric -> byTitle.put(metric.titleKey(), metric));
            for (DiagramsData metric : projected) {
                boolean common = SUMMARY.contains(metric.titleKey());
                if (!comparing && !common && !detailed) continue;
                DiagramsData previous = byTitle.remove(metric.titleKey());
                Reading total = reading(metric, after);
                if (!comparing) stats.add(new Stat(metricRow(metric, total), common, false));
                else if (previous != null) stats.add(comparedMetric(previous, metric, reading(previous, before), total, common));
                else stats.add(changedReading(metric, "—", total.text(), common));
            }
            for (DiagramsData removed : byTitle.values()) {
                stats.add(changedReading(removed, reading(removed, before).text(), "—", SUMMARY.contains(removed.titleKey())));
            }
        }
        // Keep the usual order within each group. A recoil-only attachment must also be visible in the compact strip.
        if (comparing) stats.sort(Comparator.comparing(Stat::changed).reversed());
        return stats.stream().filter(stat -> detailed || stat.common || stat.changed)
                .limit(detailed ? Long.MAX_VALUE : 4).map(Stat::row).toList();
    }

    private static AttachmentCacheProperty properties(ItemStack gun, GunData data) {
        var player = Minecraft.getInstance().player;
        if (player != null && gun == player.getMainHandItem()) {
            // The refit ACK updates the player's TaCZ cache, including contributed property modifiers.
            var cache = IGunOperator.fromLivingEntity(player).getCacheProperty();
            if (cache != null) return cache;
        }
        var cache = new AttachmentCacheProperty();
        cache.eval(gun, data);
        return cache;
    }

    private static Stat ammoRow(ItemStack gun, ItemStack preview, IGun item, GunData data, boolean comparing) {
        Component label = title("ammo_capacity");
        if (item.useInventoryAmmo(gun)) {
            return new Stat(new Row(label, Component.translatableWithFallback("screen.codpattern.refit.inventory_ammo", "INV"),
                    1, 0, GOOD), true, false);
        }
        int chambered = item.hasBulletInBarrel(gun) && data.getBolt() != Bolt.OPEN_BOLT ? 1 : 0;
        int base = (comparing ? AttachmentDataUtils.getAmmoCountWithAttachment(gun, data) : data.getAmmoAmount()) + chambered;
        int capacity = AttachmentDataUtils.getAmmoCountWithAttachment(preview, data) + chambered;
        int change = capacity - base;
        int color = change < 0 ? BAD : GOOD;
        var value = Component.literal(Integer.toString(capacity));
        if (change != 0) value.append(Component.literal(String.format(Locale.ROOT, " (%+d)", change))
                .withStyle(style -> style.withColor(color)));
        return new Stat(new Row(label, value, base / 100.0, change / 100.0, color), true, comparing && change != 0);
    }

    private static Reading reading(DiagramsData metric, AttachmentCacheProperty cache) {
        String key = statKey(metric.titleKey());
        // Arcana's ADS diagram reports the scaled base in seconds and its separate equipment adjustment.
        boolean arcanaAds = key.equals("ads") && ModList.get().isLoaded("taczexpands");
        double absolute = switch (key) {
            case "ads" -> arcanaAds ? metric.defaultPercent() + metric.modifier().doubleValue() : cache.getCache(GunProperties.ADS_TIME);
            case "ammo_speed" -> cache.getCache(GunProperties.AMMO_SPEED);
            case "armor_ignore" -> cache.getCache(GunProperties.ARMOR_IGNORE);
            case "damage" -> cache.getCache(GunProperties.DAMAGE).getFirst().getDamage();
            case "effective_range" -> Math.min(cache.getCache(GunProperties.EFFECTIVE_RANGE), 1024f);
            case "head_shot" -> cache.getCache(GunProperties.HEADSHOT_MULTIPLIER);
            case "knockback" -> cache.getCache(GunProperties.KNOCKBACK);
            case "pierce" -> cache.getCache(GunProperties.PIERCE);
            case "aim_inaccuracy" -> Mth.clamp(1.0 - cache.getCache(GunProperties.INACCURACY).get(InaccuracyType.AIM), 0.0, 1.0);
            default -> Double.NaN;
        };
        double reference = switch (key) {
            case "ads" -> .5;
            case "ammo_speed" -> 600;
            case "armor_ignore", "knockback", "aim_inaccuracy" -> 1;
            case "damage" -> 50;
            case "effective_range" -> 100;
            case "head_shot", "pierce" -> 5;
            default -> Double.NaN;
        };
        // TaCZ's range strings contain the unmodified value, so use its final cached range instead.
        String text = key.equals("effective_range") ? String.format(Locale.getDefault(Locale.Category.FORMAT), "%.1fm", absolute)
                : arcanaAds ? String.format(Locale.getDefault(Locale.Category.FORMAT), "%.2fs", absolute)
                : ChatFormatting.stripFormatting(metric.defaultString());
        double baseBar = arcanaAds ? metric.defaultPercent() / reference : metric.defaultPercent();
        double endBar = Double.isFinite(absolute) ? absolute / reference : baseBar + metric.modifierPercent();
        return new Reading(text, absolute, baseBar, endBar);
    }

    private static Stat changedReading(DiagramsData metric, String before, String after, boolean common) {
        Row row = new Row(Component.translatable(metric.titleKey()), Component.literal(before + " → " + after), Double.NaN, 0, GOOD);
        return new Stat(row, common, true);
    }

    private static Stat comparedMetric(DiagramsData before, DiagramsData after, Reading previous, Reading total, boolean common) {
        // Arcana attachments can switch the base ammunition as well as modifying its attributes.
        double delta = Double.isFinite(previous.value()) && Double.isFinite(total.value()) ? total.value() - previous.value()
                : after.modifier().doubleValue() - before.modifier().doubleValue();
        String difference = formatDifference(after.titleKey(), delta);
        // Third-party diagrams choose their own units. Preserve their formatted endpoints when those units are unknown.
        if (difference == null && !previous.text().equals(total.text())) return changedReading(after, previous.text(), total.text(), common);
        boolean changed = difference != null && !difference.isEmpty();
        int color = (delta > 0) == after.positivelyBetter() ? GOOD : BAD;
        var value = Component.literal(total.text());
        if (changed) {
            value.append(Component.literal(" (" + difference + ")").withStyle(style -> style.withColor(color)));
        }
        double start = Mth.clamp(previous.endBar(), 0, 1);
        double end = Mth.clamp(total.endBar(), 0, 1);
        return new Stat(new Row(Component.translatable(after.titleKey()), value, start, end - start, color),
                common, changed);
    }

    @Nullable private static String formatDifference(String key, double delta) {
        String metric = statKey(key);
        boolean percent = metric.equals("armor_ignore") || metric.equals("aim_inaccuracy");
        int digits = switch (metric) {
            case "rpm", "pierce", "ammo_speed" -> 0;
            case "armor_ignore", "aim_inaccuracy", "effective_range", "head_shot" -> 1;
            case "damage", "ads", "weight", "knockback", "pitch", "yaw", "hipfire_inaccuracy",
                    "sneak_inaccuracy", "lie_inaccuracy" -> 2;
            default -> -1;
        };
        if (digits < 0) return null;
        if (!Double.isFinite(delta) || delta == 0) return "";
        double amount = percent ? delta * 100 : delta;
        if (Math.abs(amount) < .5 / Math.pow(10, digits)) return "";
        return String.format(Locale.getDefault(Locale.Category.FORMAT), "%+." + digits + "f", amount) + (percent ? "%" : "");
    }

    private static Row metricRow(DiagramsData metric, Reading total) {
        double modifier = metric.modifier().doubleValue();
        int color = (modifier > 0) == metric.positivelyBetter() ? GOOD : BAD;
        String raw = modifier > 0 ? metric.positivelyString() : modifier < 0 ? metric.negativeString() : metric.defaultString();
        int correction = raw.indexOf(ChatFormatting.PREFIX_CODE);
        Component value;
        if (correction >= 0 && modifier != 0) {
            value = Component.literal(total.text() + " ")
                    .append(Component.literal(ChatFormatting.stripFormatting(raw.substring(correction)))
                            .withStyle(style -> style.withColor(color)));
        } else {
            value = Component.literal(total.text());
        }
        double base = Mth.clamp(total.baseBar(), 0, 1);
        double end = Mth.clamp(total.endBar(), 0, 1);
        return new Row(Component.translatable(metric.titleKey()), value, base, end - base, color);
    }

    private static String statKey(String titleKey) {
        return titleKey.startsWith(TACZ_STATS) ? titleKey.substring(TACZ_STATS.length()) : "";
    }

    private static Component title(String key) {
        return Component.translatable(TACZ_STATS + key);
    }

    static int maxScroll(List<Row> rows, int width, int height) {
        int content = rows.stream().mapToInt(row -> rowHeight(row, width) + GAP).sum() - GAP;
        return Math.max(0, content - height);
    }

    static void renderSummary(GuiGraphics g, List<Row> rows, int x, int y, int width, int height, float scale) {
        if (width < 1 || height < 1) return;
        if (rows.isEmpty()) {
            BackpackUi.text(g, Component.translatable("screen.codpattern.refit.stats_unavailable"), x, y,
                    width, .9f * scale, BackpackUi.MUTED);
            return;
        }
        // Use the same physical reference scale as the weapon caption, including at high GUI scales.
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1);
            int w = (int) (width / scale), h = (int) (height / scale);
            BackpackUi.horizontalGradient(g, 0, 0, w, h, 0xE81B2A2D, 0xE8293A3D);
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                int cell = i * w / rows.size();
                int left = cell + 5, right = (i + 1) * w / rows.size() - 5;
                int textWidth = right - left;
                if (i > 0) g.fill(cell, 3, cell + 1, h - 3, 0xFF425456);
                float labelScale = Math.min(.88f, textWidth / (float) Math.max(1, BackpackUi.textWidth(row.title(), 1f)));
                float valueScale = Math.min(1.18f, textWidth / (float) Math.max(1, BackpackUi.textWidth(row.value(), 1f)));
                BackpackUi.text(g, row.title(), left, 2, textWidth, labelScale, BackpackUi.MUTED);
                BackpackUi.text(g, row.value(), left, h - 5 - BackpackUi.lineHeight(valueScale), textWidth, valueScale, BackpackUi.INK);
                drawBar(g, row, left, right, h - 3);
            }
        } finally {
            g.pose().popPose();
        }
    }

    static void render(GuiGraphics g, List<Row> rows, int x, int y, int width, int height, int scroll) {
        if (width <= 0 || height <= 0) return;
        if (rows.isEmpty()) {
            BackpackUi.text(g, Component.translatableWithFallback("screen.codpattern.refit.stats_unavailable", "Weapon data unavailable"),
                    x + INSET, y + 4, width - 2 * INSET, TEXT_SCALE, BackpackUi.MUTED);
            return;
        }

        int max = maxScroll(rows, width, height);
        int offset = Mth.clamp(scroll, 0, max);
        int bottom = y + height;
        int top = y - offset;
        g.enableScissor(x, y, x + width, bottom);
        try {
            for (Row row : rows) {
                int rowHeight = rowHeight(row, width);
                if (top + rowHeight > y) renderRow(g, row, x, top, width, rowHeight);
                top += rowHeight + GAP;
                if (top >= bottom) break;
            }
        } finally {
            g.disableScissor();
        }
        if (max > 0) {
            int thumb = Math.min(height, Math.max(8, (int) ((double) height * height / (height + max))));
            int thumbY = y + (int) Math.round((height - thumb) * (double) offset / max);
            int right = x + width;
            g.fill(right - 2, y, right, bottom, 0xFF394F50);
            g.fill(right - 2, thumbY, right, thumbY + thumb, 0xFF87B8AF);
        }
    }

    private static boolean wraps(Row row, int width) {
        return BackpackUi.textWidth(row.title(), TEXT_SCALE) + BackpackUi.textWidth(row.value(), TEXT_SCALE) + 8
                > width - 2 * INSET - SCROLLBAR_SPACE;
    }

    private static int rowHeight(Row row, int width) {
        return BackpackUi.lineHeight(TEXT_SCALE) * (wraps(row, width) ? 2 : 1) + 9;
    }

    private static void renderRow(GuiGraphics g, Row row, int x, int y, int width, int height) {
        int bodyWidth = width - SCROLLBAR_SPACE;
        int left = x + INSET, right = x + bodyWidth - INSET;
        int textWidth = right - left;
        int valueWidth = Math.min(BackpackUi.textWidth(row.value(), TEXT_SCALE), textWidth);
        boolean wrap = wraps(row, width);
        int titleWidth = wrap ? textWidth : textWidth - valueWidth - 8;
        int textY = y + 3;
        int valueY = wrap ? textY + BackpackUi.lineHeight(TEXT_SCALE) : textY;
        BackpackUi.horizontalGradient(g, x, y, bodyWidth, height, 0xED1C2A2D, 0xE82B3D40);
        BackpackUi.text(g, row.title(), left, textY, titleWidth, TEXT_SCALE, BackpackUi.MUTED);
        BackpackUi.text(g, row.value(), right - valueWidth, valueY, valueWidth, TEXT_SCALE, BackpackUi.INK);
        drawBar(g, row, left, right, y + height - 4);
    }

    private static void drawBar(GuiGraphics g, Row row, int left, int right, int y) {
        if (!Double.isFinite(row.baseBar()) || !Double.isFinite(row.changeBar()) || right <= left) return;
        double base = Mth.clamp(row.baseBar(), 0, 1);
        int baseline = left + (int) Math.round((right - left) * base);
        int current = left + (int) Math.round((right - left) * Mth.clamp(row.baseBar() + row.changeBar(), 0, 1));
        g.fill(left, y, right, y + 2, 0xFF364A4C);
        g.fill(left, y, baseline, y + 2, 0xFF719A94);
        if (current != baseline) g.fill(Math.min(current, baseline), y, Math.max(current, baseline), y + 2, row.changeColor());
    }
}
