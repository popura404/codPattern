package com.cdp.codpattern.config.zombies;

import com.cdp.codpattern.config.path.ConfigPath;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesWeaponFilterRepository {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path serverConfigPath;
    private static ZombiesWeaponFilterConfig serverConfig;
    private static JsonSaveResult lastSaveResult = JsonSaveResult.skipped(null, "Zombies weapon filter config has not been saved yet.");

    private ZombiesWeaponFilterRepository() {
    }

    public static ZombiesWeaponFilterConfig loadOrCreate(MinecraftServer server, String mapName) {
        return loadOrCreate(ConfigPath.zombiesMapWeaponFilter(server, mapName));
    }

    public static ZombiesWeaponFilterConfig loadOrCreate(Path path) {
        serverConfigPath = path;
        try {
            if (Files.exists(path)) {
                String configJson = Files.readString(path);
                ZombiesWeaponFilterConfig loaded = GSON.fromJson(configJson, ZombiesWeaponFilterConfig.class);
                serverConfig = loaded != null ? loaded : new ZombiesWeaponFilterConfig();
                serverConfig.normalize();
                return serverConfig;
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Failed to load zombies weapon filter config: {}", path, e);
        }

        serverConfig = new ZombiesWeaponFilterConfig();
        serverConfig.normalize();
        lastSaveResult = save(serverConfig);
        return serverConfig;
    }

    public static ZombiesWeaponFilterConfig getConfig() {
        if (serverConfig == null) {
            serverConfig = new ZombiesWeaponFilterConfig();
            serverConfig.normalize();
        }
        return serverConfig;
    }

    public static void setConfig(ZombiesWeaponFilterConfig config) {
        serverConfig = config;
        if (serverConfig != null) {
            serverConfig.normalize();
        }
    }

    public static JsonSaveResult save(ZombiesWeaponFilterConfig config) {
        if (config == null || serverConfigPath == null) {
            lastSaveResult = JsonSaveResult.skipped(serverConfigPath, "Zombies weapon filter config save skipped because path or config is missing.");
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
            LOGGER.error("Failed to save zombies weapon filter config: {}", serverConfigPath, e);
            lastSaveResult = JsonSaveResult.failure(serverConfigPath, "Failed to save zombies weapon filter config: " + serverConfigPath, e);
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
