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
                WeaponFilterConfig loaded = GSON.fromJson(Files.readString(path), WeaponFilterConfig.class);
                serverConfig = loaded != null ? loaded : new WeaponFilterConfig();
                return serverConfig;
            }
        } catch (IOException ignored) {
        }

        serverConfig = new WeaponFilterConfig();
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
