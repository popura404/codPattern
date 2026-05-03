package com.cdp.codpattern.compat.fpsmatch.data;

import com.cdp.codpattern.app.match.persistence.CommonModeMapData;
import com.cdp.codpattern.app.zombies.map.ZombiesMapObjects;
import com.cdp.codpattern.compat.fpsmatch.map.ZombiesMap;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.Optional;

final class ZombiesMapPersistenceSupport {
    static final int SCHEMA_VERSION = 1;

    private ZombiesMapPersistenceSupport() {
    }

    record ZombiesPayload(
            Optional<SpawnPointData> matchEndTeleportPoint,
            ZombiesMapObjects objects
    ) {
        ZombiesPayload {
            matchEndTeleportPoint = matchEndTeleportPoint == null ? Optional.empty() : matchEndTeleportPoint;
            objects = objects == null ? ZombiesMapObjects.EMPTY : objects;
        }
    }

    static Optional<ServerLevel> resolveLevel(CommonModeMapData commonData, Logger logger, String modeLabel) {
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            logger.error("Failed to load {} map {}: server not ready", modeLabel, commonData.mapName());
            return Optional.empty();
        }
        ResourceLocation levelId = ResourceLocation.tryParse(commonData.levelName());
        if (levelId == null) {
            logger.error("Failed to load {} map {}: invalid levelName={}",
                    modeLabel,
                    commonData.mapName(),
                    commonData.levelName());
            return Optional.empty();
        }
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, levelId);
        ServerLevel level = ServerLifecycleHooks.getCurrentServer().getLevel(levelKey);
        if (level == null) {
            logger.error("Failed to load {} map {}: dimension {} not found",
                    modeLabel,
                    commonData.mapName(),
                    commonData.levelName());
            return Optional.empty();
        }
        return Optional.of(level);
    }

    static ZombiesPayload capturePayload(ZombiesMap map) {
        return new ZombiesPayload(map.matchEndTeleportPoint(), map.objects());
    }

    static void applyPayload(ZombiesMap map, ZombiesPayload payload) {
        ZombiesPayload resolvedPayload = payload == null
                ? new ZombiesPayload(Optional.empty(), ZombiesMapObjects.EMPTY)
                : payload;
        map.applyObjects(resolvedPayload.objects());
        resolvedPayload.matchEndTeleportPoint().ifPresent(map::setMatchEndTeleportPoint);
    }

}
