package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.client.gui.refit.FlatColorButton;
import com.cdp.codpattern.client.gui.refit.WeaponSelectionButton;
import com.cdp.codpattern.app.backpack.service.BackpackNamespaceFilter;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.compat.lrtactical.LrTacticalClientApi;
import com.cdp.codpattern.compat.tacz.client.TaczClientApi;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterClientCache;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.network.UpdateWeaponPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WeaponScreen extends Screen {

    public int SCREEN_HEIGHT = 0;
    public int SCREEN_WIDTH = 0;
    private int UNIT_LENGTH = 0;
    private final Integer BAGSERIAL;
    private final BackpackConfig.Backpack backpack;
    /** 槽位类型: "primary" | "secondary" | "tactical" | "lethal" */
    private final String slotType;
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

    public WeaponScreen(WeaponMenuScreen parent, BackpackConfig.Backpack backpack,
            Integer bagSerial, String slotType) {
        super(Component.literal("WeaponScreen"));
        this.parentScreen = parent;
        this.BAGSERIAL = bagSerial;
        this.backpack = backpack;
        this.slotType = slotType != null ? slotType : "primary";
    }

    @Override
    protected void init() {
        super.init();
        SCREEN_WIDTH = this.width;
        SCREEN_HEIGHT = this.height;
        UNIT_LENGTH = Math.max(1, (int) (((float) this.width) / 120f));

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
        WeaponFilterConfig filterConfig = WeaponFilterClientCache.get();

        // 战术/杀伤投掷物只显示投掷物列表
        if ("tactical".equals(slotType) || "lethal".equals(slotType)) {
            if (filterConfig == null || !filterConfig.isThrowablesEnabled()) {
                return;
            }

            List<ItemStack> throwables = getItemsFromTab("throwable", filterConfig);
            if (!throwables.isEmpty()) {
                weaponsByTab.put("throwable", throwables);
            }
            return;
        }

        if (filterConfig == null) {
            return;
        }
        List<String> tabNames = "primary".equals(slotType)
                ? filterConfig.getPrimaryWeaponTabs()
                : filterConfig.getSecondaryWeaponTabs();

        for (String tabName : tabNames) {
            List<ItemStack> items = getItemsFromTab(tabName, filterConfig);
            if (!items.isEmpty()) {
                weaponsByTab.put(tabName, items);
            }
        }
    }

    private List<ItemStack> getItemsFromTab(String tabName, @Nullable WeaponFilterConfig filterConfig) {
        List<ItemStack> rawItems = switch (tabName) {
            case "melee":
                yield LrTacticalClientApi.fillLrItemCategory(true);
            case "throwable":
                yield LrTacticalClientApi.fillLrItemCategory(false);
            default:
                yield TaczClientApi.fillGunItemCategory(tabName);
        };

        if (filterConfig == null || rawItems.isEmpty()) {
            return rawItems;
        }

        List<ItemStack> filteredItems = new ArrayList<>();
        for (ItemStack stack : rawItems) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ResourceLocation fallbackItemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!BackpackNamespaceFilter.isBlocked(filterConfig, stack, fallbackItemId)) {
                filteredItems.add(stack);
            }
        }
        return filteredItems;
    }

    private void createTabButtons() {
        int tabWidth = 5 * UNIT_LENGTH;
        int tabHeight = (int) (1.5f * UNIT_LENGTH);
        int tabSpacing = UNIT_LENGTH;
        int totalTabsWidth = weaponsByTab.size() * (tabWidth + tabSpacing) - tabSpacing;
        int startX = 6 * UNIT_LENGTH; // (this.width - totalTabsWidth) / 2;
        int startY = SCREEN_HEIGHT - 18 * UNIT_LENGTH;

        int index = 0;
        for (String tabName : weaponsByTab.keySet()) {
            int x = startX + index * (tabWidth + tabSpacing);

            TabButton tabButton = new TabButton(
                    x, startY, tabWidth, tabHeight,
                    tabName, btn -> switchTab(tabName));

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
                        SoundSource.PLAYERS, 0.5f, 1.2f);
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

        if (currentWeapons.isEmpty())
            return;

        int startX = 6 * UNIT_LENGTH;
        int startY = this.height - 16 * UNIT_LENGTH;

        int visibleStart = scrollOffset;
        int visibleEnd = Math.min(visibleStart + BUTTONS_PER_ROW, currentWeapons.size());

        for (int i = visibleStart; i < visibleEnd; i++) {
            ItemStack weapon = currentWeapons.get(i);
            int buttonIndex = i - visibleStart;
            int x = startX + buttonIndex * (BUTTON_SIZE + BUTTON_SPACING) * UNIT_LENGTH;

            ResourceLocation texture = TaczClientApi.getGunHudTexture(weapon);

            WeaponSelectionButton button = new WeaponSelectionButton(
                    x, startY,
                    BUTTON_SIZE * UNIT_LENGTH,
                    BUTTON_SIZE * UNIT_LENGTH / 2,
                    weapon, texture,
                    btn -> onWeaponSelected(weapon),
                    UNIT_LENGTH);

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
                btn -> scrollLeft()) {
            @Override
            public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (scrollOffset > 0) {
                    super.renderWidget(graphics, mouseX, mouseY, partialTick);
                    GuiTextHelper.drawCenteredEllipsizedString(
                            graphics,
                            Minecraft.getInstance().font,
                            Component.translatable("screen.codpattern.weapon.scroll_left"),
                            this.getX() + this.width / 2,
                            this.getY() + (this.height - Minecraft.getInstance().font.lineHeight) / 2,
                            this.width - 4,
                            0xFFFFFF,
                            false
                    );
                }
            }
        });
        addRenderableWidget(new FlatColorButton(
                SCREEN_WIDTH - 5 * UNIT_LENGTH,
                SCREEN_HEIGHT - 16 * UNIT_LENGTH,
                3 * UNIT_LENGTH,
                7 * UNIT_LENGTH,
                btn -> scrollRight()) {
            @Override
            public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (scrollOffset < maxScroll) {
                    super.renderWidget(graphics, mouseX, mouseY, partialTick);
                    GuiTextHelper.drawCenteredEllipsizedString(
                            graphics,
                            Minecraft.getInstance().font,
                            Component.translatable("screen.codpattern.weapon.scroll_right"),
                            this.getX() + this.width / 2,
                            this.getY() + (this.height - Minecraft.getInstance().font.lineHeight) / 2,
                            this.width - 4,
                            0xFFFFFF,
                            false
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
                        SoundSource.PLAYERS, 0.3f, 1.5f);
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
                        SoundSource.PLAYERS, 0.3f, 1.5f);
            }
        }
    }

    private void addBackButton() {
        addRenderableWidget(new FlatColorButton(
                5 * UNIT_LENGTH,
                this.height - 4 * UNIT_LENGTH,
                6 * UNIT_LENGTH,
                3 * UNIT_LENGTH,
                btn -> Minecraft.getInstance().setScreen(parentScreen)) {
            @Override
            public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                super.renderWidget(graphics, mouseX, mouseY, partialTick);
                GuiTextHelper.drawCenteredEllipsizedString(
                        graphics,
                        Minecraft.getInstance().font,
                        Component.translatable("screen.codpattern.weapon.back"),
                        this.getX() + this.width / 2,
                        this.getY() + (this.height - Minecraft.getInstance().font.lineHeight) / 2,
                        this.width - 4,
                        0xFFFFFF,
                        false
                );
            }
        });
    }

    private void onWeaponSelected(ItemStack weapon) {
        String key = slotType;
        String itemId = weapon.getItem().builtInRegistryHolder().key().location().toString();
        String nbt = weapon.hasTag() ? weapon.getTag().toString() : "";

        BackpackConfig.Backpack.ItemData itemData = new BackpackConfig.Backpack.ItemData(itemId, 1, nbt);
        backpack.getItem_MAP().put(key, itemData);

        // 发数据包
        ModNetworkChannel.sendToServer(
                new UpdateWeaponPacket(BAGSERIAL, key, itemId, nbt));

        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.playNotifySound(
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 0.5f, 1f);
        }
        Minecraft.getInstance().setScreen(parentScreen);

    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        String title = switch (slotType) {
            case "primary" -> Component.translatable("screen.codpattern.weapon.select_primary").getString();
            case "secondary" -> Component.translatable("screen.codpattern.weapon.select_secondary").getString();
            case "tactical" -> Component.translatable("screen.codpattern.weapon.select_tactical").getString();
            case "lethal" -> Component.translatable("screen.codpattern.weapon.select_lethal").getString();
            default -> Component.translatable("screen.codpattern.weapon.select_weapon").getString();
        };
        GuiTextHelper.drawCenteredEllipsizedString(
                graphics,
                this.font,
                title,
                this.width / 2,
                3 * UNIT_LENGTH,
                this.width - UNIT_LENGTH * 12,
                0xFFFFFF,
                false
        );

        String categoryInfo = getTabDisplayName(currentTab).getString();
        GuiTextHelper.drawCenteredEllipsizedString(
                graphics,
                this.font,
                categoryInfo,
                this.width / 2,
                17 * UNIT_LENGTH,
                this.width - UNIT_LENGTH * 12,
                0xFFFF55,
                false
        );
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
            case "melee" -> Component.translatable("lrtactical.type.melee.name");
            case "throwable" -> Component.translatable("screen.codpattern.weapon.throwable");
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
                        0xFF2A5F2A, 0xFF145214);
            } else if (isHoveredOrFocused()) {
                graphics.fillGradient(
                        this.getX(), this.getY(),
                        this.getX() + this.width, this.getY() + this.height,
                        0xDA6C666C, 0xED393739);
            } else {
                graphics.fillGradient(
                        this.getX(), this.getY(),
                        this.getX() + this.width, this.getY() + this.height,
                        0xDA5C565C, 0xED292729);
            }

            if (selected) {
                graphics.fillGradient(
                        this.getX(), this.getY() + this.height - 2,
                        this.getX() + this.width, this.getY() + this.height,
                        0xFF52FF52, 0xFF52FF52);
            }

            graphics.fillGradient(
                    this.getX(), this.getY() + this.height,
                    this.getX() + this.width, this.getY() + this.height + 1,
                    0x7019181A, 0x7019181A);

            GuiTextHelper.drawCenteredEllipsizedString(
                    graphics,
                    Minecraft.getInstance().font,
                    getMessage(),
                    this.getX() + this.width / 2,
                    this.getY() + (this.height - Minecraft.getInstance().font.lineHeight) / 2,
                    this.width - 4,
                    selected ? 0xFFFFFF : (isHoveredOrFocused() ? 0xFFFF55 : 0xAAAAAA),
                    false
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
