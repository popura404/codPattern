package com.cdp.codpattern.app.zombies.service;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class ZombiesRoomAnnouncementService {
    private static final ClientboundSetTitlesAnimationPacket UNLOCK_TITLE_ANIMATION =
            new ClientboundSetTitlesAnimationPacket(4, 42, 16);

    private final Supplier<Collection<ServerPlayer>> recipientsSupplier;

    public ZombiesRoomAnnouncementService(Supplier<Collection<ServerPlayer>> recipientsSupplier) {
        this.recipientsSupplier = recipientsSupplier == null ? List::of : recipientsSupplier;
    }

    public void broadcastSubtitle(String key, Object... args) {
        if (key == null || key.isBlank()) {
            return;
        }
        Component message = Component.translatable(key, args);
        ClientboundSetTitleTextPacket titlePacket = new ClientboundSetTitleTextPacket(Component.empty());
        ClientboundSetSubtitleTextPacket subtitlePacket = new ClientboundSetSubtitleTextPacket(message);
        for (ServerPlayer player : safeRecipients()) {
            if (player != null) {
                player.connection.send(UNLOCK_TITLE_ANIMATION);
                player.connection.send(titlePacket);
                player.connection.send(subtitlePacket);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
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
