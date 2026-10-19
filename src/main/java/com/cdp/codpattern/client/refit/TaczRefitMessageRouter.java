package com.cdp.codpattern.client.refit;

import com.cdp.codpattern.compat.tacz.TaczRefitActionCodec;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageLaserColor;

/** The channel hook only redirects operations originating in the active CDP refit screen. */
public final class TaczRefitMessageRouter {
    private TaczRefitMessageRouter() {
    }

    public static boolean route(Object channel, Object message) {
        if (channel != NetworkHandler.CHANNEL || !AttachmentRefitClientState.acceptsTaczOperations()
                || !TaczRefitActionCodec.supports(message)) {
            return false;
        }
        // Precise HSV value changes are already queued. TaCZ's onClose packet is a
        // whole-gun snapshot that may predate pending installs, so consume it here.
        if (message instanceof ClientMessageLaserColor) return true;
        try {
            AttachmentRefitClientState.enqueueOperation(TaczRefitActionCodec.decode(message));
        } catch (RuntimeException failure) {
            // An untranslatable preview message must never fall through to the real inventory.
            AttachmentRefitClientState.onOperationTranslationFailed(failure);
        }
        return true;
    }

}
