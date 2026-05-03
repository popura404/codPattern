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

public final class ZombiesRulesRepository {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path serverConfigPath;
    private static ZombiesRulesConfig serverConfig;
    private static JsonSaveResult lastSaveResult = JsonSaveResult.skipped(null, "Zombies rules config has not been saved yet.");

    private ZombiesRulesRepository() {
    }

    public static ZombiesRulesConfig loadOrCreate(MinecraftServer server) {
        return loadOrCreate(ConfigPath.SERVER_ZOMBIES_RULES_CONFIG.getPath(server));
    }

    public static ZombiesRulesConfig loadOrCreate(Path path) {
        serverConfigPath = path;
        try {
            if (Files.exists(path)) {
                String configJson = Files.readString(path);
                ZombiesRulesConfig loaded = GSON.fromJson(configJson, ZombiesRulesConfig.class);
                serverConfig = loaded != null ? loaded : new ZombiesRulesConfig();
                serverConfig.normalize();
                return serverConfig;
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load zombies rules config: {}", path, e);
        }

        serverConfig = new ZombiesRulesConfig();
        serverConfig.normalize();
        lastSaveResult = save(serverConfig);
        return serverConfig;
    }

    public static ZombiesRulesConfig getConfig() {
        if (serverConfig == null) {
            serverConfig = new ZombiesRulesConfig();
            serverConfig.normalize();
        }
        return serverConfig;
    }

    public static void setConfig(ZombiesRulesConfig config) {
        serverConfig = config;
        if (serverConfig != null) {
            serverConfig.normalize();
        }
    }

    public static JsonSaveResult save(ZombiesRulesConfig config) {
        if (config == null || serverConfigPath == null) {
            lastSaveResult = JsonSaveResult.skipped(serverConfigPath, "Zombies rules config save skipped because path or config is missing.");
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
            LOGGER.error("Failed to save zombies rules config: {}", serverConfigPath, e);
            lastSaveResult = JsonSaveResult.failure(serverConfigPath, "Failed to save zombies rules config: " + serverConfigPath, e);
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
