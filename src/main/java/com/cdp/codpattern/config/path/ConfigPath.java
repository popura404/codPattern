package com.cdp.codpattern.config.path;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

public enum ConfigPath {

    SERVERBACKPACK("serverconfig/codpattern/backpackconfig"),
    SERVER_FILTER("serverconfig/codpattern/filterconfig"),
    SERVER_ATTACHMENT_PRESET("serverconfig/codpattern/attachment_preset"),
    SERVER_TDM_CONFIG("serverconfig/codpattern/tdmconfig");

    private final String path;

    ConfigPath(String path) {
        this.path = path;
    }

    public Path getPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(path);
    }
}
