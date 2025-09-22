package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.client.gui.refit.FlatColorButton;
import com.cdp.codpattern.config.server.BagSelectionConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.GunDisplayInstance;
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
import org.jetbrains.annotations.NotNull;

public class WeaponMenuScreen extends Screen {

    private static int UNIT_LENGTH = 0;
    private final Integer BAGSERIAL;
    private BagSelectionConfig.Backpack backpack;
    private ResourceLocation primaryhudTexture;
    private ResourceLocation secondaryhudTexture;

    public WeaponMenuScreen(BagSelectionConfig.Backpack backpack, Integer BAGSERIAL) {
        super(Component.literal("WeaponMenuScreen"));
        this.BAGSERIAL = BAGSERIAL;
        this.backpack = backpack;
    }

    public void init() {
        super.init();
        int SCREEN_WIDTH = this.width;
        UNIT_LENGTH = SCREEN_WIDTH / 120;
        try {
            Weapon();
        } catch (CommandSyntaxException ignored) {
        }
        addWeaponButtonandTexture();
    }

    public void Weapon() throws CommandSyntaxException {
        //这段处理传入的主副武器
        ItemStack primaryitemstack = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(backpack.getItem_MAP().get("primary").getItem())), backpack.getItem_MAP().get("primary").getCount());
        ItemStack secondaryitemstack = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(backpack.getItem_MAP().get("secondary").getItem())), backpack.getItem_MAP().get("secondary").getCount());

        primaryitemstack.setTag(TagParser.parseTag(backpack.getItem_MAP().get("primary").getNbt()));
        secondaryitemstack.setTag(TagParser.parseTag(backpack.getItem_MAP().get("secondary").getNbt()));

        GunDisplayInstance primarydisplay = TimelessAPI.getGunDisplay(primaryitemstack).orElse(null);
        GunDisplayInstance secondarydisplay = TimelessAPI.getGunDisplay(secondaryitemstack).orElse(null);

        if (primarydisplay != null) {
            this.primaryhudTexture = primarydisplay.getHUDTexture();
        }
        if (secondarydisplay != null) {
            this.secondaryhudTexture = secondarydisplay.getHUDTexture();
        }

        //IGun primaryitemstackItemgunIdIGun = (IGun) primaryitemstack.getItem();
        //IGun secondaryitemstackItemgunIdIGun = (IGun) secondaryitemstack.getItem();
    }

    public void render(@NotNull GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderBackground(pGuiGraphics);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics pGuiGraphics) {
        pGuiGraphics.fillGradient(0, 0, this.width, this.height, 0x90202020, 0xC0000000);
    }

    public void addWeaponButtonandTexture(){
        int buttonWidth = 24 * UNIT_LENGTH;
        int buttonHeight = 12 * UNIT_LENGTH;

        // 主武器按钮 - 点击打开WeaponScreen选择主武器
        addRenderableWidget(new FlatColorButton(
                6 * UNIT_LENGTH,
                this.height - 18 * UNIT_LENGTH,
                buttonWidth,
                buttonHeight,
                this.BAGSERIAL,
                this.backpack,
                this.primaryhudTexture,
                this.UNIT_LENGTH,
                button -> {
                    // 打开WeaponScreen，传入true表示选择主武器
                    Minecraft.getInstance().setScreen(
                            new WeaponScreen(this, this.backpack, this.BAGSERIAL, true)
                    );
                }
        ));

        // 副武器按钮 - 点击打开WeaponScreen选择副武器
        addRenderableWidget(new FlatColorButton(
                32 * UNIT_LENGTH,
                this.height - 18 * UNIT_LENGTH,
                buttonWidth,
                buttonHeight,
                this.BAGSERIAL,
                this.backpack,
                this.secondaryhudTexture,
                this.UNIT_LENGTH,
                button -> {
                    // 打开WeaponScreen，传入false表示选择副武器
                    Minecraft.getInstance().setScreen(
                            new WeaponScreen(this, this.backpack, this.BAGSERIAL, false)
                    );
                }
        ));
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
            Minecraft.getInstance().setScreen(new BackpackMenuScreen());
        });
    }

    public Integer getBAGSERIAL() {
        return BAGSERIAL;
    }

    public BagSelectionConfig.Backpack getBackpack() {
        return backpack;
    }

    public static int getUnitLength() {
        return UNIT_LENGTH;
    }

    public ResourceLocation getPrimaryhudTexture() {
        return primaryhudTexture;
    }

    public ResourceLocation getSecondaryhudTexture() {
        return secondaryhudTexture;
    }
}
