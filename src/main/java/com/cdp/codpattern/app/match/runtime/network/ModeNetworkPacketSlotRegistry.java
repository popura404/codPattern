package com.cdp.codpattern.app.match.runtime.network;

import com.cdp.codpattern.app.match.extension.ModeNetworkPacketRegistration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;

/** Deterministic optional packet slots that reserve their discriminator when no addon is installed. */
public final class ModeNetworkPacketSlotRegistry {
    private final Map<String, ModeNetworkPacketRegistration> registrations = new LinkedHashMap<>();

    public void install(String slotId, ModeNetworkPacketRegistration registration) {
        String normalizedSlotId = normalize(slotId);
        Objects.requireNonNull(registration, "registration");
        if (registrations.putIfAbsent(normalizedSlotId, registration) != null) {
            throw new IllegalStateException("Duplicate network packet slot contribution: " + normalizedSlotId);
        }
    }

    public boolean registerOrReserve(String slotId, IntSupplier reserveDiscriminator) {
        ModeNetworkPacketRegistration registration = registrations.get(normalize(slotId));
        if (registration != null) {
            registration.register();
            return true;
        }
        Objects.requireNonNull(reserveDiscriminator, "reserveDiscriminator").getAsInt();
        return false;
    }

    public int size() {
        return registrations.size();
    }

    private static String normalize(String slotId) {
        String normalized = Objects.requireNonNullElse(slotId, "").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("slotId must not be blank");
        }
        return normalized;
    }
}
