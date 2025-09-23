package com.cdp.codpattern.config.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    private List<String> primaryWeaponTabs = new ArrayList<>();
    private List<String> secondaryWeaponTabs = new ArrayList<>();
    private boolean distributeToTaggedPlayersOnly = false;

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
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                return GSON.fromJson(json, WeaponFilterConfig.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        WeaponFilterConfig defaultConfig = createDefault();
        save(defaultConfig);
        return defaultConfig;
    }

    public static void save(WeaponFilterConfig config) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(config));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
}
