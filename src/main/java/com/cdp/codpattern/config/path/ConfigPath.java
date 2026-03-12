package com.cdp.codpattern.config.path;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

public enum ConfigPath {

    SERVERBACKPACK("serverconfig/codpattern/backpack_rules/backpack_config.json"),
    SERVER_FILTER("serverconfig/codpattern/backpack_rules/weapon_filter.json"),
    SERVER_TDM_CONFIG("serverconfig/codpattern/tdm_rules"),
    SERVER_TDM_MATCH_RECORDS("serverconfig/codpattern/tdm_match_records");

    private final String path;

    ConfigPath(String path) {
        this.path = path;
    }

    public Path getPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(path);
    }
}
