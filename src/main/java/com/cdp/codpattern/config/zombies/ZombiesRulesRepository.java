package com.cdp.codpattern.config.zombies;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ZombiesRulesRepository {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ZombiesRulesValidator VALIDATOR = new ZombiesRulesValidator();

    private static Path serverConfigPath;
    private static ZombiesRulesConfig serverConfig;
    private static JsonSaveResult lastSaveResult = JsonSaveResult.skipped(null, "Zombies rules config has not been saved yet.");
    private static List<com.cdp.codpattern.app.zombies.validation.ZombiesValidationIssue> lastValidationIssues = List.of();

    private ZombiesRulesRepository() {
    }

    public static ZombiesRulesConfig loadOrCreate(MinecraftServer server, String mapName) {
        return loadOrCreate(ZombiesConfigPaths.zombiesMapRulesConfig(server, mapName));
    }

    public static ZombiesRulesConfig loadOrCreate(Path path) {
        serverConfigPath = path;
        try {
            if (Files.exists(path)) {
                String configJson = Files.readString(path);
                boolean shouldBackfillStarterWeapon = missingStarterWeaponSection(configJson);
                ZombiesRulesConfig loaded = GSON.fromJson(configJson, ZombiesRulesConfig.class);
                serverConfig = loaded != null ? loaded : new ZombiesRulesConfig();
                lastValidationIssues = VALIDATOR.validate(serverConfig);
                serverConfig.normalize();
                if (shouldBackfillStarterWeapon) {
                    lastSaveResult = save(serverConfig);
                }
                return serverConfig;
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Failed to load zombies rules config: {}", path, e);
        }

        serverConfig = new ZombiesRulesConfig();
        serverConfig.normalize();
        lastValidationIssues = VALIDATOR.validate(serverConfig);
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
            lastValidationIssues = VALIDATOR.validate(serverConfig);
            serverConfig.normalize();
        } else {
            lastValidationIssues = List.of();
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
            lastValidationIssues = VALIDATOR.validate(config);
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

    public static List<com.cdp.codpattern.app.zombies.validation.ZombiesValidationIssue> getLastValidationIssues() {
        return lastValidationIssues == null ? List.of() : List.copyOf(lastValidationIssues);
    }

    private static boolean missingStarterWeaponSection(String configJson) {
        JsonElement parsed = JsonParser.parseString(configJson);
        return !parsed.isJsonObject() || !parsed.getAsJsonObject().has("starterWeapon");
    }

}
