package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.refit.AttachmentConfigButton;
import com.cdp.codpattern.client.gui.refit.FlatColorButton;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 武器配置菜单 - COD2022 风格
 */
public class WeaponMenuScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();

    private int UNIT_LENGTH = 0;
    private final Integer BAGSERIAL;
    private BackpackConfig.Backpack backpack;

    private ItemStack primaryItemStack;
    private ItemStack secondaryItemStack;
    private FlatColorButton primaryButton;
    private FlatColorButton secondaryButton;
    private int buttonWidth = 0;
    private int buttonHeight = 0;
    private int configButtonHeight = 0;
    private int configButtonY = 0;
    private int primaryX = 0;
    private int secondaryX = 0;

    private final Map<String, AttachmentConfigButton> attachmentButtonMap = new HashMap<>();
    private String currentAttachmentSlot = null;
    private int hideDelay = 0;
    private static final int MAX_HIDE_DELAY = 10;

    public WeaponMenuScreen(BackpackConfig.Backpack backpack, Integer BAGSERIAL) {
        super(Component.literal("WeaponMenuScreen"));
        this.BAGSERIAL = BAGSERIAL;
        this.backpack = backpack;
    }

    public void init() {
        super.init();
        UNIT_LENGTH = (int) ( ( ( float ) this.width ) / 120f);
        TextureandPackInfo();
        addWeaponButtonandTexture();
    }

    public void TextureandPackInfo() {
        this.primaryItemStack = buildItemStack(backpack.getItem_MAP().get("primary"));
        this.secondaryItemStack = buildItemStack(backpack.getItem_MAP().get("secondary"));
    }

    private ItemStack buildItemStack(BackpackConfig.Backpack.ItemData itemData) {
        if (itemData == null || itemData.getItem() == null) {
            return ItemStack.EMPTY;
        }
        try {
            ResourceLocation itemId = new ResourceLocation(itemData.getItem());
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId), itemData.getCount());
            String nbt = itemData.getNbt();
            if (nbt != null && !nbt.isEmpty()) {
                try {
                    stack.setTag(TagParser.parseTag(nbt));
                } catch (CommandSyntaxException e) {
                    LOGGER.warn("Invalid NBT for item {} in backpack {}", itemId, this.BAGSERIAL, e);
                }
            }
            return stack;
        } catch (Exception e) {
            LOGGER.warn("Invalid item id {} in backpack {}", itemData.getItem(), this.BAGSERIAL, e);
            return new ItemStack(Items.AIR);
        }
    }

    public void render(@NotNull GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderBackground(pGuiGraphics);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);

        // 渲染标题
        renderTitle(pGuiGraphics);

        // 渲染底部操作提示条
        renderBottomHintBar(pGuiGraphics);

        handleAttachmentButtonHover();
    }

    /**
     * 渲染标题
     */
    private void renderTitle(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        String title = "武器配置";
        int titleX = UNIT_LENGTH * 6;
        int titleY = UNIT_LENGTH * 4;

        // 标题文字
        graphics.drawString(mc.font, title, titleX, titleY, CodTheme.TEXT_PRIMARY, true);

        // 背包编号
        String bagInfo = "#" + BAGSERIAL;
        graphics.drawString(mc.font, bagInfo,
                titleX + mc.font.width(title) + 10, titleY,
                CodTheme.TEXT_SECONDARY, false);

        // 分隔线
        graphics.fill(titleX, titleY + mc.font.lineHeight + 4,
                this.width - titleX, titleY + mc.font.lineHeight + 5,
                CodTheme.DIVIDER);
    }

    /**
     * 渲染底部操作提示条
     */
    private void renderBottomHintBar(GuiGraphics graphics) {
        int barHeight = UNIT_LENGTH * 3;
        int barY = this.height - barHeight;

        // 背景
        graphics.fillGradient(0, barY, this.width, this.height, 0xE0101010, 0xF0000000);
        // 顶部边线
        graphics.fill(0, barY, this.width, barY + 1, CodTheme.BORDER_SUBTLE);

        Minecraft mc = Minecraft.getInstance();
        int textY = barY + (barHeight - mc.font.lineHeight) / 2;

        // 左侧提示
        String leftHint = "[LMB] 选择武器    [Hover] 配件配置";
        graphics.drawString(mc.font, leftHint, UNIT_LENGTH * 2, textY, CodTheme.TEXT_SECONDARY, false);

        // 右侧提示
        String rightHint = "[ESC] 返回背包列表";
        int rightWidth = mc.font.width(rightHint);
        graphics.drawString(mc.font, rightHint, this.width - UNIT_LENGTH * 2 - rightWidth, textY, CodTheme.TEXT_SECONDARY, false);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics pGuiGraphics) {
        // cod2022风格深色渐变背景
        pGuiGraphics.fillGradient(0, 0, this.width, this.height, CodTheme.BG_TOP, CodTheme.BG_BOTTOM);
    }

    @Override
    public void removed() {
        super.removed();
        hideDelay = 0;
        removeCurrentAttachmentButton();
    }

    public void addWeaponButtonandTexture(){
        buttonWidth = 24 * UNIT_LENGTH;
        buttonHeight = 12 * UNIT_LENGTH;

        // 主武器按钮
        primaryX = 6 * UNIT_LENGTH;
        int primaryY = this.height - 18 * UNIT_LENGTH;
        primaryButton = new FlatColorButton(
                primaryX,
                primaryY,
                buttonWidth,
                buttonHeight,
                this.BAGSERIAL,
                this.primaryItemStack,
                this.UNIT_LENGTH,
                button -> {
                    Minecraft.getInstance().setScreen(
                            new WeaponScreen(this, this.backpack, this.BAGSERIAL, true)
                    );
                }
        ) {
            @Override
            protected void renderOnHoveredOrFocused(GuiGraphics graphics) {
                graphics.fillGradient(this.getX(), this.getY(),
                        this.getX() + this.width, this.getY() + this.height,
                        CodTheme.HOVER_BG_TOP, CodTheme.HOVER_BG_BOTTOM);
                renderGoldBorder(graphics, this.getX(), this.getY(), this.width, this.height);
            }
        };
        addRenderableWidget(primaryButton);

        configButtonHeight = 2 * UNIT_LENGTH + 6;
        configButtonY = primaryY + buttonHeight + 1;

        // 副武器按钮
        secondaryX = 32 * UNIT_LENGTH;
        int secondaryY = this.height - 18 * UNIT_LENGTH;
        secondaryButton = new FlatColorButton(
                secondaryX,
                secondaryY,
                buttonWidth,
                buttonHeight,
                this.BAGSERIAL,
                this.secondaryItemStack,
                this.UNIT_LENGTH,
                button -> {
                    Minecraft.getInstance().setScreen(
                            new WeaponScreen(this, this.backpack, this.BAGSERIAL, false)
                    );
                }
        ) {
            @Override
            protected void renderOnHoveredOrFocused(GuiGraphics graphics) {
                graphics.fillGradient(this.getX(), this.getY(),
                        this.getX() + this.width, this.getY() + this.height,
                        CodTheme.HOVER_BG_TOP, CodTheme.HOVER_BG_BOTTOM);
                renderGoldBorder(graphics, this.getX(), this.getY(), this.width, this.height);
            }
        };
        addRenderableWidget(secondaryButton);
    }

    private void handleAttachmentButtonHover() {
        String hoveredSlot = null;

        if (primaryButton != null && primaryButton.isHoveredOrFocused()) {
            hoveredSlot = "primary";
        } else if (secondaryButton != null && secondaryButton.isHoveredOrFocused()) {
            hoveredSlot = "secondary";
        }

        if (currentAttachmentSlot != null) {
            AttachmentConfigButton attachmentButton = attachmentButtonMap.get(currentAttachmentSlot);
            if (attachmentButton != null && attachmentButton.isHoveredOrFocused()) {
                hoveredSlot = currentAttachmentSlot;
            }
        }

        if (hoveredSlot != null) {
            hideDelay = 0;
            if (!Objects.equals(hoveredSlot, currentAttachmentSlot)) {
                removeCurrentAttachmentButton();
                addAttachmentButton(hoveredSlot);
            }
        } else {
            if (currentAttachmentSlot != null) {
                hideDelay++;
                if (hideDelay >= MAX_HIDE_DELAY) {
                    removeCurrentAttachmentButton();
                    hideDelay = 0;
                }
            }
        }
    }

    private void addAttachmentButton(String slot) {
        int x = "primary".equals(slot) ? primaryX : secondaryX;
        AttachmentConfigButton button = attachmentButtonMap.get(slot);
        if (button == null) {
            button = new AttachmentConfigButton(
                    x,
                    configButtonY,
                    buttonWidth,
                    configButtonHeight,
                    this.BAGSERIAL,
                    slot
            );
            attachmentButtonMap.put(slot, button);
        }
        addRenderableWidget(button);
        currentAttachmentSlot = slot;
    }

    private void removeCurrentAttachmentButton() {
        if (currentAttachmentSlot == null) {
            return;
        }
        AttachmentConfigButton button = attachmentButtonMap.get(currentAttachmentSlot);
        if (button != null) {
            removeWidget(button);
        }
        currentAttachmentSlot = null;
    }

    private void renderGoldBorder(GuiGraphics graphics, int x, int y, int width, int height) {
        int borderWidth = CodTheme.BORDER_WIDTH;
        int color = CodTheme.SELECTED_BORDER;
        graphics.fill(x - borderWidth, y - borderWidth, x + width + borderWidth, y, color);
        graphics.fill(x - borderWidth, y + height, x + width + borderWidth, y + height + borderWidth, color);
        graphics.fill(x - borderWidth, y, x, y + height, color);
        graphics.fill(x + width, y, x + width + borderWidth, y + height, color);
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
                    Minecraft.getInstance().player.playNotifySound(SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 0.5f, 1f);
                }
            });
            this.ESCto();
        }
        return true;
    }

    public void ESCto() {
        Minecraft.getInstance().execute(() -> {
            this.onClose();
            Minecraft.getInstance().setScreen(new BackpackMenuScreen());
        });
    }

    public Integer getBAGSERIAL() {
        return BAGSERIAL;
    }

    public BackpackConfig.Backpack getBackpack() {
        return backpack;
    }
}
