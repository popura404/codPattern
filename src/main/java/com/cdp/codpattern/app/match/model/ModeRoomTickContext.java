package com.cdp.codpattern.app.match.model;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public record ModeRoomTickContext(
        MinecraftServer server,
        ServerLevel level,
        RoomId roomId,
        long gameTime
) {
}
