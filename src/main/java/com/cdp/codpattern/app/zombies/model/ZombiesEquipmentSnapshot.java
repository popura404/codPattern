package com.cdp.codpattern.app.zombies.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ZombiesEquipmentSnapshot(
        String roomId,
        UUID playerId,
        List<ZombiesEquipmentSlotSnapshot> slots
) {
    public ZombiesEquipmentSnapshot {
        roomId = Objects.requireNonNullElse(roomId, "").trim();
        slots = slots == null
                ? List.of()
                : slots.stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingInt(ZombiesEquipmentSlotSnapshot::inventorySlot))
                        .toList();
    }

    public static ZombiesEquipmentSnapshot empty(String roomId, UUID playerId) {
        return new ZombiesEquipmentSnapshot(roomId, playerId, List.of());
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    public Optional<ZombiesEquipmentSlotSnapshot> slot(ZombiesEquipmentSlot slot) {
        if (slot == null) {
            return Optional.empty();
        }
        return slots.stream()
                .filter(snapshot -> snapshot.slot() == slot)
                .findFirst();
    }
}
