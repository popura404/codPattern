package com.cdp.codpattern.mixin.inventory;

import com.cdp.codpattern.mixin.accessor.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    private static final int THROWABLE_SLOT_X_START = 178;
    private static final int THROWABLE_SLOT_Y = 142;
    private static final int THROWABLE_SLOT_SIZE = 18;
    private static final int THROWABLE_SLOT_GAP = 18;

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void codpattern$renderThrowableSlotBackdrop(GuiGraphics graphics, float partialTick, int mouseX, int mouseY,
            CallbackInfo ci) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) this;
        int leftPos = accessor.codpattern$getLeftPos();
        int topPos = accessor.codpattern$getTopPos();
        for (int i = 0; i < 2; i++) {
            int x = leftPos + THROWABLE_SLOT_X_START + (i * THROWABLE_SLOT_GAP);
            int y = topPos + THROWABLE_SLOT_Y;
            graphics.fill(x - 1, y - 1, x + THROWABLE_SLOT_SIZE + 1, y + THROWABLE_SLOT_SIZE + 1, 0xFF3A3F46);
            graphics.fill(x, y, x + THROWABLE_SLOT_SIZE, y + THROWABLE_SLOT_SIZE, 0xCC12181F);
            graphics.fill(x, y + THROWABLE_SLOT_SIZE - 1, x + THROWABLE_SLOT_SIZE, y + THROWABLE_SLOT_SIZE, 0xFF6B7685);
        }
    }

    @Inject(method = "renderLabels", at = @At("TAIL"))
    private void codpattern$renderThrowableLabels(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("screen.codpattern.inventory.throwable_1"),
                THROWABLE_SLOT_X_START - 1,
                132,
                0xFF9CA7B5,
                false);
        graphics.drawString(font, Component.translatable("screen.codpattern.inventory.throwable_2"),
                THROWABLE_SLOT_X_START + THROWABLE_SLOT_GAP - 1,
                132,
                0xFF9CA7B5,
                false);
    }
}
