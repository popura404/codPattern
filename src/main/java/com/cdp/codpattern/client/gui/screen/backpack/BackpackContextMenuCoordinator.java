package com.cdp.codpattern.client.gui.screen.backpack;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.gui.refit.BackPackSelectButton;
import com.cdp.codpattern.client.gui.refit.BackpackContextMenu;
import com.cdp.codpattern.client.gui.screen.BackpackMenuScreen;
import com.cdp.codpattern.client.gui.screen.RenameBackpackScreen;
import com.cdp.codpattern.network.CloneBackpackPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.function.IntConsumer;

public final class BackpackContextMenuCoordinator {
    private final BackpackContextMenu contextMenu = new BackpackContextMenu();

    public boolean handleMenuClick(double mouseX, double mouseY, int button) {
        if (!contextMenu.isVisible()) {
            return false;
        }
        boolean handled = contextMenu.mouseClicked(mouseX, mouseY, button);
        if (handled) {
            return true;
        }
        contextMenu.hide();
        return true;
    }

    public boolean isVisible() {
        return contextMenu.isVisible();
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return contextMenu.isVisible() && contextMenu.isMouseOver(mouseX, mouseY);
    }

    public void show(BackpackMenuScreen parent,
            int backpackId,
            BackPackSelectButton button,
            int mouseX,
            int mouseY,
            int screenWidth,
            int screenHeight,
            Runnable beforeShow,
            IntConsumer onDeleteClick) {
        beforeShow.run();
        contextMenu.clearItems();

        contextMenu.addItem("重命名", () -> Minecraft.getInstance().setScreen(
                new RenameBackpackScreen(parent, backpackId, button.getBackpack().getName())));
        contextMenu.addItem("复制", () -> ModNetworkChannel.sendToServer(new CloneBackpackPacket(backpackId)));
        contextMenu.addItem("删除", () -> onDeleteClick.accept(backpackId), CodTheme.TEXT_DANGER, CodTheme.TEXT_DANGER);

        contextMenu.show(mouseX, mouseY, screenWidth, screenHeight);
        playOpenSound();
    }

    public void hide() {
        contextMenu.hide();
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (contextMenu.isVisible()) {
            contextMenu.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static void playOpenSound() {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playNotifySound(
                        SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                        SoundSource.PLAYERS,
                        0.5f,
                        1.3f
                );
            }
        });
    }
}
