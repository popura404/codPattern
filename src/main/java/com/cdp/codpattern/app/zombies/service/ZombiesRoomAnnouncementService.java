package com.cdp.codpattern.app.zombies.service;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class ZombiesRoomAnnouncementService {
    private final Supplier<Collection<ServerPlayer>> recipientsSupplier;

    public ZombiesRoomAnnouncementService(Supplier<Collection<ServerPlayer>> recipientsSupplier) {
        this.recipientsSupplier = recipientsSupplier == null ? List::of : recipientsSupplier;
    }

    public void broadcastHotbar(String key, Object... args) {
        if (key == null || key.isBlank()) {
            return;
        }
        Component message = Component.translatable(key, args);
        for (ServerPlayer player : safeRecipients()) {
            if (player != null) {
                player.displayClientMessage(message, true);
            }
        }
    }

    private Collection<ServerPlayer> safeRecipients() {
        try {
            Collection<ServerPlayer> recipients = recipientsSupplier.get();
            return recipients == null ? List.of() : recipients.stream()
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }
}
