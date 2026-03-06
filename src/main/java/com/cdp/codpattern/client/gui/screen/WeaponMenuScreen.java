package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.app.backpack.service.BackpackNamespaceFilter;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.client.gui.refit.AttachmentConfigButton;
import com.cdp.codpattern.client.gui.refit.FlatColorButton;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterClientCache;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
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
 * 武器配置菜单
 */
public class WeaponMenuScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();

    private int UNIT_LENGTH = 0;
    private final Integer BAGSERIAL;
    private BackpackConfig.Backpack backpack;

    private ItemStack primaryItemStack;
    private ItemStack secondaryItemStack;
    private ItemStack tacticalItemStack;
    private ItemStack lethalItemStack;
    private FlatColorButton primaryButton;
    private FlatColorButton secondaryButton;
    private FlatColorButton tacticalButton;
    private FlatColorButton lethalButton;
    private int buttonWidth = 0;
    private int throwableButtonWidth = 0;
    private int buttonHeight = 0;
    private int configButtonHeight = 0;
    private int configButtonY = 0;
    private int primaryX = 0;
    private int secondaryX = 0;
    private int tacticalX = 0;
    private int lethalX = 0;

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
        UNIT_LENGTH = Math.max(1, (int) (((float) this.width) / 120f));
        TextureandPackInfo();
        addWeaponButtonandTexture();
    }

    public void TextureandPackInfo() {
        this.primaryItemStack = buildItemStack(backpack.getItem_MAP().get("primary"));
        this.secondaryItemStack = buildItemStack(backpack.getItem_MAP().get("secondary"));
        this.tacticalItemStack = buildItemStack(backpack.getItem_MAP().get("tactical"));
        this.lethalItemStack = buildItemStack(backpack.getItem_MAP().get("lethal"));
    }

    private ItemStack buildItemStack(BackpackConfig.Backpack.ItemData itemData) {
        if (itemData == null || itemData.getItem() == null) {
            return ItemStack.EMPTY;
        }
        try {
            ResourceLocation itemId = ResourceLocation.tryParse(itemData.getItem());
            if (itemId == null) {
                return new ItemStack(Items.AIR);
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId), itemData.getCount());
            String nbt = itemData.getNbt();
            if (nbt != null && !nbt.isEmpty()) {
                try {
                    stack.setTag(TagParser.parseTag(nbt));
                } catch (CommandSyntaxException e) {
                    LOGGER.warn("Invalid NBT for item {} in backpack {}", itemId, this.BAGSERIAL, e);
                }
            }
            WeaponFilterConfig filterConfig = WeaponFilterClientCache.get();
            if (filterConfig != null && BackpackNamespaceFilter.isBlocked(filterConfig, stack, itemId)) {
                return ItemStack.EMPTY;
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

        // 渲染投掷物槽位标签（左对齐，与主武器/副武器标签风格一致）
        if (tacticalButton != null) {
            pGuiGraphics.drawString(Minecraft.getInstance().font, "投掷物 1", tacticalButton.getX(), tacticalButton.getY() - 12, CodTheme.TEXT_SECONDARY, false);
        }
        if (lethalButton != null) {
            pGuiGraphics.drawString(Minecraft.getInstance().font, "投掷物 2", lethalButton.getX(), lethalButton.getY() - 12, CodTheme.TEXT_SECONDARY, false);
        }

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
        int leftMaxWidth = Math.max(32, this.width / 2 - UNIT_LENGTH * 4);
        int rightMaxWidth = Math.max(32, this.width / 3);

        // 左侧提示
        String leftHint = this.width < UNIT_LENGTH * 42
                ? "[LMB] 选择  [Hover] 配件"
                : "[LMB] 选择武器    [Hover] 更换配件";
        GuiTextHelper.drawEllipsizedString(
                graphics,
                mc.font,
                leftHint,
                UNIT_LENGTH * 2,
                textY,
                leftMaxWidth,
                CodTheme.TEXT_SECONDARY,
                false
        );

        // 右侧提示
        String rightHint = "[ESC] 返回背包列表";
        GuiTextHelper.drawRightAlignedEllipsizedString(
                graphics,
                mc.font,
                rightHint,
                this.width - UNIT_LENGTH * 2,
                textY,
                rightMaxWidth,
                CodTheme.TEXT_SECONDARY,
                false
        );
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
                            new WeaponScreen(this, this.backpack, this.BAGSERIAL, "primary")
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
                            new WeaponScreen(this, this.backpack, this.BAGSERIAL, "secondary")
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

        // 投掷物 1、投掷物 2 紧挨副武器，排布紧凑（参考 COD 配装栏）
        throwableButtonWidth = 8 * UNIT_LENGTH;
        tacticalX = 57 * UNIT_LENGTH;
        int tacticalY = this.height - 18 * UNIT_LENGTH;
        tacticalButton = new FlatColorButton(
                tacticalX,
                tacticalY,
                throwableButtonWidth,
                buttonHeight,
                this.BAGSERIAL,
                this.tacticalItemStack,
                this.UNIT_LENGTH,
                button -> {
                    Minecraft.getInstance().setScreen(
                            new WeaponScreen(this, this.backpack, this.BAGSERIAL, "tactical")
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
        tacticalButton.setHidePackName(true);  // 投掷物不显示蓝色包名
        addRenderableWidget(tacticalButton);

        // 投掷物 2
        lethalX = (57 + 8 + 1) * UNIT_LENGTH;
        int lethalY = this.height - 18 * UNIT_LENGTH;
        lethalButton = new FlatColorButton(
                lethalX,
                lethalY,
                throwableButtonWidth,
                buttonHeight,
                this.BAGSERIAL,
                this.lethalItemStack,
                this.UNIT_LENGTH,
                button -> {
                    Minecraft.getInstance().setScreen(
                            new WeaponScreen(this, this.backpack, this.BAGSERIAL, "lethal")
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
        lethalButton.setHidePackName(true);  // 投掷物不显示蓝色包名
        addRenderableWidget(lethalButton);
    }

    private void handleAttachmentButtonHover() {
        String hoveredSlot = null;

        if (primaryButton != null && primaryButton.isHoveredOrFocused() && supportsAttachmentConfig(primaryItemStack)) {
            hoveredSlot = "primary";
        } else if (secondaryButton != null && secondaryButton.isHoveredOrFocused() && supportsAttachmentConfig(secondaryItemStack)) {
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

    /** 仅 TaCZ 枪械支持配件改装，LR Tactical 近战/投掷物等不支持 */
    private boolean supportsAttachmentConfig(ItemStack stack) {
        return TaczGatewayProvider.gateway().isGun(stack);
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
