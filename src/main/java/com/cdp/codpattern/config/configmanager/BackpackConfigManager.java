package com.cdp.codpattern.config.configmanager;

import com.cdp.codpattern.config.server.BagSelectConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

//@OnlyIn(Dist.DEDICATED_SERVER)
public class BackpackConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("codpattern/backpacks.json");
    private static BagSelectConfig config;

    /**
     * 加载配置文件
     */
    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    config = GSON.fromJson(reader, BagSelectConfig.class);
                }
            } else {
                // 创建新的配置
                config = new BagSelectConfig();
                save();
            }
        } catch (IOException e) {
            e.printStackTrace();
            config = new BagSelectConfig();
        }
    }

    /**
     * 保存配置到文件
     */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取配置实例
     */
    public static BagSelectConfig getConfig() {
        if (config == null) {
            load();
        }
        return config;
    }

    /**
     * 为玩家添加背包并初始化
     * @param uuid 玩家UUID
     * @return 新背包的ID，-1表示添加失败
     */
    public static int addCustomBackpack(String uuid) {

        BagSelectConfig.PlayerBackpackData playerData = getConfig().getOrCreatePlayerData(uuid);
        int id = playerData.getNextAvailableId();

        // 创建新背包
        BagSelectConfig.Backpack newBackpack = new BagSelectConfig.Backpack("自定义背包 #" + id);
        newBackpack.setItem_MAP("primary",
                new BagSelectConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"swpu:m4a1\",GunCurrentAmmoCount:30,GunFireMode: \"AUTO\",HasBulletInBarrel:1}"));
        newBackpack.setItem_MAP("secondary",
                new BagSelectConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"swpu:p320\",GunCurrentAmmoCount:12,GunFireMode: \"SEMI\",HasBulletInBarrel:1}"));

        // 添加到玩家数据
        if (playerData.addBackpack(id,newBackpack)) {
            save();
            return id;
        }

        return -1;
    }
}
