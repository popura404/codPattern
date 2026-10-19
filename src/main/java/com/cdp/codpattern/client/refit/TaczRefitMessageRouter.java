package com.cdp.codpattern.client.refit;

import com.cdp.codpattern.compat.tacz.TaczRefitActionCodec;
import com.cdp.codpattern.core.refit.RefitAction;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageLaserColor;
import net.minecraft.world.item.ItemStack;

/** The channel hook only redirects operations originating in the active CDP refit screen. */
public final class TaczRefitMessageRouter {
    private TaczRefitMessageRouter() {
    }

    public static boolean route(Object channel, Object message) {
        if (channel != NetworkHandler.CHANNEL || !AttachmentRefitClientState.acceptsTaczOperations()
                || !TaczRefitActionCodec.supports(message)) {
            return false;
        }
        try {
            AttachmentRefitClientState.enqueueOperation(TaczRefitActionCodec.decode(message));
        } catch (RuntimeException failure) {
            // An untranslatable preview message must never fall through to the real inventory.
            AttachmentRefitClientState.onOperationTranslationFailed(failure);
        }
        return true;
    }

    public static RefitAction captureLaser(ItemStack gun, int gunSlot) {
        if (gun == null || gun.isEmpty()) {
            return null;
        }
        return TaczRefitActionCodec.decode(new ClientMessageLaserColor(gun, gunSlot));
    }
}
