package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.client.gui.refit.BackPackButton;
import com.cdp.codpattern.client.gui.refit.SecodnButton;
import com.cdp.codpattern.client.gui.refit.addBackpackButton;
import com.cdp.codpattern.config.configmanager.BackpackConfigManager;
import com.cdp.codpattern.config.server.BagSelectionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class BackpackMenuScreen extends Screen {

    public int SCREEN_HEIGHT = 0;
    public int SCREEN_WIDTH = 0;
    public int UNIT_LENGTH = 0;
    private BagSelectionConfig.PlayerBackpackData playerData;
    private int currentSelectedId;
    private Map<Integer,BackPackButton> buttonMap = new HashMap<>();

    // SecondButton管理
    private Map<Integer, SecodnButton> secondButtonMap = new HashMap<>();
    private Integer currentSecondButtonId = null;
    private int hideDelay = 0;
    private static final int MAX_HIDE_DELAY = 10; // 延迟10个tick（0.5s）

    public BackpackMenuScreen() {
        super(Component.literal("Select your bag"));
    }

    @Override
    public void render(@NotNull GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderBackground(pGuiGraphics);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);

        // 检查悬停状态
        boolean isHoveringAnyButton = false;
        Integer hoveredButtonId = null;

        // 是否悬停在BackPackButton上
        for (Map.Entry<Integer,BackPackButton> entry : buttonMap.entrySet()){
            BackPackButton button = entry.getValue();
            if(button.isHoveredOrFocused()){
                isHoveringAnyButton = true;
                hoveredButtonId = entry.getKey();
                break;
            }
        }

        // 是否悬停在自己SecondButton上
        if (currentSecondButtonId != null && secondButtonMap.containsKey(currentSecondButtonId)) {
            SecodnButton secondButton = secondButtonMap.get(currentSecondButtonId);
            if (secondButton.isHoveredOrFocused()) {
                isHoveringAnyButton = true;
                hoveredButtonId = currentSecondButtonId;
            }
        }

        // 处理SecondButton显示逻辑
        if (isHoveringAnyButton && hoveredButtonId != null) {
            hideDelay = 0; // 重置延迟

            // 如果悬停的按钮与当前显示的不同，切换SecondButton
            if (!hoveredButtonId.equals(currentSecondButtonId)) {
                removeCurrentSecondButton();
                addSecondButton(hoveredButtonId);
            }
        } else {
            // 没有悬停时开始延迟计时
            if (currentSecondButtonId != null) {
                hideDelay++;
                if (hideDelay >= MAX_HIDE_DELAY) {
                    removeCurrentSecondButton();
                    hideDelay = 0;
                }
            }
        }
    }

    /**
     * 添加SecondButton
     */
    private void addSecondButton(Integer buttonId) {
        if (buttonMap.containsKey(buttonId)) {
            BackPackButton backPackButton = buttonMap.get(buttonId);
            SecodnButton secondButton = new SecodnButton(backPackButton);
            secondButtonMap.put(buttonId, secondButton);
            addRenderableWidget(secondButton);
            currentSecondButtonId = buttonId;
        }
    }

    /**
     * 移除当前的SecondButton
     */
    private void removeCurrentSecondButton() {
        if (currentSecondButtonId != null && secondButtonMap.containsKey(currentSecondButtonId)) {
            SecodnButton secondButton = secondButtonMap.get(currentSecondButtonId);
            removeWidget(secondButton);
            //TODO: xi xi
            secondButtonMap.remove(currentSecondButtonId);
            currentSecondButtonId = null;
        }
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics pGuiGraphics) {
        pGuiGraphics.fillGradient(0, 0, this.width, this.height, 0x90202020, 0xC0000000);
    }

    public void init() {
        super.init();
        this.SCREEN_HEIGHT = this.height;
        this.SCREEN_WIDTH = this.width;
        UNIT_LENGTH = (int) (this.width / 120f);
        loadPlayerData();
        addSelectBagButton();
        addNewBackpackButton();
    }

    private void loadPlayerData() {
        if (Minecraft.getInstance().player != null) {
            String uuid = Minecraft.getInstance().player.getUUID().toString();
            playerData = BackpackConfigManager.getConfig().getOrCreatePlayerData(uuid);
            currentSelectedId = playerData.getSelectedBackpack();
        }
    }

    private void addSelectBagButton() {
        if (playerData == null) return;

        Map<Integer, BagSelectionConfig.Backpack> backpacks = playerData.getBackpacks_MAP();
        int backpackCount = Math.min(backpacks.size(), 10);
        int buttonIndex = 0;
        for (Map.Entry<Integer, BagSelectionConfig.Backpack> entry : backpacks.entrySet()) {
            if (buttonIndex >= 10) break;
            int backpackId = entry.getKey();
            BagSelectionConfig.Backpack backpack = entry.getValue();
            buttonIndex++;
            int X, Y;
            if (buttonIndex <= 5) {
                X = UNIT_LENGTH * 8 + ((buttonIndex - 1) * UNIT_LENGTH * 21);
                Y = UNIT_LENGTH * 22;
            } else {
                X = UNIT_LENGTH * 8 + ((buttonIndex - 6) * UNIT_LENGTH * 21);
                Y = UNIT_LENGTH * 13;
            }
            BackPackButton button = new BackPackButton(X, SCREEN_HEIGHT - Y, UNIT_LENGTH * 20, UNIT_LENGTH * 5, backpackId, backpack, backpackId == currentSelectedId);
            buttonMap.put(backpackId,button);
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
            addBackpackButton addButton = new addBackpackButton(X, SCREEN_HEIGHT - Y, UNIT_LENGTH * 20, UNIT_LENGTH * 5, backpackCount);
            addRenderableWidget(addButton);
        }
    }

    @Override
    public void onClose() {
        // 清理SecondButton
        removeCurrentSecondButton();
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
        if (pKeyCode == 256) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playNotifySound(SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 1f, 1f);
                }
            });
            this.ESCto();
        }
        return true;
    }

    public void ESCto() {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(null);
        });
    }
}
