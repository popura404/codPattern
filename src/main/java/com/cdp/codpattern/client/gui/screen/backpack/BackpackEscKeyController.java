package com.cdp.codpattern.client.gui.screen.backpack;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class BackpackEscKeyController {
    private static final int KEY_ESC = 256;

    private BackpackEscKeyController() {
    }

    public static boolean handleKeyPressed(int keyCode,
            BackpackContextMenuCoordinator contextMenuCoordinator,
            Runnable closeScreenAction) {
        if (keyCode != KEY_ESC) {
            return true;
        }

        if (contextMenuCoordinator.isVisible()) {
            contextMenuCoordinator.hide();
            playUiCloseSound(0.5f);
            return true;
        }

        playUiCloseSound(1.0f);
        closeScreenAction.run();
        return true;
    }

    private static void playUiCloseSound(float volume) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playNotifySound(
                        SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, volume, 1f);
            }
        });
    }
}
