package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.compatibility.lrtactical.api.APIextension;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * 扁平化颜色按钮 - COD MWII 2022 风格
 */
public class FlatColorButton extends Button {

    private int focusedtime = 0;
    private boolean isPhotoButton = false;
    private Integer BAGSERIAL;
    private int UNIT_LENGTH;

    private ItemStack weapon;
    private Component weaponName;
    private Component weaponPackinfo;
    private ResourceLocation Teaxture;
    private ItemStack lastWeaponSnapshot = ItemStack.EMPTY;
    /** 为 true 时不渲染蓝色包名（用于投掷物槽位） */
    private boolean hidePackName = false;


    // 普通按钮
    public FlatColorButton(int x, int y, int width, int height, @Nullable Button.OnPress pOnPress) {
        super(x, y, width, height, Component.literal("choose your bag"), pOnPress, DEFAULT_NARRATION);
    }

    // 带贴图的按钮
    public FlatColorButton(int pX, int pY, int pWidth, int pHeight, Integer bagserial,
                           ItemStack itemStack,
                           int UNIT_LENGTH, @Nullable Button.OnPress onPress){
        super(pX, pY, pWidth, pHeight, Component.literal("choose ur weapon"), onPress != null ? onPress : button -> {}, DEFAULT_NARRATION);
        this.isPhotoButton = true;
        this.BAGSERIAL = bagserial;
        this.UNIT_LENGTH = UNIT_LENGTH;
        this.weapon = itemStack;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float pPartialTick) {
        ensureInfoCached();
        // 播放悬停音效
        if(this.isHoveredOrFocused() && focusedtime == 0){
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playNotifySound(
                            SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                            SoundSource.PLAYERS, 0.5f, 1.2f
                    );
                }
            });
            focusedtime = 1;
        } else if (!this.isHoveredOrFocused()) {
            focusedtime = 0;
        }

        // 渲染按钮背景 - MWII 深色风格
        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                CodTheme.CARD_BG_TOP, CodTheme.CARD_BG_BOTTOM);

        // 渲染左侧阴影
        graphics.fill(this.getX() - 3, this.getY(),
                this.getX(), this.getY() + this.height,
                CodTheme.SHADOW);
        // 渲染底部阴影
        graphics.fillGradient(this.getX(), this.getY() + this.height,
                this.getX() + this.width, this.getY() + this.height + 2,
                CodTheme.SHADOW, 0x40000000);

        // 悬停效果
        if (isHoveredOrFocused()) {
            renderOnHoveredOrFocused(graphics);
        }

        // 渲染贴图（支持悬停高亮）
        if (isPhotoButton && weapon != null && !weapon.isEmpty()) {
            if (Teaxture != null) {
                renderTexture(graphics, isHoveredOrFocused());
            } else {
                // LR Tactical 近战武器等无 TaCZ HUD 贴图的物品 - 使用物品图标渲染
                renderItemFallback(graphics, isHoveredOrFocused());
            }
        }

        // 渲染包名（如果有，投掷物槽位不显示）
        if (weaponPackinfo != null && !hidePackName) {
            graphics.drawString(Minecraft.getInstance().font, this.weaponPackinfo,
                    this.getX() + 4, this.getY() + 4, CodTheme.TEXT_PRIMARY);
        }
        // 渲染枪名（如果有）
        if (weaponName != null){
            graphics.drawString(Minecraft.getInstance().font, this.weaponName,
                    this.getX() + 4, this.getY() + this.height - UNIT_LENGTH - 2, CodTheme.TEXT_PRIMARY);
        }
    }

    protected void renderOnHoveredOrFocused(GuiGraphics graphics){
        // 悬停时的背景效果 - MWII 暗绿色
        graphics.fillGradient(this.getX(), this.getY(),
                this.getX() + this.width, this.getY() + this.height,
                CodTheme.HOVER_BG_TOP, CodTheme.HOVER_BG_BOTTOM);

        // 顶部荧光绿边框
        graphics.fill(this.getX(), this.getY() - 1,
                this.getX() + this.width, this.getY(),
                CodTheme.HOVER_BORDER);

        // 底部荧光绿边框
        graphics.fill(this.getX(), this.getY() + this.height,
                this.getX() + this.width, this.getY() + this.height + 2,
                CodTheme.HOVER_BORDER_SEMI);
    }

    /** 无 TaCZ 贴图时的物品图标回退渲染（近战武器、投掷物等） */
    protected void renderItemFallback(GuiGraphics graphics, boolean isHovered) {
        if (weapon == null || weapon.isEmpty()) return;

        float scale = 3f;
        int itemSize = (int) (16 * scale);
        int x = this.getX() + (this.width - itemSize) / 2;
        int y = this.getY() + 3 * UNIT_LENGTH;

        if (isHovered) {
            RenderSystem.setShaderColor(1.15f, 1.15f, 1.15f, 1.0f);
        } else {
            RenderSystem.setShaderColor(0.9f, 0.9f, 0.9f, 0.95f);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.renderItem(weapon, 0, 0);
        graphics.pose().popPose();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    protected void renderTexture(GuiGraphics graphics, boolean isHovered){
        if (Teaxture == null) return;

        // 设定贴图大小
        int textureRenderWidth = 18 * UNIT_LENGTH;
        int textureRenderHeight = 6 * UNIT_LENGTH;

        // 计算贴图位置
        int textureX = this.getX() + 3 * UNIT_LENGTH;
        int textureY = this.getY() + 3 * UNIT_LENGTH;

        // 设置颜色和透明度（悬停时高亮）
        if (isHovered) {
            RenderSystem.setShaderColor(1.15f, 1.15f, 1.15f, 1.0f);
        } else {
            RenderSystem.setShaderColor(0.9f, 0.9f, 0.9f, 0.95f);
        }

        // 渲染贴图
        graphics.blit(Teaxture, textureX, textureY,0 , 0 , textureRenderWidth, textureRenderHeight, textureRenderWidth, textureRenderHeight);

        // 重置颜色
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public void playDownSound(@NotNull SoundManager pHandler) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playNotifySound(
                        SoundEvents.AMETHYST_BLOCK_PLACE,
                        SoundSource.PLAYERS, 0.5f, 1f
                );
            } else {
                super.playDownSound(pHandler);
            }
        });
    }

    private void ensureInfoCached(){
        if (weapon == null || weapon.isEmpty()) {
            lastWeaponSnapshot = ItemStack.EMPTY;
            Teaxture = null;
            weaponPackinfo = null;
            weaponName = null;
            return;
        }
        if (!lastWeaponSnapshot.isEmpty() && ItemStack.isSameItemSameTags(weapon, lastWeaponSnapshot)) {
            return;
        }
        lastWeaponSnapshot = weapon.copy();

        // 图片处理
        Teaxture = null;
        TimelessAPI.getGunDisplay(weapon).ifPresent(display -> this.Teaxture = display.getHUDTexture());

        // 包名处理
        weaponPackinfo = null;
        if (weapon.getItem() instanceof IGun iGun) {
            ResourceLocation gunId = iGun.getGunId(weapon);
            PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(gunId);
            if (packInfoObject != null && packInfoObject.getName() != null) {
                this.weaponPackinfo = Component.translatable(packInfoObject.getName())
                        .withStyle(ChatFormatting.BLUE)
                        .withStyle(ChatFormatting.ITALIC);
            }
        } else {
            this.weaponPackinfo = APIextension.getLrItemPackName(weapon);
        }

        // 枪名处理
        this.weaponName = weapon.getHoverName();
    }

    /** 投掷物槽位不显示蓝色包名时设为 true */
    public void setHidePackName(boolean hide) {
        this.hidePackName = hide;
    }

    // Getters
    public ResourceLocation getResourceLocation() {
        return Teaxture;
    }

    public Integer getBAGSERIAL() {
        return BAGSERIAL;
    }
}
