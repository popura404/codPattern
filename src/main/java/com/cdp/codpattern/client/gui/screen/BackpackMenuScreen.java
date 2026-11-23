package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.backpack.service.BackpackNamespaceFilter;
import com.cdp.codpattern.client.gui.refit.AttachmentConfigButton;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.cdp.codpattern.client.gui.screen.backpack.BackpackUi.*;

public class BackpackMenuScreen extends Screen {
    private static final Component CREATE_MARK = Component.literal("+");
    private static final Component REFIT_KEY = Component.literal("R");
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
    private Button refitButton;
    private double lastX, lastY;
    private boolean keyboardFocus;
    private boolean requestedData;
    private int pendingNewId;
    private int scrollOffset, pageHeight;
    private int margin, gap, titleY, headingY, rowsY, bagWidth, gearX, gearWidth, slotUnit, editWidth;
    private int stageX, stageY, stageWidth, stageHeight, captionY;
    private boolean stacked;

    public BackpackMenuScreen() {
        super(label("overview"));
    }

    private static Component label(String key) {
        return Component.translatable("screen.codpattern.loadout." + key);
    }

    @Override
    protected void init() {
        // A child screen may return with the pointer over an entirely different row.
        // Use the same double-precision GUI coordinates as MouseHandler.onMove.
        var window = minecraft.getWindow();
        lastX = minecraft.mouseHandler.xpos() * width / window.getScreenWidth();
        lastY = minecraft.mouseHandler.ypos() * height / window.getScreenHeight();
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
        boolean created = false;
        if (data != null) {
            var bags = data.getBackpacks_MAP();
            if (pendingNewId != 0 && bags.containsKey(pendingNewId)) {
                bagId = pendingNewId;
                pendingNewId = 0;
                created = true;
                previewSlot = BackpackSlot.PRIMARY;
                resetHover();
            } else if (!bags.containsKey(bagId)) {
                bagId = bags.containsKey(data.getSelectedBackpack()) ? data.getSelectedBackpack()
                        : bags.keySet().stream().min(Integer::compare).orElse(0);
                editing = false;
                previewSlot = BackpackSlot.PRIMARY;
                resetHover();
            }
        }
        buildControls();
        if (created) reveal(bagButtons.get(bagId));
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
        refitButton = null;

        stacked = width <= px(380);
        boolean narrow = width <= px(490);
        boolean shortDesktop = !stacked && height <= px(300);
        margin = stacked ? px(7) : Mth.clamp(Math.round(width * .038f), px(8), px(35));
        gap = stacked ? px(4) : Mth.clamp(Math.round(width * .0125f), px(5), px(12));
        titleY = stacked ? px(8) : shortDesktop ? px(5) : Mth.clamp(Math.round(height * .035f), px(9), px(20));
        int titleGap = stacked ? px(6) : shortDesktop ? px(5) : Mth.clamp(Math.round(height * .024f), px(7), px(13));
        headingY = titleY + titleHeight() + titleGap;
        rowsY = headingY + lineHeight(1f) + px(10);
        int inner = width - 2 * margin;
        int columns = inner - (stacked ? gap : 2 * gap);
        if (stacked) {
            bagWidth = editing ? px(27) : Math.round(columns * .78f / 1.88f);
            gearWidth = columns - bagWidth;
        } else if (narrow) {
            bagWidth = editing ? px(31) : Math.round(columns * .85f / 3.35f);
            gearWidth = editing ? Math.round((columns - bagWidth) * .9f / 2.55f) : Math.round(columns / 3.35f);
        } else {
            bagWidth = editing ? Math.round(columns * .31f / 3.81f) : Math.round(columns * .8f / 4.17f);
            gearWidth = editing ? Math.round(columns * 1.05f / 3.81f) : Math.round(columns * 1.02f / 4.17f);
        }
        gearX = margin + bagWidth + gap;
        editWidth = Mth.clamp(Math.round(bagWidth * .23f), px(22), px(32));
        slotUnit = stacked ? px(25) : Mth.clamp(Math.round(height * (editing ? .092f : .088f)),
                px(editing ? 27 : 26), px(editing ? 50 : 47));
        int footerSpace = editing ? px(39) : px(9);
        int slotGap = px(stacked ? 2 : 3);
        int bagBottom = rowsY;
        if (data != null) {
            bagBottom = addBags();
            addSlots(slotGap);
        }
        int bottom = Math.max(bagBottom, rowsY + 6 * slotUnit + slotUnit / 2 + 3 * slotGap);
        int stageTop = stacked ? bottom + gap : headingY;
        if (stacked && editing) bottom = stageTop + Math.max(px(114), height / 3);
        pageHeight = Math.max(height, bottom + footerSpace);
        int stageArea = pageHeight - footerSpace - stageTop;
        int stageLeft = stacked ? margin : gearX + gearWidth + gap;
        int stageAreaWidth = width - margin - stageLeft;
        stageX = stageLeft + stageAreaWidth / 25;
        stageWidth = stageAreaWidth * 92 / 100;
        stageY = stageTop + stageArea * 8 / 100;
        stageHeight = stageArea * 60 / 100;
        captionY = stageTop + stageArea * 93 / 100 - loadoutCaptionHeight();

        if (editing && !slotButtons.isEmpty()) {
            addRenderableWidget(BackpackUi.backButton(margin, pageHeight - px(26), this::onClose));
            int w = refitWidth();
            int h = stacked ? px(33) : px(27);
            refitButton = addRenderableWidget(BackpackUi.button(0, 0, w, h, label("refit"), this::openRefit, this::drawRefit));
        }
        scrollTo(previousScroll);
        positionActions();
        if (minecraft.getLastInputType().isKeyboard()) {
            setFocused(editing ? slotButtons.get(previewSlot) : bagButtons.get(bagId));
        }
    }

    private int refitWidth() {
        return stacked ? px(54) : Math.max(px(78), textWidth(label("refit"), 1f) + px(40));
    }

    private int addBags() {
        int h = editing ? Mth.clamp(height / 20, px(19), px(24))
                : Mth.clamp(Math.round(height * .0665f), px(24), px(39));
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
            addRenderableWidget(BackpackUi.button(margin, y + px(3), bagWidth, px(21), create, this::createBag,
                    (g, b) -> {
                        int top = b.getY();
                        if (highlighted(b)) actionFace(g, margin, top, bagWidth, b.getHeight(), false, true);
                        g.fill(margin, top, margin + bagWidth, top + 1, 0x3BACBBB0);
                        if (editing) centered(g, CREATE_MARK, b, ACCENT);
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

    private void addSlots(int gutter) {
        if (!data.getBackpacks_MAP().containsKey(bagId)) return;
        int half = (gearWidth - gutter) / 2;
        int y = rowsY;
        for (BackpackSlot slot : BackpackSlot.values()) {
            int h = slot.isGunSlot() ? 2 * slotUnit : slot == BackpackSlot.MELEE ? 3 * slotUnit / 2 : slotUnit;
            int x = slot == BackpackSlot.ITEM2 ? gearX + half + gutter : gearX;
            int w = slot.isEquipmentSlot() ? (slot == BackpackSlot.ITEM1 ? half : gearWidth - half - gutter) : gearWidth;
            if (stacked && editing && slot.isGunSlot()) w -= refitWidth() + gutter;
            Button b = BackpackUi.button(x, y, w, h, slotLabel(slot), () -> pick(slot),
                    (g, widget) -> drawSlot(g, widget, slot));
            b.active = editing;
            slotButtons.put(slot, addRenderableWidget(b));
            if (slot != BackpackSlot.ITEM1) y += h + gutter;
        }
        refreshEquipment();
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
        scrollOffset = 0;
        bagId = id;
        editing = true;
        previewSlot = BackpackSlot.PRIMARY;
        resetHover();
        buildControls();
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
        boolean joined = editing ? bagId == id : deployed;
        bagFace(g, b, id, joined, editing ? highlighted(b) : editOwner == id);
        if (editing) {
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
        int padX = Mth.clamp(Math.round(width * .0085f), px(4), px(8));
        int padY = slot.isEquipmentSlot() ? Mth.clamp(Math.round(height * .0055f), px(1), px(3))
                : Mth.clamp(Math.round(height * .0085f), px(3), px(5));
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
        if (stacked) {
            centered(g, b.getMessage(), b, color);
            return;
        }
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        background(g, width, height);
        title(g, margin, titleY, width - margin * 2, label(editing ? "editor" : "overview"));
        if (data == null) {
            text(g, Component.translatable("screen.codpattern.backpack_menu.loading"), margin, rowsY, width / 2, 1f, MUTED);
            return;
        }
        heading(g, label(editing ? "bags_short" : "bags"), margin, headingY, bagWidth);
        if (!editing) {
            Component count = Component.literal(String.format(Locale.ROOT, "%02d / %d",
                    data.getBackpackCount(), BackpackConfig.MAX_BACKPACKS));
            int w = textWidth(count, 1f);
            text(g, count, margin + bagWidth - w, headingY, w, 1f, MUTED);
        }
        var bag = data.getBackpacks_MAP().get(bagId);
        if (bag != null) {
            heading(g, BackpackNameHelper.displayNameComponent(bag, bagId), gearX, headingY, gearWidth);
            if (editing) {
                var stack = equipment.get(previewSlot);
                BackpackEquipmentRenderer.renderModel(g, stack, stageX, stageY, stageWidth, stageHeight);
                int inset = Math.max(1, stageWidth / 45);
                loadoutCaption(g, previewSlot, stack, stageX + inset, captionY, stageWidth - inset);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
        context.render(g, mouseX, mouseY, partialTick);
    }

    private boolean canRefit() {
        return editing && previewSlot.isGunSlot() && TaczGatewayProvider.gateway().isGun(equipment.get(previewSlot));
    }

    private void positionActions() {
        if (editButton != null) {
            Button owner = bagButtons.get(editOwner);
            editButton.visible = owner != null;
            if (owner != null) editButton.setY(owner.getY());
        }
        if (refitButton != null) {
            refitButton.visible = overGunCard && canRefit();
            if (refitButton.visible) {
                Button owner = slotButtons.get(previewSlot);
                int x = owner.getX() + owner.getWidth() + (stacked ? px(2) : gap);
                int y = owner.getY() + (stacked ? (owner.getHeight() - refitButton.getHeight()) / 2 : px(1));
                refitButton.setPosition(x, y);
            }
        }
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (x == lastX && y == lastY) return;
        lastX = x;
        lastY = y;
        keyboardFocus = false;
        updatePointer(x, y);
    }

    private void updatePointer(double x, double y) {
        if (context.isVisible()) return;
        if (!editing) {
            for (var entry : bagButtons.entrySet()) {
                if (entry.getValue().isMouseOver(x, y)) {
                    previewBag(entry.getKey());
                    return;
                }
            }
            editOwner = 0;
        } else if (refitButton != null) {
            Button owner = slotButtons.get(previewSlot);
            if (refitButton.visible && (refitButton.isMouseOver(x, y)
                    || (x >= owner.getX() + owner.getWidth() && x < refitButton.getX()
                    && y >= owner.getY() && y < owner.getY() + owner.getHeight()))) return;
            overGunCard = false;
            for (var entry : slotButtons.entrySet()) {
                if (entry.getValue().isMouseOver(x, y)) {
                    previewSlot = entry.getKey();
                    overGunCard = previewSlot.isGunSlot();
                    break;
                }
            }
        }
        positionActions();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        lastX = x;
        lastY = y;
        keyboardFocus = false;
        if (context.handleMenuClick(x, y, button)) return true;
        if (button == 1) {
            for (var entry : bagButtons.entrySet()) {
                if (entry.getValue().isMouseOver(x, y)) {
                    int id = entry.getKey();
                    context.show(this, id, data.getBackpacks_MAP().get(id).getName(), (int) x, (int) y,
                            width, height, this::hideActions, this::confirmDelete);
                    return true;
                }
            }
        }
        // The edit control is drawn on top of the right-hand edge of the bag button.
        if (editButton != null && editButton.mouseClicked(x, y, button)) return true;
        boolean handled = super.mouseClicked(x, y, button);
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
        int next = Mth.clamp(requestedOffset, 0, Math.max(0, pageHeight - height));
        int delta = scrollOffset - next;
        if (delta == 0) return;
        scrollOffset = next;
        titleY += delta;
        headingY += delta;
        rowsY += delta;
        stageY += delta;
        captionY += delta;
        for (var child : children()) {
            if (child instanceof AbstractWidget widget) widget.setY(widget.getY() + delta);
        }
    }

    private void reveal(AbstractWidget b) {
        if (b == null) return;
        if (b.getY() < 0) scrollTo(scrollOffset + b.getY() - px(3));
        else if (b.getY() + b.getHeight() > height)
            scrollTo(scrollOffset + b.getY() + b.getHeight() - height + px(3));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (pageHeight > height && !context.isVisible()) {
            scrollTo(scrollOffset - (int) Math.round(delta * px(24)));
            if (!keyboardFocus) updatePointer(x, y);
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (context.isVisible()) context.hide();
            else onClose();
            return true;
        }
        if (context.isVisible()) return true;
        if (key == GLFW.GLFW_KEY_R && modifiers == 0 && refitButton != null && canRefit()
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
    public void onClose() {
        if (editing) {
            editing = false;
            scrollOffset = 0;
            previewSlot = BackpackSlot.PRIMARY;
            resetHover();
            buildControls();
            reveal(bagButtons.get(bagId));
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
