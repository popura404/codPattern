package com.cdp.codpattern.core.ConfigPath;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

public enum ConfigPath {

    SERVERBACKPACK("serverconfig/codpattern/backpackconfig"),
    SERVERFLITER("serverconfig/codpattern/filterconfig"),
    SERVER_ATTACHMENT_PRESET("serverconfig/codpattern/attachment_preset");


    private final String path;
    ConfigPath(String path){
        this.path = path;
    }
    public Path getPath(MinecraftServer server){
        return server.getWorldPath(LevelResource.ROOT).resolve(path);
    }
}
