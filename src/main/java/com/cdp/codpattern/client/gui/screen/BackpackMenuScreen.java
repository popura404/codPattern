package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.refit.BackPackSelectButton;
import com.cdp.codpattern.client.gui.refit.BackpackActionButton;
import com.cdp.codpattern.client.gui.refit.BackpackContextMenu;
import com.cdp.codpattern.client.gui.refit.NewBackpackButton;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfigManager;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfig;
import com.cdp.codpattern.config.WeaponFilterConfig.WeaponFilterConfig;
import com.cdp.codpattern.network.CloneBackpackPacket;
import com.cdp.codpattern.network.DeleteBackpackPacket;
import com.cdp.codpattern.network.RequestBackpackConfigPacket;
import com.cdp.codpattern.network.handler.PacketHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 背包选择菜单
 */
public class BackpackMenuScreen extends Screen {

    public int SCREEN_HEIGHT = 0;
    public int SCREEN_WIDTH = 0;
    public int UNIT_LENGTH = 0;
    private BackpackConfig.PlayerBackpackData playerData;
    private int currentSelectedId;
    private Map<Integer, BackPackSelectButton> buttonMap = new HashMap<>();
    private boolean loading = true;

    // 配置按钮管理（hover时显示"配置"按钮）
    private Map<Integer, List<BackpackActionButton>> actionButtonMap = new HashMap<>();
    private Integer currentActionButtonId = null;
    private int hideDelay = 0;
    private static final int MAX_HIDE_DELAY = 10;

    // 右键上下文菜单
    private BackpackContextMenu contextMenu;
    private Integer contextMenuTargetId = null;

    // 武器信息显示相关
    private BackPackSelectButton currentHoveredButton = null;
    private Map<String, BackPackSelectButton.WeaponInfo> currentWeaponInfo = null;

    public BackpackMenuScreen() {
        super(Component.literal("Select your bag"));
    }

    public void init() {
        super.init();

        this.SCREEN_HEIGHT = this.height;
        this.SCREEN_WIDTH = this.width;
        UNIT_LENGTH = (int) ((float)this.width / 120.f);
        loading = true;

        // 初始化右键菜单
        contextMenu = new BackpackContextMenu();
        contextMenu.setOnClose(menu -> contextMenuTargetId = null);

        loadPlayerData();
        addSelectBagButton();
        addNewBackpackButton();
    }

    @Override
    public void render(@NotNull GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderBackground(pGuiGraphics);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);

        // 渲染武器信息
        renderWeaponDisplay(pGuiGraphics, pMouseX, pMouseY);

        if (loading) {
            String loadingText = "加载中...";
            pGuiGraphics.drawCenteredString(
                    Minecraft.getInstance().font,
                    Component.literal(loadingText),
                    this.width / 2,
                    this.height / 2,
                    0xFFFFFF
            );
        }

        // 如果右键菜单打开，或鼠标在菜单区域内，不处理hover逻辑
        boolean isMouseOverContextMenu = contextMenu != null && contextMenu.isVisible() &&
                contextMenu.isMouseOver(pMouseX, pMouseY);

        if ((contextMenu == null || !contextMenu.isVisible()) && !isMouseOverContextMenu) {
            // 检查悬停状态
            boolean isHoveringAnyButton = false;
            Integer hoveredButtonId = null;
            BackPackSelectButton hoveredButton = null;

            for (Map.Entry<Integer, BackPackSelectButton> entry : buttonMap.entrySet()) {
                BackPackSelectButton button = entry.getValue();
                if (button.isHoveredOrFocused()) {
                    isHoveringAnyButton = true;
                    hoveredButtonId = entry.getKey();
                    hoveredButton = button;
                    break;
                }
            }

            // 更新当前悬停的按钮包含的武器信息
            if (hoveredButton != null && hoveredButton != currentHoveredButton) {
                currentHoveredButton = hoveredButton;
                currentWeaponInfo = hoveredButton.getWeaponInfoCache();
            }

            // 是否悬停在动作按钮上
            if (currentActionButtonId != null && actionButtonMap.containsKey(currentActionButtonId)) {
                for (BackpackActionButton actionButton : actionButtonMap.get(currentActionButtonId)) {
                    if (actionButton.isHoveredOrFocused()) {
                        isHoveringAnyButton = true;
                        hoveredButtonId = currentActionButtonId;
                        break;
                    }
                }
            }

            // 处理动作按钮显示逻辑
            if (isHoveringAnyButton && hoveredButtonId != null) {
                hideDelay = 0;
                if (!hoveredButtonId.equals(currentActionButtonId)) {
                    removeCurrentActionButtons();
                    addActionButtons(hoveredButtonId);
                }
            } else {
                if (currentActionButtonId != null) {
                    hideDelay++;
                    if (hideDelay >= MAX_HIDE_DELAY) {
                        removeCurrentActionButtons();
                        hideDelay = 0;
                    }
                }
            }
        }

        // 最后渲染右键菜单（保证在最上层）
        if (contextMenu != null && contextMenu.isVisible()) {
            contextMenu.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        }
    }

    /**
     * 渲染武器显示区域
     */
    private void renderWeaponDisplay(GuiGraphics graphics, int mouseX, int mouseY) {
        if (currentWeaponInfo == null || currentWeaponInfo.isEmpty() || currentHoveredButton == null) return;

        Minecraft mc = Minecraft.getInstance();
        int panelPadding = UNIT_LENGTH;
        int weaponDisplayX = UNIT_LENGTH * 6;
        int weaponDisplayY = SCREEN_HEIGHT - UNIT_LENGTH * 22 - UNIT_LENGTH * 4 - UNIT_LENGTH * 12;

        // 确保不超出屏幕顶部边界
        if (weaponDisplayY < 10) {
            weaponDisplayY = 10;
        }

        int panelRight = this.width - weaponDisplayX;
        int panelBottom = weaponDisplayY + UNIT_LENGTH * 13;

        // 背景面板
        graphics.fillGradient(weaponDisplayX, weaponDisplayY - UNIT_LENGTH - 2,
                panelRight, panelBottom,
                0x05999988, 0x20AAAAAA);

        // 灰条（标题栏）
        int grayBarTop = weaponDisplayY - UNIT_LENGTH - 2;
        int grayBarBottom = weaponDisplayY + UNIT_LENGTH;
        graphics.fill(weaponDisplayX, grayBarTop, panelRight, grayBarBottom, 0x46AAAAAA);

        // 底部边线
        graphics.fill(weaponDisplayX, panelBottom - 1, panelRight, panelBottom, 0x2AAAAAAA);
        // 左边线
        graphics.fill(weaponDisplayX, grayBarTop, weaponDisplayX + 1, panelBottom, 0x38AAAAAA);
        // 右边线
        graphics.fill(panelRight - 1, grayBarTop, panelRight, panelBottom, 0x38AAAAAA);

        // 四列：主武器 | 副武器 | 投掷物 1 | 投掷物 2（主武器与副武器等宽，投掷物紧凑不平摊到右边）
        int dividerX = weaponDisplayX + UNIT_LENGTH * 25 - 1;  // 主武器列宽
        int secondaryColumnWidth = UNIT_LENGTH * 25;            // 副武器列宽与主武器一致
        int throwableColumnWidth = UNIT_LENGTH * 8;            // 投掷物列紧凑
        int dividerSecTac = dividerX + secondaryColumnWidth;   // 副武器 | 投掷物 1
        int dividerTacLeth = dividerSecTac + throwableColumnWidth; // 投掷物 1 | 投掷物 2

        // 武器内容区域（四列各自独立 X，投掷物不占满右侧）
        int primaryWeaponX = weaponDisplayX + panelPadding + 4;
        int secondaryWeaponX = dividerX + panelPadding;
        int tacticalWeaponX = dividerSecTac + panelPadding;
        int lethalWeaponX = dividerTacLeth + panelPadding;

        // 背包名称
        String displayName = currentHoveredButton.getDisplayNameRaw();
        String title = "§e§l" + displayName + " §7#" + currentHoveredButton.getBAGSERIAL();
        int titleY = grayBarTop + (grayBarBottom - grayBarTop - mc.font.lineHeight) / 2;
        graphics.drawString(mc.font, title, primaryWeaponX, titleY, 0xFFFFFF, true);

        // 分割线（主|副、副|投1、投1|投2）
        graphics.fillGradient(dividerX, weaponDisplayY + UNIT_LENGTH,
                dividerX + 1, panelBottom - UNIT_LENGTH,
                0x40808080, 0x20404040);
        graphics.fillGradient(dividerSecTac, weaponDisplayY + UNIT_LENGTH,
                dividerSecTac + 1, panelBottom - UNIT_LENGTH,
                0x40808080, 0x20404040);
        graphics.fillGradient(dividerTacLeth, weaponDisplayY + UNIT_LENGTH,
                dividerTacLeth + 1, panelBottom - UNIT_LENGTH,
                0x40808080, 0x20404040);

        // 投掷物是否启用（禁用时投掷物槽半透明）
        WeaponFilterConfig filterConfig = WeaponFilterConfig.getCLIENTweaponFilterConfig();
        boolean throwablesEnabled = filterConfig == null || filterConfig.isThrowablesEnabled();

        // 渲染武器信息（主武器、副武器、战术、杀伤）
        for (Map.Entry<String, BackPackSelectButton.WeaponInfo> entry : currentWeaponInfo.entrySet()) {
            String type = entry.getKey();
            BackPackSelectButton.WeaponInfo info = entry.getValue();

            int weaponX = switch (type) {
                case "primary" -> primaryWeaponX;
                case "secondary" -> secondaryWeaponX;
                case "tactical" -> tacticalWeaponX;
                case "lethal" -> lethalWeaponX;
                default -> primaryWeaponX;
            };
            int weaponY = weaponDisplayY + UNIT_LENGTH + 2;

            boolean isThrowableSlot = "tactical".equals(type) || "lethal".equals(type);
            boolean drawThrowableDimmed = isThrowableSlot && !throwablesEnabled;

            String typeLabel = switch (type) {
                case "primary" -> "§c主武器";
                case "secondary" -> "§9副武器";
                case "tactical" -> "§b投掷物 1";
                case "lethal" -> "§6投掷物 2";
                default -> type;
            };
            graphics.drawString(mc.font, typeLabel, weaponX, weaponY, 0xFFFFFF, true);

            if (drawThrowableDimmed) {
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1f, 1f, 1f, 0.22f);
            }
            float scale = isThrowableSlot ? 2f : 3f;
            int slotContentHeight = isThrowableSlot ? UNIT_LENGTH * 5 : UNIT_LENGTH * 7;
            // 副武器、投掷物1、投掷物2 三栏物品名同一基线（红横线对齐）
            int nameY = weaponY + mc.font.lineHeight + (isThrowableSlot ? UNIT_LENGTH * 7 : slotContentHeight) + 2;

            if (info.texture != null && !isThrowableSlot) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                int textureWidth = UNIT_LENGTH * 18;
                int textureHeight = UNIT_LENGTH * 6;
                int textureX = weaponX;
                int textureY = weaponY + mc.font.lineHeight + 2;
                graphics.blit(info.texture, textureX, textureY,
                        0, 0, textureWidth, textureHeight,
                        textureWidth, textureHeight);
            } else if (info.itemStack != null && !info.itemStack.isEmpty()) {
                int itemSize = (int) (16 * scale);
                int maxSlotWidth = isThrowableSlot ? (throwableColumnWidth - panelPadding * 2) : (UNIT_LENGTH * 18);
                int itemX = weaponX + Math.max(0, (maxSlotWidth - itemSize) / 2);
                int itemY;
                if (isThrowableSlot) {
                    int contentTop = weaponY + mc.font.lineHeight + 2;
                    int contentBottom = nameY - 2;
                    int contentHeight = Math.max(1, contentBottom - contentTop);
                    itemY = contentTop + (contentHeight - itemSize) / 2;
                } else {
                    itemY = weaponY + mc.font.lineHeight + 2;
                }

                graphics.pose().pushPose();
                graphics.pose().translate(itemX, itemY, 0);
                graphics.pose().scale(scale, scale, 1);
                graphics.renderItem(info.itemStack, 0, 0);
                graphics.pose().popPose();
            }

            if (info.weaponName != null) {
                graphics.drawString(mc.font, info.weaponName, weaponX, nameY, 0xFFFFFFFF, false);
            }
            if (info.packName != null && !isThrowableSlot) {
                int packY = weaponY + mc.font.lineHeight * 2 + UNIT_LENGTH * 7 + 6;
                graphics.drawString(mc.font, info.packName, weaponX, packY, 0xAAFFFFFF, false);
            }

            if (drawThrowableDimmed) {
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                RenderSystem.disableBlend();
            }
        }
    }

    /**
     * 添加动作按钮
     */
    private void addActionButtons(Integer buttonId) {
        if (!buttonMap.containsKey(buttonId)) {
            return;
        }
        BackPackSelectButton backPackSelectButton = buttonMap.get(buttonId);
        int unit = Math.max(1, backPackSelectButton.getWidth() / 20);
        int subHeight = 2 * unit + 6;
        int baseX = backPackSelectButton.getX();
        int baseY = backPackSelectButton.getY() + backPackSelectButton.getHeight();
        int totalWidth = backPackSelectButton.getWidth();

        List<BackpackActionButton> actions = new ArrayList<>();

        // 只添加"配置"按钮，重命名/复制/删除移到右键菜单
        actions.add(new BackpackActionButton(
                baseX,
                baseY,
                totalWidth,
                subHeight,
                Component.literal("配置"),
                btn -> Minecraft.getInstance().setScreen(new WeaponMenuScreen(backPackSelectButton.getBackpack(), backPackSelectButton.getBAGSERIAL())),
                0xFFFFFF,
                CodTheme.TEXT_HOVER
        ));

        actionButtonMap.put(buttonId, actions);
        for (BackpackActionButton actionButton : actions) {
            addRenderableWidget(actionButton);
        }
        currentActionButtonId = buttonId;
    }

    /**
     * 移除动作按钮
     */
    private void removeCurrentActionButtons() {
        if (currentActionButtonId != null && actionButtonMap.containsKey(currentActionButtonId)) {
            for (BackpackActionButton actionButton : actionButtonMap.get(currentActionButtonId)) {
                removeWidget(actionButton);
            }
            actionButtonMap.remove(currentActionButtonId);
            currentActionButtonId = null;
        }
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics pGuiGraphics) {
        pGuiGraphics.fillGradient(0, 0, this.width, this.height, CodTheme.BG_TOP, CodTheme.BG_BOTTOM);
    }

    /**
     * 鼠标右键菜单
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 如果右键菜单打开，优先处理菜单点击
        if (contextMenu != null && contextMenu.isVisible()) {
            boolean handled = contextMenu.mouseClicked(mouseX, mouseY, button);
            if (handled) {
                return true;
            }
            // 点击菜单外部，关闭菜单，但不传递事件
            contextMenu.hide();
            return true;
        }

        // 右键点击 - 检查是否点击在某个背包按钮上
        if (button == 1) {
            for (Map.Entry<Integer, BackPackSelectButton> entry : buttonMap.entrySet()) {
                BackPackSelectButton btn = entry.getValue();
                if (isMouseOverButton(btn, mouseX, mouseY)) {
                    showContextMenu(entry.getKey(), btn, (int) mouseX, (int) mouseY);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 阻止右键菜单打开时的hover穿透
     */
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        // 如果右键菜单打开，不传递mouseMoved事件给子组件
        if (contextMenu != null && contextMenu.isVisible()) {
            return;
        }
        super.mouseMoved(mouseX, mouseY);
    }

    /**
     * 检查鼠标是否在按钮上
     */
    private boolean isMouseOverButton(BackPackSelectButton btn, double mouseX, double mouseY) {
        return mouseX >= btn.getX() && mouseX <= btn.getX() + btn.getWidth()
                && mouseY >= btn.getY() && mouseY <= btn.getY() + btn.getHeight();
    }

    /**
     * 显示右键上下文菜单
     */
    private void showContextMenu(int backpackId, BackPackSelectButton btn, int mouseX, int mouseY) {
        if (contextMenu == null) return;

        // 先移除当前的动作按钮，避免干扰
        removeCurrentActionButtons();

        contextMenu.clearItems();
        contextMenuTargetId = backpackId;

        // 添加菜单项
        contextMenu.addItem("重命名", () -> {
            Minecraft.getInstance().setScreen(new RenameBackpackScreen(
                    this, backpackId, btn.getBackpack().getName()));
        });

        contextMenu.addItem("复制", () -> {
            PacketHandler.sendToServer(new CloneBackpackPacket(backpackId));
        });

        contextMenu.addItem("删除", () -> {
            openDeleteConfirm(backpackId);
        }, CodTheme.TEXT_DANGER, CodTheme.TEXT_DANGER);

        // 显示在鼠标位置
        contextMenu.show(mouseX, mouseY, this.width, this.height);

        // 播放菜单打开音效
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playNotifySound(
                        SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                        SoundSource.PLAYERS, 0.5f, 1.3f);
            }
        });
    }

    private void openDeleteConfirm(int backpackId) {
        Component title = Component.literal("删除背包");
        Component message = Component.literal("确定要删除背包 #" + backpackId + " 吗？");
        Minecraft.getInstance().setScreen(new ConfirmScreen(confirmed -> {
            Minecraft.getInstance().setScreen(this);
            if (confirmed) {
                PacketHandler.sendToServer(new DeleteBackpackPacket(backpackId));
            }
        }, title, message));
    }

    private void loadPlayerData() {
        if (Minecraft.getInstance().player != null) {
            PacketHandler.sendToServer(new RequestBackpackConfigPacket());
            playerData = BackpackConfigManager.getCLIENTplayerBackpackData();
            if (playerData != null) {
                currentSelectedId = playerData.getSelectedBackpack();
                loading = false;
            }
        }
    }

    private void addSelectBagButton() {
        if (playerData == null) return;

        Map<Integer, BackpackConfig.Backpack> backpacks = playerData.getBackpacks_MAP();
        int buttonIndex = 0;
        for (Map.Entry<Integer, BackpackConfig.Backpack> entry : backpacks.entrySet()) {
            if (buttonIndex >= 10) break;
            int backpackId = entry.getKey();
            BackpackConfig.Backpack backpack = entry.getValue();
            buttonIndex++;
            int X, Y;
            if (buttonIndex <= 5) {
                X = UNIT_LENGTH * 8 + ((buttonIndex - 1) * UNIT_LENGTH * 21);
                Y = UNIT_LENGTH * 22;
            } else {
                X = UNIT_LENGTH * 8 + ((buttonIndex - 6) * UNIT_LENGTH * 21);
                Y = UNIT_LENGTH * 13;
            }
            BackPackSelectButton button = new BackPackSelectButton(
                    X, SCREEN_HEIGHT - Y,
                    UNIT_LENGTH * 20, UNIT_LENGTH * 5,
                    backpackId, backpack,
                    backpackId == currentSelectedId
            );
            buttonMap.put(backpackId, button);
            addRenderableWidget(button);
        }
    }

    private void addNewBackpackButton() {
        if (playerData == null) return;
        int backpackCount = playerData.getBackpackCount();
        if (backpackCount < 10) {
            int buttonPosition = backpackCount + 1;
            int X, Y;
            if (buttonPosition <= 5) {
                X = UNIT_LENGTH * 8 + ((buttonPosition - 1) * UNIT_LENGTH * 21);
                Y = UNIT_LENGTH * 22;
            } else {
                X = UNIT_LENGTH * 8 + ((buttonPosition - 6) * UNIT_LENGTH * 21);
                Y = UNIT_LENGTH * 13;
            }
            NewBackpackButton addButton = new NewBackpackButton(X, SCREEN_HEIGHT - Y, UNIT_LENGTH * 20, UNIT_LENGTH * 5, backpackCount);
            addRenderableWidget(addButton);
        }
    }

    @Override
    public void onClose() {
        removeCurrentActionButtons();
        if (contextMenu != null) {
            contextMenu.hide();
        }
        currentHoveredButton = null;
        currentWeaponInfo = null;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        // ESC 键处理
        if (pKeyCode == 256) {
            // 如果菜单打开，先关闭菜单
            if (contextMenu != null && contextMenu.isVisible()) {
                contextMenu.hide();
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.playNotifySound(
                                SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 0.5f, 1f);
                    }
                });
                return true;
            }
            // 否则关闭整个界面
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playNotifySound(
                            SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 1f, 1f);
                }
            });
            this.ESCto();
            return true;
        }
        return true;
    }

    public void reloadFromPlayerData() {
        this.playerData = BackpackConfigManager.getCLIENTplayerBackpackData();
        if (this.playerData == null) {
            loading = true;
            this.buttonMap.clear();
            this.actionButtonMap.clear();
            this.currentActionButtonId = null;
            if (contextMenu != null) contextMenu.hide();
            contextMenuTargetId = null;
            this.clearWidgets();
            return;
        }
        loading = false;
        this.currentSelectedId = playerData.getSelectedBackpack();
        this.buttonMap.clear();
        this.actionButtonMap.clear();
        this.currentActionButtonId = null;
        if (contextMenu != null) contextMenu.hide();
        contextMenuTargetId = null;
        this.clearWidgets();
        addSelectBagButton();
        addNewBackpackButton();
    }

    public void ESCto() {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(null);
        });
    }
}
