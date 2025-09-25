package com.cdp.codpattern.config.configmanager;

import com.cdp.codpattern.config.server.BackpackSelectionConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class BackpackConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("codpattern/backpacks.json");
    private static BackpackSelectionConfig config;

    // 客户端缓存的配置（仅纯客户端使用）
    private static BackpackSelectionConfig clientConfig;

    /**
     * 判断是否应该使用服务端配置
     */
    private static boolean shouldUseServerConfig() {
        // 专用服务器环境
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            return true;
        }

        // 单人游戏或本地局域网服务器（集成服务器）
        // 在这种情况下，配置文件应该被创建和保存
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // 如果是单人游戏或局域网主机，使用本地配置
            // 如果是连接到远程服务器的客户端，使用同步的配置
            try {
                // 检查是否在单人游戏或作为局域网主机
                if (Minecraft.getInstance().hasSingleplayerServer() ||
                        Minecraft.getInstance().isLocalServer()) {
                    return true;
                }
            } catch (Exception e) {
                // 在服务端线程调用时可能抛异常，忽略
            }
        }

        return false;
    }

    /**
     * 加载配置文件
     */
    public static void load() {
        // 只在服务端或单人游戏中加载
        if (shouldUseServerConfig()) {
            try {
                if (Files.exists(CONFIG_PATH)) {
                    try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                        config = GSON.fromJson(reader, BackpackSelectionConfig.class);
                    }
                } else {
                    // 创建新的配置
                    config = new BackpackSelectionConfig();
                    save();
                }
            } catch (IOException e) {
                e.printStackTrace();
                config = new BackpackSelectionConfig();
            }
        }
    }

    /**
     * 保存配置到文件
     */
    public static void save() {
        // 只在服务端或单人游戏中保存
        if (shouldUseServerConfig()) {
            try {
                Files.createDirectories(CONFIG_PATH.getParent());
                try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                    GSON.toJson(getConfig(), writer);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取配置实例
     */
    public static BackpackSelectionConfig getConfig() {
        // 服务端或单人游戏使用本地配置
        if (shouldUseServerConfig()) {
            if (config == null) {
                load();
            }
            return config;
        }

        // 纯客户端（连接到远程服务器）使用同步的配置
        if (clientConfig == null) {
            clientConfig = new BackpackSelectionConfig();
        }
        return clientConfig;
    }

    /**
     * 客户端设置从服务端同步的配置（仅用于连接远程服务器时）
     */
    @OnlyIn(Dist.CLIENT)
    public static void setClientConfig(BackpackSelectionConfig syncedConfig) {
        clientConfig = syncedConfig;
    }

    /**
     * 为玩家添加背包并初始化
     * @param uuid 玩家UUID
     * @return 新背包的ID，-1表示添加失败
     */
    public static int addCustomBackpack(String uuid) {
        BackpackSelectionConfig.PlayerBackpackData playerData = getConfig().getOrCreatePlayerData(uuid);
        int id = playerData.getNextAvailableId();

        if (id == 0) {
            return -1; // 背包已满
        }

        // 创建新背包
        BackpackSelectionConfig.Backpack newBackpack = new BackpackSelectionConfig.Backpack("自定义背包 #" + id);
        newBackpack.setItem_MAP("primary",
                new BackpackSelectionConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1,
                        "{GunId:\"tacz:m4a1\",GunCurrentAmmoCount:30,GunFireMode: \"AUTO\",HasBulletInBarrel:1}"));
        newBackpack.setItem_MAP("secondary",
                new BackpackSelectionConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1,
                        "{GunId:\"tacz:p320\",GunCurrentAmmoCount:12,GunFireMode: \"SEMI\",HasBulletInBarrel:1}"));

        // 添加到玩家数据
        if (playerData.addBackpack(id, newBackpack)) {
            save();
            return id;
        }

        return -1;
    }
}
