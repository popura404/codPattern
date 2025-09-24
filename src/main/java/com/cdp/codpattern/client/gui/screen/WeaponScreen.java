package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.client.gui.refit.FlatColorButton;
import com.cdp.codpattern.client.gui.refit.WeaponSelectionButton;
import com.cdp.codpattern.config.server.BagSelectionConfig;
import com.cdp.codpattern.config.server.WeaponFilterConfig;
import com.cdp.codpattern.network.UpdateWeaponPacket;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import com.cdp.codpattern.network.handler.PacketHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WeaponScreen extends Screen {

    public int SCREEN_HEIGHT = 0;
    public int SCREEN_WIDTH = 0;
    private int UNIT_LENGTH = 0;
    private final Integer BAGSERIAL;
    private final BagSelectionConfig.Backpack backpack;
    private final boolean isPrimary;
    private final WeaponMenuScreen parentScreen;

    private final Map<String, List<ItemStack>> weaponsByTab = new LinkedHashMap<>();
    private final List<TabButton> tabButtons = new ArrayList<>();
    private String currentTab = "";

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private final List<ItemStack> currentWeapons = new ArrayList<>();
    private final List<WeaponSelectionButton> weaponButtons = new ArrayList<>();

    private static final int BUTTONS_PER_ROW = 5;
    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_SPACING = 2;

    public WeaponScreen(WeaponMenuScreen parent, BagSelectionConfig.Backpack backpack,
                        Integer bagSerial, boolean isPrimary) {
        super(Component.literal("WeaponScreen"));
        this.parentScreen = parent;
        this.BAGSERIAL = bagSerial;
        this.backpack = backpack;
        this.isPrimary = isPrimary;
    }

    @Override
    protected void init() {
        super.init();
        SCREEN_WIDTH = this.width;
        SCREEN_HEIGHT = this.height;
        UNIT_LENGTH = (int) (this.width / 120f);

        loadWeaponTabs();
        createTabButtons();

        if (!weaponsByTab.isEmpty()) {
            currentTab = weaponsByTab.keySet().iterator().next();
            updateCurrentWeapons();
            createWeaponButtons();
        }

        addScrollButtons();
        addBackButton();
    }

    private void loadWeaponTabs() {
        WeaponFilterConfig config = WeaponFilterConfig.load();
        List<String> tabNames = isPrimary ?
                config.getPrimaryWeaponTabs() :
                config.getSecondaryWeaponTabs();

        for (String tabName : tabNames) {
            List<ItemStack> items = getItemsFromTab(tabName);
            if (!items.isEmpty()) {
                weaponsByTab.put(tabName, items);
            }
        }
    }

    private List<ItemStack> getItemsFromTab(String tabName) {
        GunTabType gunTabType;

        switch(tabName) {
            case "pistol": gunTabType = GunTabType.PISTOL; break;
            case "rifle": gunTabType = GunTabType.RIFLE; break;
            case "sniper": gunTabType = GunTabType.SNIPER; break;
            case "shotgun": gunTabType = GunTabType.SHOTGUN; break;
            case "smg": gunTabType = GunTabType.SMG; break;
            case "mg": gunTabType = GunTabType.MG; break;
            case "rpg": gunTabType = GunTabType.RPG; break;
            default: return new ArrayList<>();
        }

        return AbstractGunItem.fillItemCategory(gunTabType);
    }

    private void createTabButtons() {
        int tabWidth = 5 * UNIT_LENGTH;
        int tabHeight = (int) (1.5f * UNIT_LENGTH);
        int tabSpacing = UNIT_LENGTH;
        int totalTabsWidth = weaponsByTab.size() * (tabWidth + tabSpacing) - tabSpacing;
        int startX = 6 * UNIT_LENGTH;    //(this.width - totalTabsWidth) / 2;
        int startY = SCREEN_HEIGHT - 18 * UNIT_LENGTH;

        int index = 0;
        for (String tabName : weaponsByTab.keySet()) {
            int x = startX + index * (tabWidth + tabSpacing);

            TabButton tabButton = new TabButton(
                    x, startY, tabWidth, tabHeight,
                    tabName, btn -> switchTab(tabName)
            );

            if (index == 0) {
                tabButton.setSelected(true);
            }

            tabButtons.add(tabButton);
            addRenderableWidget(tabButton);
            index++;
        }
    }

    private void switchTab(String tabName) {
        if (!tabName.equals(currentTab)) {
            currentTab = tabName;
            scrollOffset = 0;

            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playNotifySound(
                        SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                        SoundSource.PLAYERS, 0.5f, 1.2f
                );
            }

            for (TabButton tab : tabButtons) {
                tab.setSelected(tab.getTabName().equals(currentTab));
            }

            updateCurrentWeapons();
            createWeaponButtons();
        }
    }

    private void updateCurrentWeapons() {
        currentWeapons.clear();
        List<ItemStack> weapons = weaponsByTab.get(currentTab);
        if (weapons != null) {
            currentWeapons.addAll(weapons);
        }
        maxScroll = Math.max(0, currentWeapons.size() - BUTTONS_PER_ROW);
    }

    private void createWeaponButtons() {
        weaponButtons.forEach(this::removeWidget);
        weaponButtons.clear();

        if (currentWeapons.isEmpty()) return;

        int startX = 6 * UNIT_LENGTH;
        int startY = this.height - 16 * UNIT_LENGTH;

        int visibleStart = scrollOffset;
        int visibleEnd = Math.min(visibleStart + BUTTONS_PER_ROW, currentWeapons.size());

        for (int i = visibleStart; i < visibleEnd; i++) {
            ItemStack weapon = currentWeapons.get(i);
            int buttonIndex = i - visibleStart;
            int x = startX + buttonIndex * (BUTTON_SIZE + BUTTON_SPACING) * UNIT_LENGTH;

            ResourceLocation texture = null;
            GunDisplayInstance display = TimelessAPI.getGunDisplay(weapon).orElse(null);
            if (display != null) {
                texture = display.getHUDTexture();
            }

            WeaponSelectionButton button = new WeaponSelectionButton(
                    x, startY,
                    BUTTON_SIZE * UNIT_LENGTH,
                    BUTTON_SIZE * UNIT_LENGTH / 2,
                    weapon, texture,
                    btn -> onWeaponSelected(weapon),
                    UNIT_LENGTH
            );

            weaponButtons.add(button);
            addRenderableWidget(button);
        }
    }

    private void addScrollButtons() {
        addRenderableWidget(new FlatColorButton(
                2 * UNIT_LENGTH,
                SCREEN_HEIGHT - 16 * UNIT_LENGTH,
                3 * UNIT_LENGTH,
                7 * UNIT_LENGTH,
                btn -> scrollLeft()
        ) {
            @Override
            public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (scrollOffset > 0) {
                    super.renderWidget(graphics, mouseX, mouseY, partialTick);
                    graphics.drawCenteredString(
                            Minecraft.getInstance().font,
                            Component.literal("<"),
                            this.getX() + this.width / 2,
                            this.getY() + (this.height - 8) / 2,
                            0xFFFFFF
                    );
                }
            }
        });
        addRenderableWidget(new FlatColorButton(
                SCREEN_WIDTH - 5 * UNIT_LENGTH,
                SCREEN_HEIGHT - 16 * UNIT_LENGTH,
                3 * UNIT_LENGTH,
                7 * UNIT_LENGTH,
                btn -> scrollRight()
        ) {
            @Override
            public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (scrollOffset < maxScroll) {
                    super.renderWidget(graphics, mouseX, mouseY, partialTick);
                    graphics.drawCenteredString(
                            Minecraft.getInstance().font,
                            Component.literal(">"),
                            this.getX() + this.width / 2,
                            this.getY() + (this.height - 8) / 2,
                            0xFFFFFF
                    );
                }
            }
        });
    }

    private void scrollLeft() {
        if (scrollOffset > 0) {
            scrollOffset--;
            createWeaponButtons();

            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playNotifySound(
                        SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                        SoundSource.PLAYERS, 0.3f, 1.5f
                );
            }
        }
    }

    private void scrollRight() {
        if (scrollOffset < maxScroll) {
            scrollOffset++;
            createWeaponButtons();

            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playNotifySound(
                        SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                        SoundSource.PLAYERS, 0.3f, 1.5f
                );
            }
        }
    }

    private void addBackButton() {
        addRenderableWidget(new FlatColorButton(
                5 * UNIT_LENGTH,
                this.height - 4 * UNIT_LENGTH,
                6 * UNIT_LENGTH,
                3 * UNIT_LENGTH,
                btn -> Minecraft.getInstance().setScreen(parentScreen)
        ) {
            @Override
            public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                super.renderWidget(graphics, mouseX, mouseY, partialTick);
                graphics.drawCenteredString(
                        Minecraft.getInstance().font,
                        Component.literal("< 返回"),
                        this.getX() + this.width / 2,
                        this.getY() + (this.height - 8) / 2,
                        0xFFFFFF
                );
            }
        });
    }

    private void onWeaponSelected(ItemStack weapon) {
        String key = isPrimary ? "primary" : "secondary";
        String itemId = weapon.getItem().builtInRegistryHolder().key().location().toString();
        String nbt = weapon.hasTag() ? weapon.getTag().toString() : "";

        BagSelectionConfig.Backpack.ItemData itemData =
                new BagSelectionConfig.Backpack.ItemData(itemId, 1, nbt);
        backpack.getItem_MAP().put(key, itemData);

        // 发数据包
        PacketHandler.sendToServer(
                new UpdateWeaponPacket(BAGSERIAL, key, itemId, nbt));

        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.playNotifySound(
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 0.5f, 1f
            );
        }

        Minecraft.getInstance().setScreen(parentScreen);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        String title = isPrimary ? "选择主武器" : "选择副武器";
        graphics.drawCenteredString(
                this.font,
                Component.literal(title),
                this.width / 2,
                3 * UNIT_LENGTH,
                0xFFFFFF
        );

        String categoryInfo = getTabDisplayName(currentTab).getString();
        graphics.drawCenteredString(
                this.font,
                Component.literal(categoryInfo),
                this.width / 2,
                17 * UNIT_LENGTH,
                0xFFFF55
        );

        //if (maxScroll > 0) {
        //    int totalPages = maxScroll + 1;
        //    int currentPage = scrollOffset + 1;
        //    String scrollInfo = String.format(" %d / %d", currentPage, totalPages);
        //    graphics.drawCenteredString(
        //            this.font,
        //            Component.literal(scrollInfo),
        //            this.width / 2,
        //            this.height - 20 * UNIT_LENGTH,
        //            0xAAAAAA
        //    );
        //}
    }

    private Component getTabDisplayName(String tabName) {
        return switch (tabName) {
            case "pistol" -> Component.translatable("tacz.type.pistol.name");
            case "rifle" -> Component.translatable("tacz.type.rifle.name");
            case "sniper" -> Component.translatable("tacz.type.sniper.name");
            case "shotgun" -> Component.translatable("tacz.type.shotgun.name");
            case "smg" -> Component.translatable("tacz.type.smg.name");
            case "mg" -> Component.translatable("tacz.type.mg.name");
            case "rpg" -> Component.translatable("tacz.type.rpg.name");
            default -> Component.literal(tabName.toUpperCase());
        };
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics) {
        graphics.fillGradient(0, 0, this.width, this.height, 0x90202020, 0xC0000000);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            Minecraft.getInstance().setScreen(parentScreen);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) {
            scrollLeft();
        } else if (delta < 0) {
            scrollRight();
        }
        return true;
    }

    private class TabButton extends Button {
        private final String tabName;
        private boolean selected = false;

        public TabButton(int x, int y, int width, int height, String tabName, OnPress onPress) {
            super(x, y, width, height,
                    getTabDisplayName(tabName),
                    onPress, DEFAULT_NARRATION);
            this.tabName = tabName;
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (selected) {
                graphics.fillGradient(
                        this.getX(), this.getY(),
                        this.getX() + this.width, this.getY() + this.height,
                        0xFF2A5F2A, 0xFF145214
                );
            } else if (isHoveredOrFocused()) {
                graphics.fillGradient(
                        this.getX(), this.getY(),
                        this.getX() + this.width, this.getY() + this.height,
                        0xDA6C666C, 0xED393739
                );
            } else {
                graphics.fillGradient(
                        this.getX(), this.getY(),
                        this.getX() + this.width, this.getY() + this.height,
                        0xDA5C565C, 0xED292729
                );
            }

            if (selected) {
                graphics.fillGradient(
                        this.getX(), this.getY() + this.height - 2,
                        this.getX() + this.width, this.getY() + this.height,
                        0xFF52FF52, 0xFF52FF52
                );
            }

            graphics.fillGradient(
                    this.getX(), this.getY() + this.height,
                    this.getX() + this.width, this.getY() + this.height + 1,
                    0x7019181A, 0x7019181A
            );

            graphics.drawCenteredString(
                    Minecraft.getInstance().font,
                    getMessage(),
                    this.getX() + this.width / 2,
                    this.getY() + (this.height - 8) / 2,
                    selected ? 0xFFFFFF : (isHoveredOrFocused() ? 0xFFFF55 : 0xAAAAAA)
            );
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        public String getTabName() {
            return tabName;
        }
    }
}

