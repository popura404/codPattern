package com.cdp.codpattern.config.BackPackConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

public class BackpackConfigManager {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT)
            .create();

    private static Path CONFIG_PATH;

    private static BackpackConfig config;

    private static BackpackConfig.PlayerBackpackData CLIENTplayerBackpackData;

    private static BackpackConfig createDefault(Path path){
        config = new BackpackConfig();
        CONFIG_PATH = path;
        return config;
    }

    /**
     * 加载全局配置文件，用于服务端
     */
    public static BackpackConfig LoadorCreate(Path path){
        CONFIG_PATH = path;
            try {
                if (Files.exists(path)) {
                    try (Reader reader = Files.newBufferedReader(path)) {
                        config = GSON.fromJson(reader, BackpackConfig.class);
                        return config;
                    }
                }
            } catch (IOException ignored) {}

        // 没有文件就创建新的配置
        config = createDefault(path);
        save();
        return config;
    }

    /**
     * 加载个人配置文件，减小发包量，增加安全性，用于服务端向客户端
     */
    public static BackpackConfig.PlayerBackpackData LoadorCreatePlayer(String uuid , Path path){
        CONFIG_PATH = path;
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    config = GSON.fromJson(reader, BackpackConfig.class);
                }
            }else {
                //没有就创建
                config = createDefault(path);
                save();
            }
        } catch (IOException ignored) {}

        var playerData = config.getOrCreatePlayerData(uuid);
        save();
        return playerData;
    }

    /**
     * 保存配置到文件
     */
    public static void save() {
        if (CONFIG_PATH == null) {
            System.err.println("[BackpackConfigManager] CONFIG_PATH is null");
            return;
        }
            try {
                Files.createDirectories(CONFIG_PATH.getParent());
                try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                    GSON.toJson(config, writer);
                }
            } catch (IOException ignored) {}
    }

    /**
     * 为玩家添加背包并初始化
     * @param uuid 玩家UUID
     * @return 新背包的ID，-1表示添加失败
     */
    public static int addCustomBackpack(String uuid) {
        BackpackConfig.PlayerBackpackData playerData = config.getOrCreatePlayerData(uuid);
        int id = playerData.getNextAvailableId();

        if (id == 0) {
            return -1; // 背包已满
        }

        // 创建背包
        BackpackConfig.Backpack newBackpack = new BackpackConfig.Backpack("自定义背包 #" + id);
        newBackpack.setItem_MAP("primary", BackpackConfig.getItemDataADDP());
        newBackpack.setItem_MAP("secondary", BackpackConfig.getItemDataADDS());

        // 添加到玩家数据
        if (playerData.addBackpack(id, newBackpack)) {
            save();
            return id;
        }
        return -1;
    }

    /**
     * 设置配置
     */
    public static void setConfig(BackpackConfig config) {
        BackpackConfigManager.config = config;
    }

    /**
     * 获取配置实例
     */
    public static BackpackConfig getConfig() {
        return config;
    }

    public static void setCLIENTplayerBackpackData(BackpackConfig.PlayerBackpackData CLIENTplayerBackpackData) {
        BackpackConfigManager.CLIENTplayerBackpackData = CLIENTplayerBackpackData;
    }

    public static BackpackConfig.PlayerBackpackData getCLIENTplayerBackpackData() {
        return CLIENTplayerBackpackData;
    }
}
