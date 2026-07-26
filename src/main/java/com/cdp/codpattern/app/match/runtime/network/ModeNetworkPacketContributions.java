package com.cdp.codpattern.app.match.runtime.network;

import com.cdp.codpattern.app.match.extension.ModeNetworkPacketRegistration;

import java.util.function.IntSupplier;

/** Combined-distribution registry for deterministic installed or reserved packet slots. */
public final class ModeNetworkPacketContributions {
    private static final ModeNetworkPacketSlotRegistry REGISTRY = new ModeNetworkPacketSlotRegistry();

    private ModeNetworkPacketContributions() {
    }

    public static void install(String slotId, ModeNetworkPacketRegistration registration) {
        REGISTRY.install(slotId, registration);
    }

    public static boolean registerOrReserve(String slotId, IntSupplier reserveDiscriminator) {
        return REGISTRY.registerOrReserve(slotId, reserveDiscriminator);
    }
}
