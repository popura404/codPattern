package com.cdp.codpattern.config.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class WeaponFilterConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("codpattern/weapon_filter.json");

    // 服务端配置实例
    private static WeaponFilterConfig serverInstance;
    // 客户端缓存实例（从服务端同步）
    private static WeaponFilterConfig clientInstance;

    private List<String> primaryWeaponTabs = new ArrayList<>();
    private List<String> secondaryWeaponTabs = new ArrayList<>();
    private boolean distributeToTaggedPlayersOnly = false;
    private Integer ammunitionPerMagazineMultiple = 3;

    public static WeaponFilterConfig createDefault() {
        WeaponFilterConfig config = new WeaponFilterConfig();
        config.primaryWeaponTabs.add("rifle");
        config.primaryWeaponTabs.add("sniper");
        config.primaryWeaponTabs.add("shotgun");
        config.primaryWeaponTabs.add("smg");
        config.primaryWeaponTabs.add("mg");
        config.secondaryWeaponTabs.add("pistol");
        config.secondaryWeaponTabs.add("rpg");
        return config;
    }

    public static WeaponFilterConfig load() {
        // 判断是否为服务端环境（包括单人游戏）
        boolean isServerEnvironment = FMLEnvironment.dist == Dist.DEDICATED_SERVER;
        if (FMLEnvironment.dist == Dist.CLIENT) {
            try {
                Minecraft mc = Minecraft.getInstance();
                // 单人游戏或局域网主机也视为服务端环境
                isServerEnvironment = mc.hasSingleplayerServer() || mc.isLocalServer();
            } catch (Exception ignored) {
                // 如果获取失败，使用客户端缓存
            }
        }

        // 客户端（连接远程服务器）使用缓存
        if (!isServerEnvironment) {
            return clientInstance != null ? clientInstance : createDefault();
        }

        // 服务端/单人游戏从文件加载
        if (serverInstance != null) {
            return serverInstance;
        }

        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                serverInstance = GSON.fromJson(json, WeaponFilterConfig.class);
                return serverInstance;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        serverInstance = createDefault();
        save(serverInstance);
        return serverInstance;
    }

    public static void save(WeaponFilterConfig config) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(config));
            serverInstance = config;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 客户端设置从服务端同步的配置
     */
    public static void setClientInstance(WeaponFilterConfig config) {
        clientInstance = config;
    }

    // Getter方法保持不变
    public List<String> getPrimaryWeaponTabs() {
        return primaryWeaponTabs;
    }

    public List<String> getSecondaryWeaponTabs() {
        return secondaryWeaponTabs;
    }

    public boolean isDistributeToTaggedPlayersOnly() {
        return distributeToTaggedPlayersOnly;
    }

    public void setDistributeToTaggedPlayersOnly(boolean value) {
        this.distributeToTaggedPlayersOnly = value;
        save(this);
    }

    public Integer getAmmunitionPerMagazineMultiple() {
        return ammunitionPerMagazineMultiple;
    }
}
