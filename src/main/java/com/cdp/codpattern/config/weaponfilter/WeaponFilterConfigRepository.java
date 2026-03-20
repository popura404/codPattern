package com.cdp.codpattern.config.weaponfilter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WeaponFilterConfigRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path serverConfigPath;
    private static WeaponFilterConfig serverConfig;

    private WeaponFilterConfigRepository() {
    }

    public static WeaponFilterConfig loadOrCreate(Path path) {
        serverConfigPath = path;
        try {
            if (Files.exists(path)) {
                String configJson = Files.readString(path);
                WeaponFilterConfig loaded = GSON.fromJson(configJson, WeaponFilterConfig.class);
                serverConfig = loaded != null ? loaded : new WeaponFilterConfig();
                serverConfig.normalize();
                String normalizedJson = GSON.toJson(serverConfig);
                if (!normalizedJson.equals(configJson)) {
                    save(serverConfig);
                }
                return serverConfig;
            }
        } catch (IOException ignored) {
        }

        serverConfig = new WeaponFilterConfig();
        serverConfig.normalize();
        save(serverConfig);
        return serverConfig;
    }

    public static WeaponFilterConfig getConfig() {
        return serverConfig;
    }

    public static void setConfig(WeaponFilterConfig config) {
        serverConfig = config;
    }

    public static void save(WeaponFilterConfig config) {
        if (config == null || serverConfigPath == null) {
            return;
        }
        try {
            Files.createDirectories(serverConfigPath.getParent());
            Files.writeString(serverConfigPath, GSON.toJson(config));
            serverConfig = config;
        } catch (IOException ignored) {
        }
    }
}
