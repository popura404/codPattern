package com.cdp.codpattern.config.zombies;

import com.cdp.codpattern.config.path.ConfigPath;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesBackpackConfigRepository {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path serverConfigPath;
    private static ZombiesBackpackConfig serverConfig;
    private static JsonSaveResult lastSaveResult = JsonSaveResult.skipped(null, "Zombies backpack config has not been saved yet.");

    private ZombiesBackpackConfigRepository() {
    }

    public static ZombiesBackpackConfig loadOrCreate(MinecraftServer server, String mapName) {
        return loadOrCreate(ConfigPath.zombiesMapBackpackConfig(server, mapName));
    }

    public static ZombiesBackpackConfig loadOrCreate(Path path) {
        serverConfigPath = path;
        try {
            if (Files.exists(path)) {
                String configJson = Files.readString(path);
                ZombiesBackpackConfig loaded = GSON.fromJson(configJson, ZombiesBackpackConfig.class);
                serverConfig = loaded != null ? loaded : new ZombiesBackpackConfig();
                serverConfig.normalize();
                return serverConfig;
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load zombies backpack config: {}", path, e);
        }

        serverConfig = new ZombiesBackpackConfig();
        serverConfig.normalize();
        lastSaveResult = save(serverConfig);
        return serverConfig;
    }

    public static ZombiesBackpackConfig getConfig() {
        if (serverConfig == null) {
            serverConfig = new ZombiesBackpackConfig();
            serverConfig.normalize();
        }
        return serverConfig;
    }

    public static void setConfig(ZombiesBackpackConfig config) {
        serverConfig = config;
        if (serverConfig != null) {
            serverConfig.normalize();
        }
    }

    public static JsonSaveResult save(ZombiesBackpackConfig config) {
        if (config == null || serverConfigPath == null) {
            lastSaveResult = JsonSaveResult.skipped(serverConfigPath, "Zombies backpack config save skipped because path or config is missing.");
            return lastSaveResult;
        }
        try {
            config.normalize();
            Files.createDirectories(serverConfigPath.getParent());
            Files.writeString(serverConfigPath, GSON.toJson(config));
            serverConfig = config;
            lastSaveResult = JsonSaveResult.success(serverConfigPath);
            return lastSaveResult;
        } catch (IOException e) {
            LOGGER.error("Failed to save zombies backpack config: {}", serverConfigPath, e);
            lastSaveResult = JsonSaveResult.failure(serverConfigPath, "Failed to save zombies backpack config: " + serverConfigPath, e);
            return lastSaveResult;
        }
    }

    public static JsonSaveResult save() {
        return save(serverConfig);
    }

    public static JsonSaveResult getLastSaveResult() {
        return lastSaveResult;
    }
}
