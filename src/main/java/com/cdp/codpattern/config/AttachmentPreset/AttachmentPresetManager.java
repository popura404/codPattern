package com.cdp.codpattern.config.AttachmentPreset;

import com.cdp.codpattern.core.ConfigPath.ConfigPath;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public class AttachmentPresetManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Path getPresetPath(MinecraftServer server, UUID playerId, int bagId, String slot) {
        Path base = ConfigPath.SERVER_ATTACHMENT_PRESET.getPath(server);
        return base.resolve(playerId.toString())
                .resolve("bag_" + bagId + "_" + slot + ".snbt");
    }

    public static Optional<String> readPreset(MinecraftServer server, UUID playerId, int bagId, String slot) {
        Path path = getPresetPath(server, playerId, bagId, slot);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.warn("Failed to read attachment preset file {}", path, e);
            return Optional.empty();
        }
    }

    public static void writePreset(MinecraftServer server, UUID playerId, int bagId, String slot, String payload) {
        Path path = getPresetPath(server, playerId, bagId, slot);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, payload == null ? "" : payload, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to write attachment preset file {}", path, e);
        }
    }
}
