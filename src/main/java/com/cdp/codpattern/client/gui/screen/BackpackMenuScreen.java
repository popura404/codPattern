package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.client.gui.refit.BackPackSelectButton;
import com.cdp.codpattern.client.gui.refit.BackpackActionButton;
import com.cdp.codpattern.client.gui.refit.NewBackpackButton;
import com.cdp.codpattern.client.gui.screen.backpack.BackpackContextMenuCoordinator;
import com.cdp.codpattern.client.gui.screen.backpack.BackpackEscKeyController;
import com.cdp.codpattern.client.gui.screen.backpack.BackpackGridLayout;
import com.cdp.codpattern.client.gui.screen.backpack.BackpackWeaponPreviewPanel;
import com.cdp.codpattern.config.backpack.BackpackClientCache;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.network.DeleteBackpackPacket;
import com.cdp.codpattern.network.RequestBackpackConfigPacket;
import com.cdp.codpattern.network.RequestWeaponFilterPacket;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private Map<Integer, BackPackSelectButton> buttonMap = new LinkedHashMap<>();
    private boolean loading = true;

    // 配置按钮管理（hover时显示"配置"按钮）
    private Map<Integer, List<BackpackActionButton>> actionButtonMap = new HashMap<>();
    private Integer currentActionButtonId = null;
    private int hideDelay = 0;
    private static final int MAX_HIDE_DELAY = 10;

    // 右键上下文菜单
    private final BackpackContextMenuCoordinator contextMenuCoordinator = new BackpackContextMenuCoordinator();

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
        UNIT_LENGTH = Math.max(1, (int) ((float) this.width / 120.f));
        loading = true;

        loadPlayerData();
        addSelectBagButton();
        addNewBackpackButton();
    }

    @Override
    public void render(@NotNull GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderBackground(pGuiGraphics);
        renderHeaderBar(pGuiGraphics);
        renderBackpackGridBackdrop(pGuiGraphics);
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
        boolean isMouseOverContextMenu = contextMenuCoordinator.isMouseOver(pMouseX, pMouseY);

        if (!contextMenuCoordinator.isVisible() && !isMouseOverContextMenu) {
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
        contextMenuCoordinator.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
    }

    private void renderHeaderBar(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        int titleX = UNIT_LENGTH * 6;
        int titleY = UNIT_LENGTH * 4;
        int accentX = Math.max(4, titleX - 10);
        int accentBottom = titleY + mc.font.lineHeight + 1;

        graphics.fill(accentX, titleY - 1, accentX + 3, accentBottom, CodTheme.HOVER_BORDER);
        graphics.drawString(mc.font, "背包选择", titleX, titleY, CodTheme.TEXT_PRIMARY, true);
        graphics.fill(titleX, titleY + mc.font.lineHeight + 4, this.width - titleX, titleY + mc.font.lineHeight + 5,
                CodTheme.DIVIDER);

        int hintRightX = this.width - titleX;
        int hintMaxWidth = Math.max(40, this.width / 3);
        String hint = this.width < UNIT_LENGTH * 36
                ? "[LMB] 选  [RMB] 更多"
                : "[LMB] 选择背包   [RMB] 更多操作";
        GuiTextHelper.drawRightAlignedEllipsizedString(
                graphics,
                mc.font,
                hint,
                hintRightX,
                titleY,
                hintMaxWidth,
                CodTheme.TEXT_SECONDARY,
                false
        );

        if (playerData != null) {
            String counterText = playerData.getBackpackCount() + " / 10";
            int counterX = titleX + mc.font.width("背包选择") + UNIT_LENGTH;
            int counterMaxWidth = hintRightX - hintMaxWidth - counterX - UNIT_LENGTH;
            if (counterMaxWidth >= mc.font.width(counterText)) {
                graphics.drawString(
                        mc.font,
                        counterText,
                        counterX,
                        titleY,
                        CodTheme.TEXT_HOVER,
                        false
                );
            }
        }
    }

    private void renderBackpackGridBackdrop(GuiGraphics graphics) {
        BackpackGridLayout.LayoutMetrics layoutMetrics =
                BackpackGridLayout.metrics(this.width, SCREEN_HEIGHT, UNIT_LENGTH);
        BackpackGridLayout.PanelBounds panel = layoutMetrics.panelBounds();
        int panelLeft = panel.left();
        int panelTop = panel.top();
        int panelRight = panel.right();
        int panelBottom = panel.bottom();

        graphics.fillGradient(panelLeft, panelTop, panelRight, panelBottom, 0x2A202020, 0x3A101010);
        graphics.fillGradient(panelLeft + 1, panelTop + 1, panelRight - 1, panelTop + UNIT_LENGTH + 1,
                0x1CFFFFFF, 0x02000000);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 1, CodTheme.BORDER_SUBTLE);
        graphics.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, CodTheme.BORDER_SUBTLE);
        graphics.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, CodTheme.BORDER_SUBTLE);
        graphics.fill(panelRight - 1, panelTop, panelRight, panelBottom, CodTheme.BORDER_SUBTLE);
        graphics.fill(panelLeft + UNIT_LENGTH, panelTop + UNIT_LENGTH + 1, panelRight - UNIT_LENGTH, panelTop + UNIT_LENGTH + 2,
                CodTheme.DIVIDER);
    }

    private void renderWeaponDisplay(GuiGraphics graphics, int mouseX, int mouseY) {
        BackpackWeaponPreviewPanel.render(
                graphics,
                this.width,
                SCREEN_HEIGHT,
                UNIT_LENGTH,
                currentHoveredButton,
                currentWeaponInfo
        );
    }

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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenuCoordinator.handleMenuClick(mouseX, mouseY, button)) {
            return true;
        }

        // 右键点击 - 检查是否点击在某个背包按钮上
        if (button == 1) {
            for (Map.Entry<Integer, BackPackSelectButton> entry : buttonMap.entrySet()) {
                BackPackSelectButton btn = entry.getValue();
                if (isMouseOverButton(btn, mouseX, mouseY)) {
                    contextMenuCoordinator.show(
                            this,
                            entry.getKey(),
                            btn,
                            (int) mouseX,
                            (int) mouseY,
                            this.width,
                            this.height,
                            this::removeCurrentActionButtons,
                            this::openDeleteConfirm
                    );
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        // 如果右键菜单打开，不传递mouseMoved事件给子组件
        if (contextMenuCoordinator.isVisible()) {
            return;
        }
        super.mouseMoved(mouseX, mouseY);
    }

    private boolean isMouseOverButton(BackPackSelectButton btn, double mouseX, double mouseY) {
        return mouseX >= btn.getX() && mouseX <= btn.getX() + btn.getWidth()
                && mouseY >= btn.getY() && mouseY <= btn.getY() + btn.getHeight();
    }

    private void openDeleteConfirm(int backpackId) {
        Component title = Component.literal("删除背包");
        Component message = Component.literal("确定要删除背包 #" + backpackId + " 吗？");
        Minecraft.getInstance().setScreen(new ConfirmScreen(confirmed -> {
            Minecraft.getInstance().setScreen(this);
            if (confirmed) {
                ModNetworkChannel.sendToServer(new DeleteBackpackPacket(backpackId));
            }
        }, title, message));
    }

    private void loadPlayerData() {
        if (Minecraft.getInstance().player != null) {
            ModNetworkChannel.sendToServer(new RequestWeaponFilterPacket());
            ModNetworkChannel.sendToServer(new RequestBackpackConfigPacket());
            playerData = BackpackClientCache.get();
            if (playerData != null) {
                currentSelectedId = playerData.getSelectedBackpack();
                loading = false;
            }
        }
    }

    private void addSelectBagButton() {
        if (playerData == null) return;

        Map<Integer, BackpackConfig.Backpack> backpacks = playerData.getBackpacks_MAP();
        int totalButtons = getDisplayedButtonCount();
        int buttonIndex = 0;
        for (Map.Entry<Integer, BackpackConfig.Backpack> entry : backpacks.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .toList()) {
            if (buttonIndex >= 10) break;
            int backpackId = entry.getKey();
            BackpackConfig.Backpack backpack = entry.getValue();
            buttonIndex++;
            BackpackGridLayout.ButtonPosition pos = BackpackGridLayout.selectButtonPosition(
                    buttonIndex, totalButtons, this.width, SCREEN_HEIGHT, UNIT_LENGTH);
            BackPackSelectButton button = new BackPackSelectButton(
                    pos.x(), pos.y(),
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
            BackpackGridLayout.ButtonPosition pos = BackpackGridLayout.selectButtonPosition(
                    buttonPosition, getDisplayedButtonCount(), this.width, SCREEN_HEIGHT, UNIT_LENGTH);
            NewBackpackButton addButton = new NewBackpackButton(
                    pos.x(), pos.y(), UNIT_LENGTH * 20, UNIT_LENGTH * 5, backpackCount);
            addRenderableWidget(addButton);
        }
    }

    private int getDisplayedButtonCount() {
        if (playerData == null) {
            return 0;
        }
        int backpackCount = Math.min(playerData.getBackpackCount(), 10);
        return backpackCount < 10 ? backpackCount + 1 : backpackCount;
    }

    @Override
    public void onClose() {
        removeCurrentActionButtons();
        contextMenuCoordinator.hide();
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
        return BackpackEscKeyController.handleKeyPressed(pKeyCode, contextMenuCoordinator, this::ESCto);
    }

    public void reloadFromPlayerData() {
        this.playerData = BackpackClientCache.get();
        if (this.playerData == null) {
            loading = true;
            this.buttonMap.clear();
            this.actionButtonMap.clear();
            this.currentActionButtonId = null;
            contextMenuCoordinator.hide();
            this.clearWidgets();
            return;
        }
        loading = false;
        this.currentSelectedId = playerData.getSelectedBackpack();
        this.buttonMap.clear();
        this.actionButtonMap.clear();
        this.currentActionButtonId = null;
        contextMenuCoordinator.hide();
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
