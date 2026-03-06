package com.cdp.codpattern.config.backpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BackpackConfigRepository {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT)
            .create();

    private static Path serverConfigPath;
    private static BackpackConfig serverConfig;

    private BackpackConfigRepository() {
    }

    public static BackpackConfig loadOrCreate(Path path) {
        serverConfigPath = path;
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    BackpackConfig loaded = GSON.fromJson(reader, BackpackConfig.class);
                    serverConfig = loaded != null ? loaded : new BackpackConfig();
                    return serverConfig;
                }
            }
        } catch (IOException ignored) {
        }

        serverConfig = new BackpackConfig();
        save();
        return serverConfig;
    }

    public static BackpackConfig.PlayerBackpackData loadOrCreatePlayer(String uuid, Path path) {
        ensureLoaded(path);
        BackpackConfig.PlayerBackpackData playerData = serverConfig.getOrCreatePlayerData(uuid);
        save();
        return playerData;
    }

    public static BackpackConfig getConfig() {
        if (serverConfig == null) {
            serverConfig = new BackpackConfig();
        }
        return serverConfig;
    }

    public static void setConfig(BackpackConfig config) {
        serverConfig = config;
    }

    public static BackpackConfig.PlayerBackpackData getOrCreatePlayerData(String uuid) {
        return getConfig().getOrCreatePlayerData(uuid);
    }

    public static int addCustomBackpack(String uuid) {
        BackpackConfig.PlayerBackpackData playerData = getConfig().getOrCreatePlayerData(uuid);
        int selectedBeforeAdd = playerData.getSelectedBackpack();
        int id = playerData.getNextAvailableId();
        if (id == 0) {
            return -1;
        }

        BackpackConfig.Backpack newBackpack = new BackpackConfig.Backpack("自定义背包 #" + id);
        newBackpack.setItem_MAP("primary", BackpackConfig.getItemDataADDP());
        newBackpack.setItem_MAP("secondary", BackpackConfig.getItemDataADDS());
        newBackpack.setItem_MAP("tactical", BackpackConfig.getItemDataTactical());
        newBackpack.setItem_MAP("lethal", BackpackConfig.getItemDataLethal());

        if (playerData.addBackpack(id, newBackpack)) {
            if (playerData.getBackpacks_MAP().containsKey(selectedBeforeAdd)) {
                playerData.setSelectedBackpack(selectedBeforeAdd);
            }
            save();
            return id;
        }
        return -1;
    }

    public static void save() {
        if (serverConfigPath == null || serverConfig == null) {
            return;
        }
        try {
            Files.createDirectories(serverConfigPath.getParent());
            try (Writer writer = Files.newBufferedWriter(serverConfigPath)) {
                GSON.toJson(serverConfig, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static void ensureLoaded(Path path) {
        if (serverConfig == null || serverConfigPath == null || !serverConfigPath.equals(path)) {
            loadOrCreate(path);
        }
    }
}
