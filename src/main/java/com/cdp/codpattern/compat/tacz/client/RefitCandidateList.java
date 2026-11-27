package com.cdp.codpattern.compat.tacz.client;

import com.cdp.codpattern.client.gui.screen.backpack.BackpackUi;
import com.cdp.codpattern.client.gui.screen.backpack.WeaponCatalog;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitUnloadButton;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Only visible rows are widgets. Real attachments retain their exact TaCZ inventory indices. */
final class RefitCandidateList {
    private static final int GAP = 2;
    private static final int SCROLL_MS = 160;

    private record Entry(int slot, ItemStack stack) {}
    private final List<Entry> entries = new ArrayList<>();
    private final List<Button> rows = new ArrayList<>();
    private final Consumer<AbstractWidget> add, remove, focus;
    private final Runnable returnToSlots;
    private AttachmentType type;
    private Inventory inventory;
    private Button.OnPress install;
    private RefitUnloadButton unload;
    private ScrollBar bar;
    private int left, top, width, bottom, rowHeight, barGap, barWidth, firstVisible;
    private int installedSlot = -1, lastPressedSlot = -1;
    private double scroll, target, start;
    private long scrollTime;

    RefitCandidateList(Consumer<AbstractWidget> add, Consumer<AbstractWidget> remove, Consumer<AbstractWidget> focus,
                       Runnable returnToSlots) {
        this.add = add;
        this.remove = remove;
        this.focus = focus;
        this.returnToSlots = returnToSlots;
    }

    void begin(AttachmentType nextType) {
        if (nextType != type) {
            scroll = target = start = 0;
        }
        type = nextType;
        // The native screen clears all children in init(), including our old rows.
        rows.clear();
        entries.clear();
        bar = null;
        install = null;
        inventory = null;
        unload = null;
        firstVisible = 0;
        installedSlot = -1;
    }

    void captureInstaller(Inventory source, Button.OnPress nativePress) {
        inventory = source;
        install = nativePress;
    }

    void layout(int x, int y, int w, int endY, int h, ItemStack gun, RefitUnloadButton nativeUnload) {
        unload = nativeUnload;
        left = x;
        top = y;
        width = w;
        float scale = WeaponCatalog.NativeCardColumn.current().scale();
        barGap = Math.max(1, Math.round(BackpackUi.LOADOUT_CARD_GAP * scale));
        barWidth = barGap;
        bottom = Math.max(y + 1, endY);
        rowHeight = h;
        var gunItem = IGun.getIGunOrNull(gun);
        if (install != null && gunItem != null) {
            var installed = unload == null ? ItemStack.EMPTY : gunItem.getAttachment(gun, type);
            var installedItem = IAttachment.getIAttachmentOrNull(installed);
            var installedId = installedItem == null ? null : installedItem.getAttachmentId(installed);
            int bestMatch = -1;
            for (int index = 0; index < inventory.getContainerSize(); index++) {
                var stack = inventory.getItem(index);
                var attachment = IAttachment.getIAttachmentOrNull(stack);
                if (attachment != null && attachment.getType(stack) == type && gunItem.allowAttachment(gun, stack)) {
                    entries.add(new Entry(index, stack));
                    if (installedId != null && installedId.equals(attachment.getAttachmentId(stack))) {
                        // Configured variants can share an ID. Keep the actual variant selected after an ACK.
                        int match = (ItemStack.isSameItemSameTags(installed, stack) ? 2 : 0) + (lastPressedSlot == index ? 1 : 0);
                        if (match > bestMatch) {
                            installedSlot = index;
                            bestMatch = match;
                        }
                    }
                }
            }
        }
        int max = maxScroll();
        scroll = Math.min(scroll, max);
        target = Math.min(target, max);
        start = Math.min(start, max);
        if (max > 0) {
            bar = new ScrollBar();
            add.accept(bar);
        }
        updateRows();
    }

    private int size() { return type == AttachmentType.NONE ? 0 : entries.size() + 1; }

    private int step() { return rowHeight + GAP; }

    private int maxScroll() {
        return Math.max(0, size() * step() - GAP - (bottom - top));
    }

    private boolean contains(double x, double y) {
        return x >= left && x < left + width + (bar == null ? 0 : barGap + barWidth) && y >= top && y < bottom;
    }

    boolean wheel(double x, double y, double delta) {
        if (bar == null || !contains(x, y) || delta == 0) return false;
        animateTo(target - delta * step() * 1.5);
        return true;
    }

    boolean key(int key, AbstractWidget focused) {
        int position = rows.indexOf(focused);
        if (position >= 0) {
            int current = firstVisible + position;
            int pageSize = Math.max(1, (bottom - top) / step());
            int index = switch (key) {
                case GLFW.GLFW_KEY_UP -> current - 1;
                case GLFW.GLFW_KEY_DOWN -> current + 1;
                case GLFW.GLFW_KEY_PAGE_UP -> Math.max(0, current - pageSize);
                case GLFW.GLFW_KEY_PAGE_DOWN -> Math.min(size() - 1, current + pageSize);
                case GLFW.GLFW_KEY_HOME -> 0;
                case GLFW.GLFW_KEY_END -> size() - 1;
                default -> -1;
            };
            if (index < 0 || index >= size()) return false;
            int y = index * step();
            scroll = target = start = Mth.clamp(scroll, Math.max(0, y + rowHeight - (bottom - top)), y);
            updateRows();
            focus.accept(rows.get(index - firstVisible));
            return true;
        }
        if (bar == null || focused != bar) return false;
        switch (key) {
            case GLFW.GLFW_KEY_HOME -> animateTo(0);
            case GLFW.GLFW_KEY_END -> animateTo(maxScroll());
            case GLFW.GLFW_KEY_PAGE_UP -> animateTo(target - (bottom - top - rowHeight));
            case GLFW.GLFW_KEY_PAGE_DOWN -> animateTo(target + (bottom - top - rowHeight));
            default -> { return false; }
        }
        return true;
    }

    private void animateTo(double next) {
        long now = Util.getMillis();
        advance(now);
        start = scroll;
        target = Mth.clamp(next, 0, maxScroll());
        scrollTime = now;
    }

    private void advance(long now) {
        if (scroll == target) return;
        double remaining = 1 - Mth.clamp((now - scrollTime) / (double) SCROLL_MS, 0, 1);
        scroll = target + (start - target) * remaining * remaining * remaining;
        updateRows();
    }

    private void updateRows() {
        int pixels = (int) Math.round(scroll);
        int first = Math.min(size(), (pixels + GAP) / step());
        int end = Math.min(size(), Mth.positiveCeilDiv(pixels + bottom - top, step()));
        if (rows.size() != end - first || firstVisible != first) {
            rows.forEach(remove);
            rows.clear();
            firstVisible = first;
            for (int i = first; i < end; i++) {
                Button row = i == 0 ? new NoAttachmentRow() : new Row(entries.get(i - 1));
                rows.add(row);
                add.accept(row);
            }
        }
        for (int i = 0; i < rows.size(); i++) rows.get(i).setY(top + (first + i) * step() - pixels);
    }

    /** Advance once per frame. Null is outside the list; an empty stack is the no-attachment row. */
    @Nullable ItemStack hoveredCandidate(double mx, double my) {
        advance(Util.getMillis());
        for (Button row : rows) {
            if (row.isMouseOver(mx, my)) return row instanceof Row item ? item.stack : ItemStack.EMPTY;
        }
        return null;
    }

    void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (rows.isEmpty()) return;
        g.enableScissor(left, top, left + width + barGap + barWidth, bottom);
        try {
            for (Button row : rows) {
                boolean hot = row.isMouseOver(mx, my);
                if (row instanceof Row item) RefitSlotPainter.candidate(g, item, item.stack, item.selected(), hot);
                else if (row instanceof NoAttachmentRow empty) RefitSlotPainter.noAttachment(g, empty, empty.selected(), hot);
            }
            if (bar != null) bar.render(g, mx, my, partialTick);
        } finally {
            g.disableScissor();
        }
    }

    final class NoAttachmentRow extends Button {
        NoAttachmentRow() {
            super(left, top, RefitCandidateList.this.width, rowHeight,
                    Component.translatable("screen.codpattern.refit.no_attachment"),
                    button -> {
                        if (unload != null) unload.onPress();
                        returnToSlots.run();
                    }, DEFAULT_NARRATION);
        }

        // TaCZ only provides its unload action for removable attachments, excluding built-in defaults.
        public boolean selected() { return unload == null; }

        @Override
        public boolean isMouseOver(double x, double y) { return contains(x, y) && super.isMouseOver(x, y); }

        @Override
        protected boolean clicked(double x, double y) { return contains(x, y) && super.clicked(x, y); }
    }

    final class Row extends InventoryAttachmentSlot {
        final ItemStack stack;

        Row(Entry entry) {
            super(left, top, entry.slot(), inventory, install);
            stack = entry.stack();
            setMessage(Component.literal(ChatFormatting.stripFormatting(stack.getHoverName().getString())));
            setHeight(rowHeight);
            setWidth(RefitCandidateList.this.width);
        }

        public boolean selected() { return getSlotIndex() == installedSlot; }

        @Override
        public void onPress() {
            lastPressedSlot = getSlotIndex();
            super.onPress();
            returnToSlots.run();
        }

        @Override
        public boolean isMouseOver(double x, double y) { return contains(x, y) && super.isMouseOver(x, y); }

        @Override
        protected boolean clicked(double x, double y) { return contains(x, y) && super.clicked(x, y); }
    }

    private final class ScrollBar extends AbstractWidget {
        private double grab;

        ScrollBar() {
            super(left + RefitCandidateList.this.width + barGap, top, barWidth, bottom - top,
                    Component.translatable("screen.codpattern.refit.scrollbar"));
        }

        private int thumbHeight() { return Math.min(height, Math.max(10, height * height / (height + maxScroll()))); }

        private int thumbY() { return top + (int) Math.round(scroll / maxScroll() * (height - thumbHeight())); }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
            g.fill(getX(), top, getX() + width, bottom, 0xA537474A);
            g.fill(getX(), thumbY(), getX() + width, thumbY() + thumbHeight(),
                    isHoveredOrFocused() ? BackpackUi.INK : BackpackUi.ACCENT);
        }

        @Override
        public void onClick(double x, double y) {
            int thumb = thumbY();
            grab = y >= thumb && y < thumb + thumbHeight() ? y - thumb : thumbHeight() / 2.0;
            dragTo(y);
        }

        @Override
        protected void onDrag(double x, double y, double dx, double dy) { dragTo(y); }

        private void dragTo(double y) {
            scroll = target = start = Mth.clamp((y - grab - top) / Math.max(1, height - thumbHeight()), 0, 1) * maxScroll();
            updateRows();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
