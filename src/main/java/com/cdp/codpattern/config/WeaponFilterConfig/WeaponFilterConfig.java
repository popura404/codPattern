package com.cdp.codpattern.config.WeaponFilterConfig;

import com.cdp.codpattern.config.BackPackConfig.BackpackConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 背包与物品发放逻辑的配置文件
 */
public class WeaponFilterConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path CONFIG_PATH;

    private static WeaponFilterConfig weaponFilterConfig;
    private static WeaponFilterConfig CLIENTweaponFilterConfig;

    private List<String> primaryWeaponTabs = new ArrayList<>();
    private List<String> secondaryWeaponTabs = new ArrayList<>();
    private boolean distributeToTaggedPlayersOnly = false;

    //默认弹匣携带倍数
    private Integer ammunitionPerMagazineMultiple = 3;

    //默认预制背包........................................
    public static BackpackConfig.Backpack.ItemData itemDataP1 = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:hk_g3\",GunCurrentAmmoCount:20,GunFireMode: \"AUTO\",HasBulletInBarrel:1}");
    public static BackpackConfig.Backpack.ItemData itemDataS1 = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:glock_17\",GunCurrentAmmoCount:17,GunFireMode: \"SEMI\",HasBulletInBarrel:1}");

    public static BackpackConfig.Backpack.ItemData itemDataP2 = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:ak47\",GunCurrentAmmoCount:30,GunFireMode: \"AUTO\",HasBulletInBarrel:1}");
    public static BackpackConfig.Backpack.ItemData itemDataS2 = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:deagle\",GunCurrentAmmoCount:7,GunFireMode: \"SEMI\",HasBulletInBarrel:1}");

    public static BackpackConfig.Backpack.ItemData itemDataP3 = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:m4a1\",GunCurrentAmmoCount:30,GunFireMode: \"AUTO\",HasBulletInBarrel:1}");
    public static BackpackConfig.Backpack.ItemData itemDataS3 = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:p320\",GunCurrentAmmoCount:12,GunFireMode: \"SEMI\",HasBulletInBarrel:1}");
    //..................................................

    //添加背包时默认......................................
    public static BackpackConfig.Backpack.ItemData itemDataADDP = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:m4a1\",GunCurrentAmmoCount:30,GunFireMode: \"AUTO\",HasBulletInBarrel:1}");
    public static BackpackConfig.Backpack.ItemData itemDataADDS = new BackpackConfig.Backpack.ItemData("tacz:modern_kinetic_gun", 1, "{GunId:\"tacz:p320\",GunCurrentAmmoCount:12,GunFireMode: \"SEMI\",HasBulletInBarrel:1}");
    //.................................................

    private static WeaponFilterConfig createDefault(Path path) {
        WeaponFilterConfig config = new WeaponFilterConfig();
        config.primaryWeaponTabs.add("rifle");
        config.primaryWeaponTabs.add("sniper");
        config.primaryWeaponTabs.add("shotgun");
        config.primaryWeaponTabs.add("smg");
        config.primaryWeaponTabs.add("mg");
        config.secondaryWeaponTabs.add("pistol");
        config.secondaryWeaponTabs.add("rpg");
        CONFIG_PATH = path;
        return config;
    }

    public static WeaponFilterConfig LoadorCreate(Path path){
        try {
            if (Files.exists(path)) {
                String json = Files.readString(path);
                weaponFilterConfig = GSON.fromJson(json, WeaponFilterConfig.class);
                return weaponFilterConfig;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        //文件不存在就直接创建
        weaponFilterConfig = createDefault(path);
        save(weaponFilterConfig);
        return weaponFilterConfig;
    }

    public static void save(WeaponFilterConfig config) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(config));
            WeaponFilterConfig.weaponFilterConfig = config;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //统一个人客户端的所有WeaponFilterConfig
    //.............................................................
    public static WeaponFilterConfig getWeaponFilterConfig(){
        return weaponFilterConfig;
    }

    public static void setWeaponFilterConfig(WeaponFilterConfig weaponFilterConfig) {
        WeaponFilterConfig.weaponFilterConfig = weaponFilterConfig;
    }

    public static WeaponFilterConfig getCLIENTweaponFilterConfig() {
        return CLIENTweaponFilterConfig;
    }

    public static void setCLIENTweaponFilterConfig(WeaponFilterConfig CLIENTweaponFilterConfig) {
        WeaponFilterConfig.CLIENTweaponFilterConfig = CLIENTweaponFilterConfig;
    }
    //..............................................................

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
