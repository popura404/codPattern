package com.cdp.codpattern.client.gui.screen.backpack;

import com.cdp.codpattern.app.backpack.service.BackpackNamespaceFilter;
import com.cdp.codpattern.compat.lrtactical.LrTacticalClientApi;
import com.cdp.codpattern.compat.tacz.client.TaczClientApi;
import com.cdp.codpattern.config.backpack.BackpackSlot;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Shared picker categories and header geometry, including filtering and wrapped tabs. */
public final class WeaponCatalog {
    private static final int TAB_PADDING = 10;
    private static final int TAB_GAP = 4;

    private WeaponCatalog() {}

    public static Map<String, List<ItemStack>> load(BackpackSlot slot, WeaponFilterConfig filter) {
        List<String> tabs;
        if (slot.isGunSlot()) {
            tabs = filter == null ? List.of() : filter.getGunTabs(slot == BackpackSlot.PRIMARY);
        } else if (slot == BackpackSlot.MELEE) {
            tabs = List.of("melee");
        } else {
            tabs = filter != null && !filter.isThrowablesEnabled() ? List.of() : List.of("throwable");
        }

        var catalog = new LinkedHashMap<String, List<ItemStack>>();
        for (String tab : tabs) {
            List<ItemStack> raw = switch (tab) {
                case "melee" -> LrTacticalClientApi.fillLrItemCategory(true);
                case "throwable" -> LrTacticalClientApi.fillLrItemCategory(false);
                default -> TaczClientApi.fillGunItemCategory(tab);
            };
            List<ItemStack> items = raw.stream().filter(stack -> !stack.isEmpty())
                    .filter(stack -> !BackpackNamespaceFilter.isBlocked(filter, stack,
                            BuiltInRegistries.ITEM.getKey(stack.getItem())))
                    .toList();
            if (!items.isEmpty()) catalog.put(tab, items);
        }
        return catalog;
    }

    public static Component categoryName(BackpackSlot slot, String tab) {
        return switch (tab) {
            case "" -> BackpackUi.slotLabel(slot);
            case "melee" -> Component.translatable("lrtactical.type.melee.name");
            case "throwable" -> Component.translatable("screen.codpattern.weapon.throwable");
            default -> gunTabName(tab);
        };
    }

    private static Component gunTabName(String tab) {
        return Component.translatable("tacz.type." + tab + ".name");
    }

    public record CardColumn(int left, int width) {
        public static CardColumn forCanvas(int width) {
            return new CardColumn(Math.round(width * .038f),
                    BackpackUi.loadoutCardWidth(BackpackUi.loadoutColumnWidth(width), BackpackSlot.PRIMARY));
        }
    }

    /** Match the picker's exact raster bounds even when a native button rounds to whole GUI units. */
    public record NativeCardColumn(CardColumn canvas, float scale) {
        public static NativeCardColumn current() {
            var view = BackpackCanvasScreen.Viewport.current();
            return new NativeCardColumn(CardColumn.forCanvas(view.width()), view.scale());
        }

        public int left() { return Mth.floor(canvas.left() * scale); }

        public int width() { return Mth.ceil((canvas.left() + canvas.width()) * scale) - left(); }
    }

    public static Header header(int width, int height, int categoryCount, WeaponFilterConfig filter) {
        int margin = CardColumn.forCanvas(width).left();
        int titleY = Math.round(height * .035f);
        int tabY = titleY + Math.max(22, height / 15);
        if (categoryCount <= 1) return new Header(margin, titleY, tabY, 0, 0, 1, tabY);

        int available = width - 2 * margin;
        // Both gun pickers use the longest configured category in the active language.
        int longest = Stream.concat(filter.getGunTabs(true).stream(), filter.getGunTabs(false).stream())
                .filter(TaczClientApi::isGunTab)
                .map(WeaponCatalog::gunTabName)
                .mapToInt(label -> BackpackUi.textWidth(label, 1f)).max().orElse(0);
        int tabWidth = Math.min(available, longest + 2 * TAB_PADDING);
        int tabHeight = BackpackUi.lineHeight(1f) + 12;
        int columns = Math.max(1, (available + TAB_GAP) / (tabWidth + TAB_GAP));
        int rows = Mth.positiveCeilDiv(categoryCount, columns);
        int headingY = tabY + rows * (tabHeight + TAB_GAP) + 1;
        return new Header(margin, titleY, tabY, tabWidth, tabHeight, columns, headingY);
    }

    public static float tabLabelScale(int tabWidth, int textWidth) {
        return Math.min(1f, (tabWidth - 2f * TAB_PADDING) / Math.max(1, textWidth));
    }

    public record Header(int margin, int titleY, int tabY, int tabWidth, int tabHeight, int columns, int headingY) {
        public ScreenRectangle tabBounds(int index) {
            int x = margin + index % columns * (tabWidth + TAB_GAP);
            int y = tabY + index / columns * (tabHeight + TAB_GAP);
            return new ScreenRectangle(x, y, tabWidth, tabHeight);
        }
    }
}
