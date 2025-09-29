package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.client.gui.refit.BackPackButton;
import com.cdp.codpattern.client.gui.refit.SecodnButton;
import com.cdp.codpattern.client.gui.refit.addBackpackButton;
import com.cdp.codpattern.config.configmanager.BackpackConfigManager;
import com.cdp.codpattern.config.server.BackpackSelectionConfig;
import com.cdp.codpattern.network.RequestConfigPacket;
import com.cdp.codpattern.network.handler.PacketHandler;
import com.mojang.blaze3d.systems.RenderSystem;
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
    private BackpackSelectionConfig.PlayerBackpackData playerData;
    private int currentSelectedId;
    private Map<Integer,BackPackButton> buttonMap = new HashMap<>();

    // SecondButton管理
    private Map<Integer, SecodnButton> secondButtonMap = new HashMap<>();
    private Integer currentSecondButtonId = null;
    private int hideDelay = 0;
    private static final int MAX_HIDE_DELAY = 10; // 延迟10个tick（0.5s）

    // 武器信息显示相关
    private BackPackButton currentHoveredButton = null;
    private Map<String, BackPackButton.WeaponInfo> currentWeaponInfo = null;
    private int weaponDisplayX = 0;
    private int weaponDisplayY = 0;

    public BackpackMenuScreen() {
        super(Component.literal("Select your bag"));
    }

    @Override
    public void render(@NotNull GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderBackground(pGuiGraphics);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);

        // 渲染武器信息
        renderWeaponDisplay(pGuiGraphics, pMouseX, pMouseY);

        // 检查悬停状态
        boolean isHoveringAnyButton = false;
        Integer hoveredButtonId = null;
        BackPackButton hoveredButton = null;

        // 是否悬停在BackPackButton上
        for (Map.Entry<Integer,BackPackButton> entry : buttonMap.entrySet()){
            BackPackButton button = entry.getValue();
            if(button.isHoveredOrFocused()){
                isHoveringAnyButton = true;
                hoveredButtonId = entry.getKey();
                hoveredButton = button;
                break;
            }
        }

        // 更新当前悬停的按钮和武器信息
        if (hoveredButton != null && hoveredButton != currentHoveredButton) {
            currentHoveredButton = hoveredButton;
            currentWeaponInfo = hoveredButton.getWeaponInfoCache();
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
     * 渲染武器显示区域
     */
    private void renderWeaponDisplay(GuiGraphics graphics, int mouseX, int mouseY) {
        if (currentWeaponInfo == null || currentWeaponInfo.isEmpty() || currentHoveredButton == null) return;

        int weaponDisplayX = UNIT_LENGTH * 6;
        int weaponDisplayY = SCREEN_HEIGHT - UNIT_LENGTH * 22 - UNIT_LENGTH * 4 - UNIT_LENGTH * 12;

        // 确保不超出屏幕顶部边界
        if (weaponDisplayY < 10) {
            weaponDisplayY = 10;
        }

        // 背景面板
        graphics.fillGradient(weaponDisplayX, weaponDisplayY - 10, //微调,
                this.width - weaponDisplayX,
                weaponDisplayY + UNIT_LENGTH * 12 + 10,
                0x05999988, 0x20AAAAAA);

        // 边框
        graphics.fill(weaponDisplayX, weaponDisplayY - UNIT_LENGTH,
                this.width - weaponDisplayX, weaponDisplayY + UNIT_LENGTH, 0x46AAAAAA);
        graphics.fill(weaponDisplayX, weaponDisplayY + UNIT_LENGTH * 12 + 10,
                this.width - weaponDisplayX, weaponDisplayY + UNIT_LENGTH * 12 + 11, 0x2AAAAAAA);
        graphics.fill(weaponDisplayX, weaponDisplayY - 10,
                weaponDisplayX, weaponDisplayY + UNIT_LENGTH * 12 + 10, 0x38AAAAAA);
        graphics.fill(this.width - weaponDisplayX, weaponDisplayY - 10,
                this.width - weaponDisplayX, weaponDisplayY + UNIT_LENGTH * 12 + 10, 0x38AAAAAA);

        // 渲染标题
        String title = "§e§l" + currentHoveredButton.getBackpack().getName() + " §7(#" + currentHoveredButton.getBAGSERIAL() + ")";
        graphics.drawString(Minecraft.getInstance().font, title,
                weaponDisplayX + 5, weaponDisplayY, 0xFFFFFF, true);

        // 分割线
        int dividerX = weaponDisplayX + UNIT_LENGTH * 25 - 1;
        graphics.fillGradient(dividerX, weaponDisplayY + UNIT_LENGTH * 2,
                dividerX + 2, weaponDisplayY + UNIT_LENGTH * 11,
                0x40808080, 0x20404040);

        // 渲染武器信息
        for (Map.Entry<String, BackPackButton.WeaponInfo> entry : currentWeaponInfo.entrySet()) {
            String type = entry.getKey();
            BackPackButton.WeaponInfo info = entry.getValue();

            // 计算武器区域位置
            int offsetX;
            if (type.equals("primary")) {
                offsetX = UNIT_LENGTH * 3;
            } else {
                offsetX = UNIT_LENGTH * 29;
            }

            int weaponX = weaponDisplayX + offsetX;
            int weaponY = weaponDisplayY + UNIT_LENGTH * 2;

            // 渲染武器区域背景
            //graphics.fillGradient(weaponX, weaponY,
            //        weaponX + UNIT_LENGTH * 18,
            //        weaponY + UNIT_LENGTH * 9,
            //        0x20303030, 0x1C202020);  // 相应调整内部区域透明度

            // 渲染武器类型标签
            String typeLabel = type.equals("primary") ? "§c主武器" : "§9副武器";
            graphics.drawString(Minecraft.getInstance().font, typeLabel,
                    weaponX + 2, weaponY + 2, 0xFFFFFF, true);

            // 渲染武器贴图 - 修改为与FlatColorButton相同的大小和位置
            if (info.texture != null) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

                // 使用与FlatColorButton相同的贴图尺寸
                int textureWidth = UNIT_LENGTH * 18;
                int textureHeight = UNIT_LENGTH * 6;

                // 贴图位置居中
                int textureX = weaponX + UNIT_LENGTH * 0;  // 左对齐
                int textureY = weaponY + UNIT_LENGTH * 2;  // 给标签留空间

                graphics.blit(info.texture, textureX, textureY,
                        0, 0, textureWidth, textureHeight,
                        textureWidth, textureHeight);
            }

            // 渲染枪包名
            if (info.packName != null) {
                graphics.drawString(Minecraft.getInstance().font, info.packName,
                        weaponX + 2, weaponY + UNIT_LENGTH * 7, 0xDDFFFFFF);
            }

            // 渲染武器名
            if (info.weaponName != null) {
                graphics.drawString(Minecraft.getInstance().font, info.weaponName,
                        weaponX + 2, weaponY + UNIT_LENGTH * 8, 0xDDFFFFFF);
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
            secondButtonMap.remove(currentSecondButtonId);
            currentSecondButtonId = null;
        }
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics pGuiGraphics) {
        pGuiGraphics.fillGradient(0, 0, this.width, this.height, 0xA0202020, 0xD0000000);
    }

    public void init() {
        super.init();

        // 向服务端请求配置
        PacketHandler.sendToServer(new RequestConfigPacket());

        this.SCREEN_HEIGHT = this.height;
        this.SCREEN_WIDTH = this.width;
        UNIT_LENGTH = (int) (this.width / 120.f);
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

        Map<Integer, BackpackSelectionConfig.Backpack> backpacks = playerData.getBackpacks_MAP();
        int buttonIndex = 0;
        for (Map.Entry<Integer, BackpackSelectionConfig.Backpack> entry : backpacks.entrySet()) {
            if (buttonIndex >= 10) break;
            int backpackId = entry.getKey();
            BackpackSelectionConfig.Backpack backpack = entry.getValue();
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
        // 清理武器信息
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
