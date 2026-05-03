package com.cdp.codpattern.app.zombies.map;

import com.cdp.codpattern.app.match.model.RoomId;

import java.util.Objects;

/**
 * Minimal match-level snapshot for startup validation.
 * MVP1 only needs the map snapshot; later runtime adapters can add live counts without changing the map contract.
 */
public record ZombiesMatchSnapshot(
        RoomId roomId,
        ZombiesMapSnapshot mapSnapshot
) {
    public ZombiesMatchSnapshot {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(mapSnapshot, "mapSnapshot");
    }

    public static ZombiesMatchSnapshot of(ZombiesMapSnapshot mapSnapshot) {
        Objects.requireNonNull(mapSnapshot, "mapSnapshot");
        return new ZombiesMatchSnapshot(mapSnapshot.roomId(), mapSnapshot);
    }
}
