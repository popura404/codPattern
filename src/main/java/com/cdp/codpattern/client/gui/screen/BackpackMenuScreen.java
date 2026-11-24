package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.backpack.service.BackpackNamespaceFilter;
import com.cdp.codpattern.client.gui.BackNavigation;
import com.cdp.codpattern.client.gui.refit.AttachmentConfigButton;
import com.cdp.codpattern.client.gui.screen.backpack.BackpackCanvasScreen;
import com.cdp.codpattern.client.gui.screen.backpack.BackpackContextMenuCoordinator;
import com.cdp.codpattern.client.gui.screen.backpack.BackpackEquipmentRenderer;
import com.cdp.codpattern.client.gui.screen.backpack.BackpackUi;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.config.backpack.BackpackClientCache;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackItemStackFactory;
import com.cdp.codpattern.config.backpack.BackpackNameHelper;
import com.cdp.codpattern.config.backpack.BackpackSlot;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterClientCache;
import com.cdp.codpattern.network.AddBackpackPacket;
import com.cdp.codpattern.network.DeleteBackpackPacket;
import com.cdp.codpattern.network.RequestBackpackConfigPacket;
import com.cdp.codpattern.network.RequestWeaponFilterPacket;
import com.cdp.codpattern.network.SelectBackpackPacket;
import net.minecraft.Util;
import net.minecraft.client.InputType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.cdp.codpattern.client.gui.screen.backpack.BackpackUi.*;

public class BackpackMenuScreen extends BackpackCanvasScreen {
    private static final Component CREATE_MARK = Component.literal("+");
    private static final Component REFIT_KEY = Component.literal("R");
    private static final int TRANSITION_MS = 200;
    private record Columns(int width, int top, int rowHeight) {}
    private record PreviewStage(BackpackSlot slot, int x, int y, int width, int height, int caption) {}
    private record Transition(long start, Columns from, Columns to, PreviewStage preview) {}
    private Transition transition;
    private BackpackConfig.PlayerBackpackData data;
    private boolean editing;
    private int bagId;
    private BackpackSlot previewSlot = BackpackSlot.PRIMARY;
    private final Map<Integer, Button> bagButtons = new LinkedHashMap<>();
    private final EnumMap<BackpackSlot, Button> slotButtons = new EnumMap<>(BackpackSlot.class);
    private final EnumMap<BackpackSlot, ItemStack> equipment = new EnumMap<>(BackpackSlot.class);
    private final BackpackContextMenuCoordinator context = new BackpackContextMenuCoordinator();
    private boolean overGunCard;
    private int editOwner;
    private Button editButton;
    private Button createButton;
    private Button refitButton;
    private Button backButton;
    private double lastX, lastY;
    private boolean keyboardFocus;
    private boolean requestedData;
    private int pendingNewId;
    private int scrollOffset, pageHeight;
    private int margin, gap, titleY, headingY, rowsY, bagWidth, gearX, gearWidth, editWidth;
    private int stageX, stageY, stageWidth, stageHeight, captionY;

    public BackpackMenuScreen() {
        super(label("overview"));
    }

    private static Component label(String key) {
        return Component.translatable("screen.codpattern.loadout." + key);
    }

    @Override
    protected void init() {
        super.init();
        transition = null;
        // A child screen may return with the pointer over an entirely different row.
        lastX = pointerCanvasX();
        lastY = pointerCanvasY();
        resetHover();
        reloadFromPlayerData();
        if (!requestedData) {
            requestedData = true;
            ModNetworkChannel.sendToServer(new RequestWeaponFilterPacket());
            ModNetworkChannel.sendToServer(new RequestBackpackConfigPacket());
        }
    }

    public void reloadFromPlayerData() {
        data = BackpackClientCache.get();
        if (data == null) transition = null;
        boolean created = false;
        if (data != null) {
            var bags = data.getBackpacks_MAP();
            if (pendingNewId != 0 && bags.containsKey(pendingNewId)) {
                transition = null;
                bagId = pendingNewId;
                pendingNewId = 0;
                created = true;
                previewSlot = BackpackSlot.PRIMARY;
                resetHover();
            } else if (!bags.containsKey(bagId)) {
                transition = null;
                bagId = bags.containsKey(data.getSelectedBackpack()) ? data.getSelectedBackpack()
                        : bags.keySet().stream().min(Integer::compare).orElse(0);
                editing = false;
                previewSlot = BackpackSlot.PRIMARY;
                resetHover();
            }
        }
        buildControls();
        if (created) reveal(bagButtons.get(bagId));
        if (transition != null) {
            // A server update can clamp the destination scroll while the columns are moving.
            transition = new Transition(transition.start, transition.from, columns(),
                    editing ? previewStage() : transition.preview);
        }
    }

    private void resetHover() {
        keyboardFocus = minecraft.getLastInputType().isKeyboard();
        overGunCard = false;
        editOwner = 0;
        context.hide();
    }

    private void buildControls() {
        int previousScroll = scrollOffset;
        scrollOffset = 0;
        setFocused(null);
        clearWidgets();
        bagButtons.clear();
        slotButtons.clear();
        equipment.clear();
        editButton = null;
        createButton = null;
        refitButton = null;
        backButton = null;

        margin = LOADOUT_MARGIN;
        gap = LOADOUT_COLUMN_GAP;
        titleY = 19;
        headingY = titleY + titleHeight() + 13;
        rowsY = headingY + lineHeight(1f) + 10;
        int columns = canvasWidth - 2 * (margin + gap);
        bagWidth = editing ? Math.round(columns * .31f / 3.81f) : Math.round(columns * .8f / 4.17f);
        gearWidth = loadoutColumnWidth(canvasWidth);
        gearX = margin + bagWidth + gap;
        editWidth = Mth.clamp(Math.round(bagWidth * .23f), px(22), px(32));
        var backBounds = BackNavigation.bounds(canvasWidth, canvasHeight);
        int footerSpace = editing ? canvasHeight - backBounds.top() + px(4) : 9;
        int bottom = rowsY;
        if (data != null) {
            int bagBottom = addBags();
            int gearBottom = addSlots();
            bottom = Math.max(bagBottom, gearBottom);
        }
        pageHeight = Math.max(canvasHeight, bottom + footerSpace);
        int stageTop = headingY;
        int stageArea = pageHeight - footerSpace - stageTop;
        int stageLeft = gearX + gearWidth + gap;
        int stageAreaWidth = canvasWidth - margin - stageLeft;
        stageX = stageLeft + stageAreaWidth / 25;
        stageWidth = stageAreaWidth * 92 / 100;
        stageY = stageTop + stageArea * 8 / 100;
        stageHeight = stageArea * 60 / 100;
        captionY = stageTop + stageArea * 93 / 100 - captionHeight();

        if (editing && !slotButtons.isEmpty()) {
            backButton = addWidget(BackNavigation.button(backBounds, this::onClose));
            int w = refitWidth();
            int h = 27;
            refitButton = addRenderableWidget(BackpackUi.button(0, 0, w, h, label("refit"), this::openRefit, this::drawRefit));
        }
        scrollTo(previousScroll);
        positionActions();
        if (minecraft.getLastInputType().isKeyboard()) {
            setFocused(editing ? slotButtons.get(previewSlot) : bagButtons.get(bagId));
        }
    }

    private int refitWidth() {
        return Math.max(78, textWidth(label("refit"), 1f) + 40);
    }

    private int addBags() {
        int h = bagRowHeight();
        int y = rowsY;
        for (int id : data.getBackpacks_MAP().keySet().stream().sorted().toList()) {
            Component name = BackpackNameHelper.displayNameComponent(data.getBackpacks_MAP().get(id), id);
            Component number = Component.literal(String.format(Locale.ROOT, "%02d", id));
            Button button = BackpackUi.button(margin, y, bagWidth, h, name, () -> {
                if (editing) edit(id);
                else selectAndClose(id);
            }, (g, b) -> drawBag(g, b, id, number));
            bagButtons.put(id, addRenderableWidget(button));
            y += h + px(2);
        }
        if (!editing) {
            editButton = addRenderableWidget(BackpackUi.button(margin + bagWidth - editWidth, rowsY, editWidth,
                    h, label("edit"), () -> edit(editOwner), (g, b) -> {
                        int x = b.getX(), top = b.getY();
                        boolean hot = highlighted(b);
                        BackpackUi.horizontalGradient(g, x, top, editWidth, h, hot ? 0xFF27342C : 0xFF222C26,
                                hot ? 0xFF384A3E : 0xFF303D33);
                        g.renderOutline(x, top, editWidth, h, hot ? 0xFFC6ED83 : 0xFF6D7E69);
                        centered(g, b.getMessage(), b, hot ? 0xFFFFFFFF : 0xFFE3E9DD);
                    }));
        }
        if (data.getBackpackCount() < BackpackConfig.MAX_BACKPACKS) {
            var create = label("create");
            createButton = addRenderableWidget(BackpackUi.button(margin, y + px(3), bagWidth, px(21), create, this::createBag,
                    (g, b) -> {
                        int top = b.getY();
                        if (highlighted(b)) actionFace(g, margin, top, bagWidth, b.getHeight(), false, true);
                        g.fill(margin, top, margin + bagWidth, top + 1, 0x3BACBBB0);
                        if (compactBags()) centered(g, CREATE_MARK, b, ACCENT);
                        else {
                            int textY = top + (b.getHeight() - lineHeight(1f)) / 2;
                            int nameInset = bagWidth > px(110) ? px(27) : px(19);
                            text(g, CREATE_MARK, margin + px(8), textY, px(10), 1f, ACCENT);
                            text(g, create, margin + nameInset, textY, bagWidth - nameInset, 1f,
                                    highlighted(b) ? INK : MUTED);
                        }
                    }));
            y += px(24);
        }
        return y;
    }

    private int addSlots() {
        if (!data.getBackpacks_MAP().containsKey(bagId)) return rowsY;
        int y = rowsY;
        for (BackpackSlot slot : BackpackSlot.values()) {
            int h = loadoutCardHeight(slot);
            int w = loadoutCardWidth(gearWidth, slot);
            int x = slot == BackpackSlot.ITEM2 ? gearX + w + LOADOUT_CARD_GAP : gearX;
            Button b = BackpackUi.button(x, y, w, h, slotLabel(slot), () -> pick(slot),
                    (g, widget) -> drawSlot(g, widget, slot));
            b.active = editing;
            slotButtons.put(slot, addRenderableWidget(b));
            if (slot != BackpackSlot.ITEM1) y += h + LOADOUT_CARD_GAP;
        }
        refreshEquipment();
        return y - LOADOUT_CARD_GAP;
    }

    private void refreshEquipment() {
        var bag = data.getBackpacks_MAP().get(bagId);
        slotButtons.forEach((slot, button) -> {
            ItemStack item = stack(bag, slot);
            equipment.put(slot, item);
            button.setMessage(slotLabel(slot).copy().append(" ").append(BackpackEquipmentRenderer.displayName(item)));
        });
    }

    private void previewBag(int id) {
        if (bagId != id) {
            bagId = id;
            refreshEquipment();
        }
        editOwner = id;
        positionActions();
    }

    private void selectAndClose(int id) {
        ModNetworkChannel.sendToServer(new SelectBackpackPacket(id));
        ModNetworkChannel.sendToServer(new RequestBackpackConfigPacket());
        onClose();
    }

    private ItemStack stack(BackpackConfig.Backpack bag, BackpackSlot slot) {
        ItemStack stack = BackpackItemStackFactory.create(bag.getItem(slot));
        return BackpackNamespaceFilter.isBlocked(WeaponFilterClientCache.get(), stack, null) ? ItemStack.EMPTY : stack;
    }

    private void edit(int id) {
        switchView(true, id);
    }

    private Columns columns() {
        return new Columns(bagWidth, rowsY, bagRowHeight());
    }

    private int bagRowHeight() {
        return editing ? 24 : 36;
    }

    private PreviewStage previewStage() {
        return new PreviewStage(previewSlot, stageX, stageY, stageWidth, stageHeight, captionY);
    }

    private void switchView(boolean openEditor, int id) {
        Columns from = columns();
        PreviewStage previous = previewStage();
        boolean changeView = editing != openEditor;
        scrollOffset = 0;
        bagId = id;
        editing = openEditor;
        previewSlot = BackpackSlot.PRIMARY;
        resetHover();
        buildControls();
        if (!editing) reveal(bagButtons.get(bagId));
        if (changeView) {
            transition = new Transition(Util.getMillis(), from, columns(), editing ? previewStage() : previous);
        }
    }

    @Override
    protected boolean isTransitioning() {
        return transition != null;
    }

    private boolean compactBags() {
        return transition == null ? editing : 2 * bagWidth < transition.from.width + transition.to.width;
    }

    private void positionColumns(int width, int top, int rowHeight) {
        int dx = width - bagWidth, dy = top - rowsY;
        bagWidth = width;
        gearX += dx;
        titleY += dy;
        headingY += dy;
        rowsY = top;
        int y = top;
        for (Button button : bagButtons.values()) {
            button.setWidth(width);
            button.setHeight(rowHeight);
            button.setY(y);
            y += rowHeight + px(2);
        }
        if (createButton != null) {
            createButton.setWidth(width);
            createButton.setY(y + px(3));
        }
        for (Button button : slotButtons.values()) {
            button.setPosition(button.getX() + dx, button.getY() + dy);
        }
    }

    private void animateColumns(GuiGraphics g) {
        var slide = transition;
        float t = Mth.clamp((Util.getMillis() - slide.start) / (float) TRANSITION_MS, 0f, 1f);
        float eased = t * t * (3f - 2f * t);
        positionColumns(Math.round(Mth.lerp(eased, (float) slide.from.width, slide.to.width)),
                Math.round(Mth.lerp(eased, (float) slide.from.top, slide.to.top)),
                Math.round(Mth.lerp(eased, (float) slide.from.rowHeight, slide.to.rowHeight)));
        if (t >= 1f) {
            transition = null;
            setDragging(false);
            if (!keyboardFocus) {
                lastX = pointerCanvasX();
                lastY = pointerCanvasY();
                updatePointer(lastX, lastY);
            } else if (!editing) {
                previewBag(bagId);
            }
            return;
        }
        // Reveal the editor model in the space opened by the cards, retaining its full final scale.
        int left = gearX + gearWidth + gap;
        int right = Math.round(Mth.lerp(editing ? eased : 1f - eased, (float) left, canvasWidth));
        if (right > left && equipment.containsKey(slide.preview.slot)) {
            renderCanvasClipped(g, left, 0, right, contentBottom(), () -> drawPreview(g, slide.preview));
        }
    }

    private void drawPreview(GuiGraphics g, PreviewStage stage) {
        var stack = equipment.get(stage.slot);
        BackpackEquipmentRenderer.renderModel(g, stack, stage.x, stage.y, stage.width, stage.height);
        loadoutCaption(g, stage.slot, stack, stage.x, stage.caption, canvasWidth - margin - stage.x);
    }

    /** The server can finish opening a refit after the pointer has crossed into another slot. */
    public void restoreRefitTarget(int id, BackpackSlot slot) {
        editing = true;
        bagId = id;
        previewSlot = slot;
    }

    private void createBag() {
        pendingNewId = data.getNextAvailableId();
        ModNetworkChannel.sendToServer(new AddBackpackPacket());
    }

    private void pick(BackpackSlot slot) {
        previewSlot = slot;
        minecraft.setScreen(new WeaponScreen(this, data.getBackpacks_MAP().get(bagId), bagId, slot));
    }

    private void openRefit() {
        ItemStack stack = equipment.get(previewSlot);
        TaczGatewayProvider.gateway().resolveGunId(stack).ifPresent(id ->
                AttachmentConfigButton.open(bagId, previewSlot.key(), id, this));
    }

    private void centered(GuiGraphics g, Component name, Button b, int color) {
        int x = b.getX() + Math.max(0, (b.getWidth() - textWidth(name, 1f)) / 2);
        int y = b.getY() + (b.getHeight() - lineHeight(1f)) / 2;
        text(g, name, x, y, b.getX() + b.getWidth() - x, 1f, color);
    }

    private boolean highlighted(Button b) {
        return b.isHovered() || (keyboardFocus && b.isFocused());
    }

    private void drawBag(GuiGraphics g, Button b, int id, Component number) {
        int x = b.getX(), y = b.getY(), h = b.getHeight();
        boolean deployed = id == data.getSelectedBackpack();
        boolean joined = compactBags() ? bagId == id : deployed;
        bagFace(g, b, id, joined, editing ? highlighted(b) : editOwner == id);
        if (compactBags()) {
            centered(g, number, b, joined ? JOINED_INK : INK);
            return;
        }
        int left = x + px(8);
        float nameScale = 1.12f;
        if (bagWidth > px(110)) {
            text(g, number, left, y + (h - lineHeight(1f)) / 2, px(17), 1f, joined ? JOINED_MUTED : MUTED);
            left = x + px(27);
        }
        int remaining = x + bagWidth - editWidth - px(3) - left;
        int nameHeight = lineHeight(nameScale);
        int statusHeight = deployed ? lineHeight(1f) + px(1) : 0;
        int top = y + (h - nameHeight - statusHeight) / 2;
        text(g, b.getMessage(), left, top, remaining, nameScale, joined ? JOINED_INK : INK);
        if (deployed) text(g, label("deployed"), left, top + nameHeight + px(1), remaining, 1f, JOINED_MUTED);
    }

    private void drawSlot(GuiGraphics g, Button b, BackpackSlot slot) {
        int x = b.getX(), y = b.getY(), h = b.getHeight(), w = b.getWidth();
        ItemStack stack = equipment.get(slot);
        Component name = BackpackEquipmentRenderer.displayName(stack);
        boolean current = editing && previewSlot == slot;
        if (editing) row(g, x, y, w, h, slot, 0, current);
        else readOnlySlot(g, x, y, w, h, slot);
        int padX = 8;
        int padY = slot.isEquipmentSlot() ? 3 : 5;
        int left = x + padX, baselineBottom = y + h - padY;
        float nameScale = w < px(75) ? 1f : 1.12f;
        Component role = slotLabel(slot);
        int roleInk = current ? 0xFFC2E6E2 : MUTED;
        if (slot.isEquipmentSlot()) {
            boolean compact = w <= px(60);
            int iconW = compact ? Math.min(px(9), h / 3) : w / 4;
            int iconH = compact ? iconW : h * 65 / 100;
            BackpackEquipmentRenderer.renderIcon(g, stack, x + w - padX - iconW,
                    compact ? y + padY : y + (h - iconH) / 2, iconW, iconH);
            int lineW = w - 2 * padX;
            text(g, role, left, y + padY, lineW - iconW, 1f, roleInk);
            // The item renderer centers a square inside the icon area.
            int iconInset = (iconW - Math.min(iconW, iconH)) / 2;
            int nameWidth = compact ? lineW : lineW - iconW + iconInset;
            if (textWidth(name, nameScale) > nameWidth) nameScale = 1f;
            text(g, name, left, baselineBottom - lineHeight(nameScale), nameWidth, nameScale, INK);
            return;
        }
        int nameY = baselineBottom - lineHeight(nameScale);
        int labelBand = padY + lineHeight(nameScale) + px(1);
        if (slot == BackpackSlot.MELEE) {
            // A square item icon can extend beside short labels without covering them.
            int marginY = Math.round(h * .16f);
            int sideSpace = (w - (h - 2 * marginY)) / 2 - padX;
            if (sideSpace >= Math.max(textWidth(role, 1f), textWidth(name, nameScale)) + px(2)) {
                labelBand = Math.min(labelBand, marginY);
            }
        }
        BackpackEquipmentRenderer.renderCardIcon(g, stack, x, y, w, h, 68, labelBand);
        text(g, role, left, y + padY, w - 2 * padX, 1f, roleInk);
        Component type = slot.isGunSlot() ? BackpackEquipmentRenderer.category(stack) : Component.empty();
        if (!type.getString().isBlank()) {
            Component prefix = Component.translatable("screen.codpattern.loadout.type_prefix", type);
            int typeW = Math.min(textWidth(prefix, 1f), (w - 2 * padX) / 2);
            text(g, prefix, left, baselineBottom - lineHeight(1f), typeW, 1f, MUTED);
            left += typeW;
        }
        text(g, name, left, nameY, x + w - padX - left, nameScale, INK);
    }

    private void drawRefit(GuiGraphics g, Button b) {
        int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
        boolean hot = highlighted(b);
        g.fill(x, y, x + w, y + h, hot ? 0xFF31332B : 0xFF222725);
        g.fill(x, y + h - 1, x + w, y + h, hot ? 0xFFDFC189 : 0xFF8B7956);
        int color = hot ? 0xFFFFE4B4 : 0xFFE9CEA0;
        int keyH = lineHeight(1f) + 2 * px(3);
        int keyW = Math.max(keyH, textWidth(REFIT_KEY, 1f) + 2 * px(4));
        int space = px(5);
        int left = x + Math.max(1, (w - textWidth(b.getMessage(), 1f) - keyW - space) / 2);
        int textY = y + (h - lineHeight(1f)) / 2;
        keycap(g, REFIT_KEY, left, y + (h - keyH) / 2, keyW, keyH,
                hot ? color : MUTED, hot ? 0xFFAA966D : 0xFF73786D);
        text(g, b.getMessage(), left + keyW + space, textY, x + w - left - keyW - space, 1f, color);
    }

    @Override
    protected void renderCanvas(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (isTransitioning()) animateColumns(g);
        int x = isTransitioning() ? -1 : mouseX, y = isTransitioning() ? -1 : mouseY;
        if (backButton != null && pageHeight > canvasHeight) {
            int bottom = contentBottom();
            renderCanvasClipped(g, 0, 0, canvasWidth, bottom, () ->
                    drawPage(g, x, y < bottom ? y : -1, partialTick));
        } else {
            drawPage(g, x, y, partialTick);
        }
        if (backButton != null && compactBags()) backButton.render(g, x, y, partialTick);
        context.render(g, x, y, partialTick);
    }

    private void drawPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean compact = compactBags();
        title(g, margin, titleY, canvasWidth - margin * 2, label(compact ? "editor" : "overview"));
        if (data == null) {
            text(g, Component.translatable("screen.codpattern.backpack_menu.loading"), margin, rowsY, canvasWidth / 2, 1f, MUTED);
            return;
        }
        heading(g, label(compact ? "bags_short" : "bags"), margin, headingY, bagWidth);
        if (!compact) {
            Component count = Component.literal(String.format(Locale.ROOT, "%02d / %d",
                    data.getBackpackCount(), BackpackConfig.MAX_BACKPACKS));
            int w = textWidth(count, 1f);
            text(g, count, margin + bagWidth - w, headingY, w, 1f, MUTED);
        }
        var bag = data.getBackpacks_MAP().get(bagId);
        if (bag != null) {
            heading(g, BackpackNameHelper.displayNameComponent(bag, bagId), gearX, headingY, gearWidth);
            if (editing && !isTransitioning()) drawPreview(g, previewStage());
        }
        renderCanvasWidgets(g, mouseX, mouseY, partialTick);
    }

    private boolean canRefit(BackpackSlot slot) {
        return editing && slot.isGunSlot() && TaczGatewayProvider.gateway().isGun(equipment.get(slot));
    }

    private boolean inRefitHoverArea(BackpackSlot slot, Button card, double x, double y) {
        // Split the gap between rows so the two gun targets never overlap.
        int above = slot == BackpackSlot.PRIMARY ? gap / 2 : LOADOUT_CARD_GAP / 2;
        int below = LOADOUT_CARD_GAP - LOADOUT_CARD_GAP / 2;
        int buttonRight = card.getX() + card.getWidth() + gap + refitButton.getWidth();
        return x >= card.getX() - gap / 2 && x < buttonRight + gap
                && y >= card.getY() - above && y < card.getY() + card.getHeight() + below
                && canRefit(slot);
    }

    private void positionActions() {
        if (editButton != null) {
            Button owner = bagButtons.get(editOwner);
            editButton.visible = owner != null;
            if (owner != null) editButton.setY(owner.getY());
        }
        if (refitButton != null) {
            refitButton.visible = overGunCard && canRefit(previewSlot);
            if (refitButton.visible) {
                Button owner = slotButtons.get(previewSlot);
                int x = owner.getX() + owner.getWidth() + gap;
                int y = owner.getY() + 1;
                refitButton.setPosition(x, y);
            }
        }
    }

    @Override
    protected void canvasMouseMoved(double x, double y) {
        if (x == lastX && y == lastY) return;
        lastX = x;
        lastY = y;
        keyboardFocus = false;
        updatePointer(x, y);
    }

    private void updatePointer(double x, double y) {
        if (context.isVisible()) return;
        if (backButton != null && y >= contentBottom()) {
            overGunCard = false;
            positionActions();
            return;
        }
        if (!editing) {
            for (var entry : bagButtons.entrySet()) {
                if (entry.getValue().isMouseOver(x, y)) {
                    previewBag(entry.getKey());
                    return;
                }
            }
            editOwner = 0;
        } else if (refitButton != null) {
            overGunCard = false;
            for (var entry : slotButtons.entrySet()) {
                if (entry.getValue().isMouseOver(x, y) || inRefitHoverArea(entry.getKey(), entry.getValue(), x, y)) {
                    previewSlot = entry.getKey();
                    overGunCard = previewSlot.isGunSlot();
                    break;
                }
            }
        }
        positionActions();
    }

    @Override
    protected boolean canvasMouseClicked(double x, double y, int button) {
        lastX = x;
        lastY = y;
        keyboardFocus = false;
        if (context.handleMenuClick(x, y, button)) return true;
        if (backButton != null && y >= contentBottom()) return backButton.mouseClicked(x, y, button);
        if (button == 1) {
            for (var entry : bagButtons.entrySet()) {
                if (entry.getValue().isMouseOver(x, y)) {
                    int id = entry.getKey();
                    context.show(this, id, data.getBackpacks_MAP().get(id).getName(), (int) x, (int) y,
                            canvasWidth, canvasHeight, this::hideActions, this::confirmDelete);
                    return true;
                }
            }
        }
        // The edit control is drawn on top of the right-hand edge of the bag button.
        if (editButton != null && editButton.mouseClicked(x, y, button)) return true;
        boolean handled = super.canvasMouseClicked(x, y, button);
        if (getFocused() != null && !children().contains(getFocused())) setFocused(null);
        return handled;
    }

    private void hideActions() {
        editOwner = 0;
        overGunCard = false;
        positionActions();
    }

    private void confirmDelete(int id) {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) ModNetworkChannel.sendToServer(new DeleteBackpackPacket(id));
        }, Component.translatable("screen.codpattern.backpack.delete.title"),
                Component.translatable("screen.codpattern.backpack.delete.message", id)));
    }

    private void scrollTo(int requestedOffset) {
        // Both columns belong to one page, including the create button after the final bag.
        int next = Mth.clamp(requestedOffset, 0, Math.max(0, pageHeight - canvasHeight));
        int delta = scrollOffset - next;
        if (delta == 0) return;
        scrollOffset = next;
        titleY += delta;
        headingY += delta;
        rowsY += delta;
        stageY += delta;
        captionY += delta;
        for (var child : children()) {
            if (child != backButton && child instanceof AbstractWidget widget) widget.setY(widget.getY() + delta);
        }
    }

    private int contentBottom() {
        return backButton == null ? canvasHeight : backButton.getY() - px(4);
    }

    private void reveal(AbstractWidget b) {
        if (b == null || b == backButton) return;
        if (b.getY() < 0) scrollTo(scrollOffset + b.getY() - px(3));
        else if (b.getY() + b.getHeight() > contentBottom())
            scrollTo(scrollOffset + b.getY() + b.getHeight() - contentBottom() + px(3));
    }

    @Override
    protected boolean canvasMouseScrolled(double x, double y, double delta) {
        if (pageHeight > canvasHeight && !context.isVisible()) {
            scrollTo(scrollOffset - (int) Math.round(delta * px(24)));
            if (!keyboardFocus) updatePointer(x, y);
            return true;
        }
        return super.canvasMouseScrolled(x, y, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (isTransitioning()) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (context.isVisible()) context.hide();
            else onClose();
            return true;
        }
        if (context.isVisible()) return true;
        boolean enterEditor = key == GLFW.GLFW_KEY_D && !editing && bagButtons.containsKey(bagId);
        boolean leaveEditor = key == GLFW.GLFW_KEY_A && editing;
        if (modifiers == 0 && (enterEditor || leaveEditor)) {
            minecraft.setLastInputType(InputType.KEYBOARD_ARROW);
            switchView(enterEditor, bagId);
            return true;
        }
        if (key == GLFW.GLFW_KEY_R && modifiers == 0 && refitButton != null && canRefit(previewSlot)
                && (refitButton.visible || (keyboardFocus && getFocused() == slotButtons.get(previewSlot)))) {
            openRefit();
            return true;
        }
        boolean navigate = key == GLFW.GLFW_KEY_TAB || (key >= GLFW.GLFW_KEY_RIGHT && key <= GLFW.GLFW_KEY_UP);
        if (navigate) keyboardFocus = true;
        if (editing && navigateSlots(key)) return true;
        if (!editing && navigate && editButton != null) {
            boolean forwardTab = key == GLFW.GLFW_KEY_TAB && !hasShiftDown();
            if (getFocused() == editButton) {
                setFocused(bagButtons.get(editOwner));
                if (key == GLFW.GLFW_KEY_LEFT || (key == GLFW.GLFW_KEY_TAB && !forwardTab)) return true;
                hideActions();
            } else if (forwardTab || key == GLFW.GLFW_KEY_RIGHT) {
                for (var entry : bagButtons.entrySet()) {
                    if (getFocused() == entry.getValue()) {
                        previewBag(entry.getKey());
                        setFocused(editButton);
                        reveal(editButton);
                        return true;
                    }
                }
            }
        }
        var previousFocus = getFocused();
        boolean handled = super.keyPressed(key, scanCode, modifiers);
        if (navigate && getFocused() instanceof AbstractWidget focused) {
            reveal(focused);
            if (focused == editButton || focused == refitButton) return true;
            hideActions();
            if (!editing) {
                bagButtons.forEach((id, b) -> { if (focused == b) previewBag(id); });
            } else {
                slotButtons.forEach((slot, b) -> {
                    if (focused == b) {
                        previewSlot = slot;
                        overGunCard = slot.isGunSlot();
                        positionActions();
                    }
                });
            }
        }
        return handled || (navigate && getFocused() != previousFocus);
    }

    private boolean navigateSlots(int key) {
        // Up/down follows all five slots in loadout order. The equipment pair also accepts left/right.
        for (var entry : slotButtons.entrySet()) {
            BackpackSlot current = entry.getKey();
            if (getFocused() != entry.getValue() && !(getFocused() == refitButton && current == previewSlot)) continue;
            BackpackSlot[] slots = BackpackSlot.values();
            BackpackSlot next = switch (key) {
                case GLFW.GLFW_KEY_UP -> slots[Math.max(0, current.ordinal() - 1)];
                case GLFW.GLFW_KEY_DOWN -> slots[Math.min(slots.length - 1, current.ordinal() + 1)];
                case GLFW.GLFW_KEY_LEFT -> current == BackpackSlot.ITEM2 ? BackpackSlot.ITEM1 : null;
                case GLFW.GLFW_KEY_RIGHT -> current == BackpackSlot.ITEM1 ? BackpackSlot.ITEM2 : null;
                default -> null;
            };
            if (next == null) return false;
            previewSlot = next;
            Button target = slotButtons.get(next);
            setFocused(target);
            reveal(target);
            overGunCard = next.isGunSlot();
            positionActions();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        return isTransitioning() || super.keyReleased(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return isTransitioning() || super.charTyped(character, modifiers);
    }

    @Override
    public void onClose() {
        if (isTransitioning()) return;
        if (editing) {
            switchView(false, bagId);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
