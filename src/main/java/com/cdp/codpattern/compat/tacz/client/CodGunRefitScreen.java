package com.cdp.codpattern.compat.tacz.client;

import com.cdp.codpattern.client.gui.BackNavigation;
import com.cdp.codpattern.client.gui.screen.backpack.BackpackUi;
import com.cdp.codpattern.client.gui.screen.backpack.WeaponCatalog;
import com.cdp.codpattern.client.gui.screen.backpack.WeaponPreview;
import com.cdp.codpattern.config.backpack.BackpackSlot;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.FlatColorButton;
import com.tacz.guns.client.gui.components.refit.GunAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.HSVSliderGroup;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.RefitTurnPageButton;
import com.tacz.guns.client.gui.components.refit.RefitUnloadButton;
import net.minecraft.client.InputType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class CodGunRefitScreen extends GunRefitScreen {
    private static final Component TITLE = Component.translatable("screen.codpattern.loadout.refit");
    private static final Component EXPAND = Component.translatable("screen.codpattern.refit.expand_stats");
    private static final Component COLLAPSE = Component.translatable("screen.codpattern.refit.collapse_stats");
    private static final Component BACK_TO_SLOTS = Component.translatable("screen.codpattern.refit.back_to_slots");
    private static final int GAP = 2;

    private final Screen parentScreen;
    private final BackpackSlot weaponSlot;
    private final RefitPreviewCamera camera = new RefitPreviewCamera();
    private WeaponPreview.NativeView weaponPreview;
    private final RefitCandidateList candidateList = new RefitCandidateList(this::addWidget, this::removeWidget, this::setFocused);
    private final Map<AbstractWidget, Component> actions = new IdentityHashMap<>();
    private List<HSVSliderGroup.LaserColorSlider> sliders = List.of();
    private int margin, headerY, listWidth, listY, headingY;
    private int statsX, statsY, statsWidth, statsHeight, statsScroll;
    private Button statsToggle, fireModeButton;
    private float titleScale;
    private boolean detailed;
    private int refreshTicks;
    private List<RefitStatsPanel.Row> statsRows = List.of();
    @Nullable private ItemStack previewCandidate;
    private ItemStack comparedGun = ItemStack.EMPTY;
    private boolean keyboardNavigation;

    public CodGunRefitScreen() {
        this(null);
    }

    public CodGunRefitScreen(Screen parentScreen) {
        this(parentScreen, BackpackSlot.PRIMARY);
    }

    public CodGunRefitScreen(Screen parentScreen, BackpackSlot slot) {
        this.parentScreen = parentScreen;
        weaponSlot = slot == BackpackSlot.SECONDARY ? slot : BackpackSlot.PRIMARY;
    }

    @Override
    public Component getTitle() {
        return TITLE;
    }

    @Override
    public void init() {
        weaponPreview = WeaponPreview.nativeView(weaponSlot, weaponPreview);
        AttachmentType type = RefitTransform.getCurrentTransformType();
        previewCandidate = null;
        candidateList.begin(type);
        // TaCZ supplies the installation callback and the exact session inventory it reads.
        super.init();
        actions.clear();
        var categories = widgets(GunAttachmentSlot.class);
        widgets(InventoryAttachmentSlot.class).forEach(this::removeWidget);
        widgets(RefitTurnPageButton.class).forEach(this::removeWidget);
        sliders = widgets(HSVSliderGroup.LaserColorSlider.class);
        var cardColumn = WeaponCatalog.NativeCardColumn.current();
        margin = cardColumn.left();
        headerY = Mth.clamp(height / 27, 8, 20);
        boolean compact = height < 320;
        titleScale = compact ? 1.65f : 2.5f;
        listWidth = cardColumn.width();
        headingY = headerY + BackpackUi.lineHeight(titleScale) + (compact ? 10 : 16);
        listY = headingY + (compact ? 16 : 19);
        int backY = BackNavigation.bounds(width, height).top();
        int available = backY - listY;
        int baseHeight = Mth.clamp((available - 20) / Math.max(8, categories.size()) - GAP, 10, 25);
        int maxSlotHeight = (available + GAP) / Math.max(1, categories.size()) - GAP;
        int rowHeight = Math.max(1, Math.min(maxSlotHeight, Math.round(baseHeight * (compact ? 1.14f : 1.24f))));
        boolean selectingSlot = type == AttachmentType.NONE;
        for (int i = 0; i < categories.size(); i++) {
            var tab = categories.get(i);
            tab.visible = selectingSlot;
            tab.setMessage(RefitSlotPainter.categoryName(tab.getType()));
            position(tab, margin, listY + i * (rowHeight + GAP), listWidth, rowHeight);
        }
        if (!selectingSlot) {
            addRenderableWidget(BackpackUi.button(margin, headingY - 2, listWidth, listY - headingY,
                    BACK_TO_SLOTS, this::backToSlots, (g, b) -> paintSlotHeader(g, b, type)));
        }

        var nativeUnloads = widgets(RefitUnloadButton.class);
        var unload = nativeUnloads.stream().filter(button -> button.active && button.visible).findFirst().orElse(null);
        nativeUnloads.forEach(this::removeWidget);
        candidateList.layout(margin, listY, listWidth, backY - GAP, rowHeight,
                minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem(), unload);
        for (var button : widgets(FlatColorButton.class)) {
            if (button.getMessage().equals(Component.translatable("gui.tacz.gun_refit.property_diagrams.show"))
                    || button.getMessage().equals(Component.translatable("gui.tacz.gun_refit.property_diagrams.hide"))
                    || "S".equals(button.getMessage().getString())) button.visible = false;
        }

        statsToggle = addRenderableWidget(BackpackUi.button(0, 0, 1, 1, EXPAND, () -> {
            detailed = !detailed;
            statsScroll = 0;
            layoutStats();
            refreshStats();
        }, this::paintStatsToggle));
        Component mode = Component.translatable("gui.tacz.gun_refit.property_diagrams.fire_mode.switch");
        fireModeButton = addRenderableWidget(Button.builder(mode, b -> {
            if (minecraft.player != null && !minecraft.player.isSpectator()) {
                IClientPlayerGunOperator.fromLocalPlayer(minecraft.player).fireSelect();
                refreshStats();
            }
        }).build());
        actions.put(fireModeButton, mode);
        layoutStats();
        refreshStats();
        for (int i = 0; i < sliders.size(); i++) {
            int w = Math.min(120, width / 4);
            position(sliders.get(i), width - margin - w, headerY + i * 18, w, 16);
        }
        addRenderableWidget(BackNavigation.button(BackNavigation.bounds(width, height), this::onClose));
    }

    private void backToSlots() {
        if (RefitTransform.changeRefitScreenView(AttachmentType.NONE)) init();
    }

    public void captureCandidateInstaller(Inventory inventory, Button.OnPress install) {
        candidateList.captureInstaller(inventory, install);
    }

    private float equipmentScale() {
        return Math.min(width / 960f, height / 540f);
    }

    private void layoutStats() {
        ItemStack gun = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        var caption = weaponPreview.captionBounds(gun);
        var model = weaponPreview.modelBounds();
        float scale = equipmentScale();
        int gap = Math.max(2, Math.round(8 * scale));
        int buttonHeight = Math.max(9, Math.round(16 * scale));
        int buttonWidth = Math.max(36, Math.round((Math.max(BackpackUi.textWidth(EXPAND, .9f),
                BackpackUi.textWidth(COLLAPSE, .9f)) + 24) * scale));
        statsX = margin + listWidth + gap;
        int widthBesideTitle = caption.left() - gap - statsX;
        // A long pack or gun name can fill the caption band. Use the strip below it in that case.
        boolean belowTitle = widthBesideTitle < Math.round(217 * scale);
        int right = belowTitle ? width - margin : caption.left() - gap;
        int sideInset = Math.round((right - statsX) * .055f);
        statsX += sideInset;
        right -= sideInset;
        int bottom = belowTitle ? height - Math.max(1, gap / 2) : caption.bottom();
        int toggleY = bottom - buttonHeight;
        int toggleWidth = Math.min(buttonWidth, width - margin - statsX);
        position(statsToggle, statsX, toggleY, toggleWidth, buttonHeight);
        statsToggle.setMessage(detailed ? COLLAPSE : EXPAND);
        int bodyBottom = toggleY - GAP;
        int modelClearY = model.bottom() + gap;
        if (belowTitle && !detailed) {
            statsX += toggleWidth + gap;
            bodyBottom = bottom;
            modelClearY = Math.max(modelClearY, caption.bottom() + GAP);
        } else if (belowTitle) {
            bodyBottom = caption.top() - gap;
        }
        statsWidth = Math.max(1, right - statsX);
        statsHeight = detailed ? Math.max(1, Math.min(Math.round(height * .47f), bodyBottom - headerY - gap))
                : Math.max(1, Math.min(Math.round(35 * scale), bodyBottom - modelClearY));
        statsY = bodyBottom - statsHeight;
        fireModeButton.visible = detailed;
        int fireX = statsToggle.getX() + toggleWidth + gap;
        position(fireModeButton, fireX, toggleY, Math.max(1, Math.min(right - fireX, Math.round(120 * scale))), buttonHeight);
    }

    private void paintStatsToggle(GuiGraphics g, Button button) {
        int x = button.getX(), y = button.getY(), w = button.getWidth(), h = button.getHeight();
        boolean hot = highlighted(button);
        BackpackUi.actionFace(g, x, y, w, h, false, hot);
        float scale = equipmentScale();
        int step = Math.max(1, Math.round(scale));
        int iconX = x + Math.max(2, Math.round(7 * scale));
        int ink = hot ? BackpackUi.INK : BackpackUi.ACCENT;
        for (int i = -2; i <= 2; i++) {
            int dy = (detailed ? 1 - Math.abs(i) : Math.abs(i) - 1) * step;
            int dx = (i + 2) * step;
            g.fill(iconX + dx, y + h / 2 + dy, iconX + dx + step, y + h / 2 + dy + step, ink);
        }
        int textX = iconX + 5 * step + Math.max(2, Math.round(4 * scale));
        BackpackUi.text(g, button.getMessage(), textX, y + (h - BackpackUi.lineHeight(.9f * scale)) / 2,
                x + w - textX - 1, .9f * scale, BackpackUi.INK);
    }

    private void paintSlotHeader(GuiGraphics g, Button b, AttachmentType type) {
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        int ink = highlighted(b) ? BackpackUi.ACCENT : BackpackUi.MUTED;
        int mid = y + h / 2;
        for (int i = 0; i < 4; i++) {
            g.fill(x + i, mid - i, x + i + 1, mid - i + 1, ink);
            g.fill(x + i, mid + i, x + i + 1, mid + i + 1, ink);
        }
        Component name = RefitSlotPainter.categoryName(type);
        int nameWidth = Math.min(w / 2, BackpackUi.textWidth(name, .90f));
        BackpackUi.text(g, BACK_TO_SLOTS, x + 9, mid - 5, w - nameWidth - 13, .9f, ink);
        BackpackUi.text(g, name, x + w - nameWidth, mid - 5, nameWidth, .9f, BackpackUi.INK);
        g.fill(x, y + h - 1, x + w, y + h, highlighted(b) ? BackpackUi.ACCENT : 0xFF52686A);
    }

    private boolean highlighted(AbstractWidget widget) {
        return widget.isHovered() || (widget.isFocused() && minecraft.getLastInputType().isKeyboard());
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks % 5 == 0 && (previewCandidate == null || minecraft.player == null
                || !ItemStack.matches(comparedGun, minecraft.player.getMainHandItem()))) refreshStats();
    }

    private void refreshStats() {
        ItemStack gun = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        statsRows = RefitStatsPanel.rows(gun, detailed, RefitTransform.getCurrentTransformType(), previewCandidate);
        comparedGun = previewCandidate == null ? ItemStack.EMPTY : gun.copy();
        if (previewCandidate == null) statsScroll = Math.min(statsScroll, RefitStatsPanel.maxScroll(statsRows, statsWidth, statsHeight));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        keyboardNavigation = false;
        if (candidateList.wheel(x, y, delta)) return true;
        if (detailed && x >= statsX && x < statsX + statsWidth && y >= statsY && y < statsY + statsHeight) {
            int max = RefitStatsPanel.maxScroll(statsRows, statsWidth, statsHeight);
            statsScroll = Mth.clamp(statsScroll - (int) Math.round(delta * 22), 0, max);
            return max > 0;
        }
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public void mouseMoved(double x, double y) {
        keyboardNavigation = false;
        super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        keyboardNavigation = false;
        return super.mouseClicked(x, y, button);
    }

    private <T> List<T> widgets(Class<T> type) {
        return children().stream().filter(type::isInstance).map(type::cast).toList();
    }

    private static void position(AbstractWidget widget, int x, int y, int w, int h) {
        widget.setX(x);
        widget.setY(y);
        widget.setWidth(w);
        widget.setHeight(h);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ItemStack gun = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        weaponPreview.render(g, gun, camera.sample(gun));
        BackpackUi.text(g, TITLE, margin, headerY, listWidth, titleScale, BackpackUi.INK);
        if (RefitTransform.getCurrentTransformType() == AttachmentType.NONE) {
            BackpackUi.heading(g, Component.translatable("screen.codpattern.refit.slots"), margin, headingY, listWidth);
        }
        ItemStack hovered = candidateList.hoveredCandidate(mouseX, mouseY);
        if (keyboardNavigation && children().contains(getFocused())) {
            hovered = null;
            if (getFocused() instanceof RefitCandidateList.Row row) hovered = row.stack;
            else if (getFocused() instanceof RefitCandidateList.NoAttachmentRow) hovered = ItemStack.EMPTY;
        }
        if (previewCandidate != hovered) {
            previewCandidate = hovered;
            refreshStats();
        }
        if (detailed) RefitStatsPanel.render(g, statsRows, statsX, statsY, statsWidth, statsHeight,
                previewCandidate == null ? statsScroll : 0);
        else RefitStatsPanel.renderSummary(g, statsRows, statsX, statsY, statsWidth, statsHeight, equipmentScale());
        for (int i = 0; i < sliders.size(); i++) {
            var slider = sliders.get(i);
            var label = Component.translatable(i == 0 ? "screen.codpattern.refit.hue" : "screen.codpattern.refit.saturation");
            int w = Math.min(slider.getX() - margin, BackpackUi.textWidth(label, .85f) + 5);
            g.fill(slider.getX() - w - 3, slider.getY(), slider.getX(), slider.getY() + slider.getHeight(), 0xEB213033);
            BackpackUi.text(g, label, slider.getX() - w, slider.getY() + 4, w, .85f, BackpackUi.INK);
        }

        candidateList.render(g, mouseX, mouseY, partialTick);
        for (Renderable child : renderables) {
            if (child instanceof AbstractWidget w && !w.visible) continue;
            if (child instanceof GunAttachmentSlot slot) {
                RefitSlotPainter.category(g, slot, gun, slot.isMouseOver(mouseX, mouseY));
            } else if (child instanceof AbstractWidget w && actions.containsKey(w)) {
                paintAction(g, w, mouseX, mouseY);
            } else {
                child.render(g, mouseX, mouseY, partialTick);
            }
        }
    }

    private void paintAction(GuiGraphics g, AbstractWidget w, int mouseX, int mouseY) {
        int x = w.getX(), y = w.getY(), width = w.getWidth(), height = w.getHeight();
        boolean hot = w.isMouseOver(mouseX, mouseY) || (w.isFocused() && minecraft.getLastInputType().isKeyboard());
        BackpackUi.actionFace(g, x, y, width, height, false, hot);
        Component label = actions.get(w);
        float scale = Math.min(.85f, Math.min((width - 2f) / BackpackUi.textWidth(label, 1f),
                (height - 2f) / BackpackUi.lineHeight(1f)));
        int textWidth = BackpackUi.textWidth(label, scale);
        BackpackUi.text(g, label, x + (width - textWidth) / 2, y + (height - BackpackUi.lineHeight(scale)) / 2,
                textWidth, scale, BackpackUi.INK);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        keyboardNavigation = true;
        if (getFocused() instanceof AbstractWidget focus && candidateList.key(keyCode, focus)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.setLastInputType(InputType.KEYBOARD_TAB);
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        // ScreenEvent.Closing queues the backpack save for both Escape and the back button.
        minecraft.setScreen(parentScreen);
    }
}
