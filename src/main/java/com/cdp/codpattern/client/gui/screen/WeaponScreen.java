package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.client.gui.BackNavigation;
import com.cdp.codpattern.client.gui.screen.backpack.BackpackCanvasScreen;
import com.cdp.codpattern.client.gui.screen.backpack.BackpackEquipmentRenderer;
import com.cdp.codpattern.client.gui.screen.backpack.BackpackUi;
import com.cdp.codpattern.client.gui.screen.backpack.WeaponCatalog;
import com.cdp.codpattern.client.gui.screen.backpack.WeaponPreview;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackItemStackFactory;
import com.cdp.codpattern.config.backpack.BackpackSlot;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterClientCache;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.network.UpdateWeaponPacket;
import net.minecraft.Util;
import net.minecraft.client.InputType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.cdp.codpattern.client.gui.screen.backpack.BackpackUi.LOADOUT_CARD_GAP;
import static com.cdp.codpattern.client.gui.screen.backpack.BackpackUi.lineHeight;
import static com.cdp.codpattern.client.gui.screen.backpack.BackpackUi.px;
import static com.cdp.codpattern.client.gui.screen.backpack.BackpackUi.textWidth;

public class WeaponScreen extends BackpackCanvasScreen {
    private static final Component EQUIPPED_LABEL = Component.translatable("screen.codpattern.backpack.chip.equipped");
    private static final int CATALOG_RULE = 0xFF485E5E;
    private static final int SOFT_ACCENT = 0xFF8CB9B4;
    private static final int SCROLL_DURATION_MS = 160;
    private final Screen parent;
    private final BackpackConfig.Backpack backpack;
    private final int bagId;
    private final BackpackSlot slot;
    private final Map<String, List<ItemStack>> catalog = new LinkedHashMap<>();
    private final List<CandidateButton> rows = new ArrayList<>();
    private WeaponFilterConfig filter;
    private WeaponPreview previewStage;
    private ItemStack equipped;
    private String category = "";
    private List<ItemStack> candidates = List.of();
    private int preview;
    private int equippedIndex;
    private double scrollPosition, scrollTarget, scrollStart;
    private long scrollStartedAt;
    private int margin;
    private int titleY;
    private int listWidth;
    private int listY;
    private int listBottom;
    private int rowHeight;
    private double lastPointerX, lastPointerY;

    public WeaponScreen(Screen parent, BackpackConfig.Backpack backpack, int bagId, BackpackSlot slot) {
        super(Component.translatable("screen.codpattern.weapon.select_slot", BackpackUi.slotLabel(slot)));
        this.parent = parent;
        this.backpack = backpack;
        this.bagId = bagId;
        this.slot = slot;
    }

    public WeaponScreen(WeaponMenuScreen parent, BackpackConfig.Backpack backpack, Integer bagId, String slot) {
        this(parent, backpack, bagId, BackpackSlot.fromMenuKey(slot));
    }

    @Override
    protected void init() {
        super.init();
        // Every picker uses the backpack's primary-weapon card, including melee and throwables.
        listWidth = WeaponCatalog.CardColumn.forCanvas(canvasWidth).width();
        rowHeight = BackpackUi.loadoutCardHeight(BackpackSlot.PRIMARY);
        lastPointerX = pointerCanvasX();
        lastPointerY = pointerCanvasY();
        equipped = BackpackItemStackFactory.create(backpack.getItem(slot));
        loadCatalog();
        var header = WeaponCatalog.header(canvasWidth, canvasHeight, catalog.size(), filter);
        margin = header.margin();
        titleY = header.titleY();
        previewStage = WeaponPreview.layout(canvasWidth, canvasHeight, header);
        if (catalog.size() > 1) {
            addTabs(header);
        }
        listY = header.headingY() + lineHeight(1f) + px(7);
        var backBounds = BackNavigation.bounds(canvasWidth, canvasHeight);
        listBottom = backBounds.top() - px(4);
        chooseCategory(category);
        addRenderableWidget(BackNavigation.button(backBounds, this::onClose));
    }

    private void loadCatalog() {
        catalog.clear();
        filter = WeaponFilterClientCache.get();
        catalog.putAll(WeaponCatalog.load(slot, filter));
        if (!catalog.containsKey(category)) {
            category = catalog.entrySet().stream()
                    .filter(entry -> entry.getValue().stream().anyMatch(this::isEquipped))
                    .map(Map.Entry::getKey).findFirst()
                    .orElseGet(() -> catalog.keySet().stream().findFirst().orElse(""));
        }
    }

    private boolean isEquipped(ItemStack stack) {
        if (equipped.isEmpty() || !equipped.is(stack.getItem())) return false;
        if (slot.isGunSlot()) {
            var gunId = TaczGatewayProvider.gateway().resolveGunId(equipped);
            return gunId.isPresent() && gunId.equals(TaczGatewayProvider.gateway().resolveGunId(stack));
        }
        String key = slot == BackpackSlot.MELEE ? "MeleeWeaponId" : "ThrowableId";
        return equipped.hasTag() && stack.hasTag() && equipped.getTag().contains(key)
                && equipped.getTag().getString(key).equals(stack.getTag().getString(key));
    }

    private Component categoryName(String tab) {
        return WeaponCatalog.categoryName(slot, tab);
    }

    private void addTabs(WeaponCatalog.Header header) {
        int index = 0;
        for (String tab : catalog.keySet()) {
            var bounds = header.tabBounds(index++);
            addRenderableWidget(BackpackUi.button(bounds.left(), bounds.top(), bounds.width(), bounds.height(), categoryName(tab),
                    () -> chooseCategory(tab), (g, b) -> drawCategoryTab(g, b, category.equals(tab))));
        }
    }

    private void drawCategoryTab(GuiGraphics g, Button b, boolean selected) {
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        boolean keyboard = minecraft.getLastInputType().isKeyboard();
        boolean hovered = !keyboard && b.isHovered();
        if (selected) {
            BackpackUi.horizontalGradient(g, x, y, w, h, 0xFFA5D8CF, 0xFF82BFB8);
            int center = x + w / 2;
            g.fill(center - 6, y + h - 3, center + 6, y + h - 1, 0xFF325A56);
        } else {
            BackpackUi.horizontalGradient(g, x, y, w, h, 0xFF243236, 0xFF2D4043);
            g.fill(x, y + h - 1, x + w, y + h, 0xFF52686A);
        }
        if (hovered) g.renderOutline(x, y, w, h, selected ? 0xFFE0FAF3 : 0xFF8FB6B3);
        if (keyboard && b.isFocused()) {
            g.renderOutline(x + 2, y + 2, w - 4, h - 4, selected ? 0xFF355E59 : 0xFFB7E5DE);
        }

        Component label = b.getMessage();
        int naturalWidth = textWidth(label, 1f);
        // Only an individual caption wider than the entire window needs smaller type.
        float scale = WeaponCatalog.tabLabelScale(w, naturalWidth);
        int labelX = x + (w - textWidth(label, scale)) / 2;
        BackpackUi.text(g, label, labelX, y + (h - lineHeight(scale)) / 2,
                x + w - labelX, scale, selected ? BackpackUi.PRIMARY_INK : BackpackUi.INK);
    }

    private void chooseCategory(String tab) {
        category = tab;
        candidates = catalog.getOrDefault(tab, List.of());
        equippedIndex = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (isEquipped(candidates.get(i))) {
                equippedIndex = i;
                break;
            }
        }
        preview = Math.max(0, equippedIndex);
        if (getFocused() instanceof CandidateButton) setFocused(null);
        rows.forEach(this::removeWidget);
        rows.clear();
        scrollPosition = 0;
        showPreviewRow();
    }

    private int rowStep() {
        return rowHeight + LOADOUT_CARD_GAP;
    }

    private int maxScroll() {
        int contentHeight = candidates.size() * rowStep() - LOADOUT_CARD_GAP;
        return Math.max(0, contentHeight - (listBottom - listY));
    }

    private void showPreviewRow() {
        int top = preview * rowStep();
        int bottom = top + rowHeight;
        snapScroll(Mth.clamp(scrollPosition, bottom - (listBottom - listY), top));
        if (!rows.isEmpty() && minecraft.getLastInputType().isKeyboard()) {
            setFocused(rows.get(preview - rows.get(0).index));
        }
    }

    private void snapScroll(double position) {
        scrollPosition = scrollTarget = Mth.clamp(position, 0, maxScroll());
        updateRows();
    }

    private int scrollPixel() {
        return (int) Math.round(scrollPosition);
    }

    private void advanceScroll(long now) {
        if (scrollPosition == scrollTarget) return;
        int previousPixel = scrollPixel();
        double remaining = 1 - Math.min(1, (now - scrollStartedAt) / (double) SCROLL_DURATION_MS);
        scrollPosition = scrollTarget + (scrollStart - scrollTarget) * remaining * remaining * remaining;
        if (scrollPixel() != previousPixel) {
            updateRows();
            if (minecraft.getLastInputType() == InputType.MOUSE) {
                previewUnderPointer(lastPointerX, lastPointerY);
            }
        }
    }

    private void updateRows() {
        int step = rowStep();
        int pixels = scrollPixel();
        int first = Math.min(candidates.size(), (pixels + LOADOUT_CARD_GAP) / step);
        int end = Math.min(candidates.size(), Mth.positiveCeilDiv(pixels + listBottom - listY, step));
        if (rows.size() != end - first || (!rows.isEmpty() && rows.get(0).index != first)) {
            if (getFocused() instanceof CandidateButton) setFocused(null);
            rows.forEach(this::removeWidget);
            rows.clear();
            for (int index = first; index < end; index++) {
                rows.add(addWidget(new CandidateButton(index, listY + index * step - pixels)));
            }
        } else {
            for (CandidateButton row : rows) {
                row.setY(listY + row.index * step - pixels);
            }
        }
    }

    private final class CandidateButton extends Button {
        private final int index;

        private CandidateButton(int index, int y) {
            super(margin, y, listWidth, rowHeight, BackpackEquipmentRenderer.displayName(candidates.get(index)),
                    button -> equip(candidates.get(index)), DEFAULT_NARRATION);
            this.index = index;
        }

        @Override
        public boolean isMouseOver(double x, double y) {
            return y >= listY && y < listBottom && super.isMouseOver(x, y);
        }

        @Override
        protected boolean clicked(double x, double y) {
            return isMouseOver(x, y);
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean selected = preview == index;
            BackpackUi.candidateRow(graphics, getX(), getY(), getWidth(), getHeight(), slot, index, selected);
            int padX = px(8);
            int padY = px(4);
            float nameScale = getWidth() >= px(145) ? 1.25f : 1f;
            int nameHeight = lineHeight(nameScale);
            int captionY = getY() + getHeight() - padY - nameHeight;
            int nameWidth = getWidth() - 2 * padX;
            int badgeWidth = index == equippedIndex ? Math.min(nameWidth / 2, textWidth(EQUIPPED_LABEL, 1f)) : 0;
            int artWidthPercent = slot.isGunSlot() ? 58 : 48;
            BackpackEquipmentRenderer.renderCardIcon(graphics, candidates.get(index), getX(), getY(),
                    getWidth(), getHeight(), artWidthPercent, nameHeight + padY);
            if (badgeWidth > 0) {
                nameWidth -= badgeWidth + padX;
                int badgeY = captionY + nameHeight - lineHeight(1f);
                BackpackUi.text(graphics, EQUIPPED_LABEL, getX() + getWidth() - padX - badgeWidth,
                        badgeY, badgeWidth, 1f, BackpackUi.JOINED_MINT);
            }
            BackpackUi.text(graphics, getMessage(), getX() + padX, captionY, nameWidth, nameScale, BackpackUi.INK);
        }
    }

    private void equip(ItemStack stack) {
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String nbt = stack.hasTag() ? stack.getTag().toString() : "";
        ModNetworkChannel.sendToServer(new UpdateWeaponPacket(bagId, slot.key(), item, nbt));
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (filter != WeaponFilterClientCache.get()) rebuildWidgets();
    }

    @Override
    protected void renderCanvas(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        advanceScroll(Util.getMillis());
        BackpackUi.title(graphics, margin, titleY, canvasWidth - margin * 2, title);
        int headingY = listY - lineHeight(1f) - px(7);
        int right = margin + listWidth;
        int headingWidth = listWidth;
        if (!candidates.isEmpty()) {
            Component count = Component.translatable("screen.codpattern.weapon.count", candidates.size());
            int countWidth = Math.min(listWidth / 2, textWidth(count, 1f));
            headingWidth -= countWidth + px(6);
            BackpackUi.text(graphics, count, right - countWidth, headingY, countWidth, 1f, BackpackUi.MUTED);
            drawPreview(graphics);
        } else {
            BackpackUi.text(graphics, Component.translatable("screen.codpattern.weapon.empty"),
                    margin + px(4), listY + px(6), listWidth - px(6), 1f, BackpackUi.MUTED);
        }
        BackpackUi.text(graphics, categoryName(category), margin, headingY, headingWidth, 1f, BackpackUi.INK);
        int ruleY = headingY + lineHeight(1f) + px(4);
        graphics.fill(margin, ruleY, right, ruleY + 1, CATALOG_RULE);
        int max = maxScroll();
        if (max > 0) {
            int track = listBottom - listY;
            int thumb = Math.max(px(5), (int) Math.round((double) track * track / (max + track)));
            int top = listY + (int) Math.round((track - thumb) * scrollPosition / max);
            int scrollX = right + LOADOUT_CARD_GAP;
            graphics.fill(scrollX, listY, scrollX + LOADOUT_CARD_GAP, listBottom, CATALOG_RULE);
            graphics.fill(scrollX, top, scrollX + LOADOUT_CARD_GAP, top + thumb, SOFT_ACCENT);
        }
        if (!rows.isEmpty()) {
            int hoverY = mouseY >= listY && mouseY < listBottom ? mouseY : -1;
            renderCanvasClipped(graphics, margin, listY, right, listBottom, () -> {
                for (CandidateButton row : rows) row.render(graphics, mouseX, hoverY, partialTick);
            });
        }
        renderCanvasWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPreview(GuiGraphics graphics) {
        previewStage.render(graphics, slot, candidates.get(preview));
    }

    @Override
    protected void canvasMouseMoved(double mouseX, double mouseY) {
        if (mouseX == lastPointerX && mouseY == lastPointerY) return;
        lastPointerX = mouseX;
        lastPointerY = mouseY;
        super.canvasMouseMoved(mouseX, mouseY);
        minecraft.setLastInputType(InputType.MOUSE);
        previewUnderPointer(mouseX, mouseY);
    }

    private void previewUnderPointer(double x, double y) {
        for (CandidateButton row : rows) {
            if (row.isMouseOver(x, y)) {
                preview = row.index;
                break;
            }
        }
    }

    @Override
    protected boolean canvasMouseScrolled(double x, double y, double delta) {
        int right = margin + listWidth + 2 * LOADOUT_CARD_GAP;
        if (x >= margin && x <= right && y >= listY && y < listBottom && maxScroll() > 0 && delta != 0) {
            double target = Mth.clamp(scrollTarget - delta * rowStep(), 0, maxScroll());
            if (target != scrollTarget) {
                scrollStart = scrollPosition;
                scrollTarget = target;
                scrollStartedAt = Util.getMillis();
            }
            minecraft.setLastInputType(InputType.MOUSE);
            lastPointerX = x;
            lastPointerY = y;
            if (getFocused() instanceof CandidateButton) setFocused(null);
            previewUnderPointer(x, y);
            return true;
        }
        return super.canvasMouseScrolled(x, y, delta);
    }

    @Override
    public void setFocused(GuiEventListener listener) {
        super.setFocused(listener);
        if (listener instanceof CandidateButton row && minecraft.getLastInputType().isKeyboard()) {
            preview = row.index;
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.setLastInputType(InputType.KEYBOARD_TAB);
            onClose();
            return true;
        }
        if (!candidates.isEmpty() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            preview = Mth.clamp(preview + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, candidates.size() - 1);
            showPreviewRow();
            return true;
        }
        if (catalog.size() > 1 && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            List<String> tabs = List.copyOf(catalog.keySet());
            int next = tabs.indexOf(category) + (key == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            chooseCategory(tabs.get(Math.floorMod(next, tabs.size())));
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            snapScroll(scrollPixel());
            boolean handled = super.keyPressed(key, scanCode, modifiers);
            if (getFocused() instanceof CandidateButton row) {
                preview = row.index;
                showPreviewRow();
            }
            return handled;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
}
