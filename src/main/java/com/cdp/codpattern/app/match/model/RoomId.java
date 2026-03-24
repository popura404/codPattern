package com.cdp.codpattern.app.match.model;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

public record RoomId(String gameType, String mapName) {
    private static final String SEPARATOR = "|";

    public RoomId {
        gameType = sanitize(gameType);
        mapName = sanitize(mapName);
        if (gameType.isBlank()) {
            throw new IllegalArgumentException("gameType must not be blank");
        }
        if (mapName.isBlank()) {
            throw new IllegalArgumentException("mapName must not be blank");
        }
    }

    public static RoomId of(String gameType, String mapName) {
        return new RoomId(gameType, mapName);
    }

    public static RoomId decode(String encoded) {
        String safe = sanitize(encoded);
        int split = safe.indexOf(SEPARATOR);
        if (split <= 0 || split >= safe.length() - 1) {
            throw new IllegalArgumentException("Invalid room id: " + encoded);
        }
        return new RoomId(safe.substring(0, split), safe.substring(split + 1));
    }

    public String encode() {
        return gameType + SEPARATOR + mapName;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(gameType);
        buf.writeUtf(mapName);
    }

    public static RoomId read(FriendlyByteBuf buf) {
        return new RoomId(buf.readUtf(), buf.readUtf());
    }

    @Override
    public String toString() {
        return encode();
    }

    private static String sanitize(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}
