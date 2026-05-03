package com.cdp.codpattern.compat.fpsmatch.data;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.persistence.CommonModeMapData;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceProvider;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceRegistry;
import com.cdp.codpattern.app.zombies.map.ZombiesMapObjects;
import com.cdp.codpattern.app.zombies.map.object.ZombiesAmmoBoxData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesArmorStationData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesInitialSpawnData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesMysteryBoxData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesPowerSwitchData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesSodaMachineData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesUltimateMachineData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesWeaponWallData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesWindowData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesZombieSpawnData;
import com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry;
import com.cdp.codpattern.compat.fpsmatch.map.ZombiesMap;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.data.save.SaveHolder;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ZombiesMapData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ModeMapPersistenceProvider PERSISTENCE_PROVIDER = new ZombiesPersistenceProvider();

    public static ModeMapPersistenceProvider persistenceProvider() {
        return PERSISTENCE_PROVIDER;
    }

    public record MapData(
            int schemaVersion,
            String gameType,
            String mapName,
            String levelName,
            AreaData areaData,
            Optional<SpawnPointData> endtp,
            ZombiesMapObjects objects
    ) {
        public static final Codec<MapData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("schemaVersion", ZombiesMapPersistenceSupport.SCHEMA_VERSION).forGetter(MapData::schemaVersion),
                Codec.STRING.optionalFieldOf("gameType", BuiltInGameModes.ZOMBIES).forGetter(MapData::gameType),
                Codec.STRING.fieldOf("mapName").forGetter(MapData::mapName),
                Codec.STRING.fieldOf("levelName").forGetter(MapData::levelName),
                AreaData.CODEC.fieldOf("areaData").forGetter(MapData::areaData),
                SpawnPointData.CODEC.optionalFieldOf("endtp").forGetter(MapData::endtp),
                ZombiesMapObjects.CODEC.forGetter(MapData::objects)
        ).apply(instance, MapData::new));

        public MapData {
            endtp = endtp == null ? Optional.empty() : endtp;
            objects = objects == null ? ZombiesMapObjects.EMPTY : objects;
        }

        public MapData(
                int schemaVersion,
                String gameType,
                String mapName,
                String levelName,
                AreaData areaData,
                Optional<SpawnPointData> endtp,
                List<ZombiesInitialSpawnData> initialSpawns,
                List<ZombiesZombieSpawnData> zombieSpawns,
                List<ZombiesBarrierData> barriers
        ) {
            this(
                    schemaVersion,
                    gameType,
                    mapName,
                    levelName,
                    areaData,
                    endtp,
                    new ZombiesMapObjects(
                            initialSpawns,
                            zombieSpawns,
                            barriers,
                            List.of(),
                            List.of(),
                            List.of(),
                            Optional.empty(),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of()));
        }

        public List<ZombiesInitialSpawnData> initialSpawns() {
            return objects.initialSpawns();
        }

        public List<ZombiesZombieSpawnData> zombieSpawns() {
            return objects.zombieSpawns();
        }

        public List<ZombiesBarrierData> barriers() {
            return objects.barriers();
        }

        public List<ZombiesWeaponWallData> weaponWalls() {
            return objects.weaponWalls();
        }

        public List<ZombiesAmmoBoxData> ammoBoxes() {
            return objects.ammoBoxes();
        }

        public List<ZombiesArmorStationData> armorStations() {
            return objects.armorStations();
        }

        public Optional<ZombiesPowerSwitchData> powerSwitch() {
            return objects.powerSwitch();
        }

        public List<ZombiesSodaMachineData> sodaMachines() {
            return objects.sodaMachines();
        }

        public List<ZombiesUltimateMachineData> ultimateMachines() {
            return objects.ultimateMachines();
        }

        public List<ZombiesMysteryBoxData> mysteryBoxes() {
            return objects.mysteryBoxes();
        }

        public List<ZombiesWindowData> windows() {
            return objects.windows();
        }

    }

    @SubscribeEvent
    public static void onRegisterFPSMap(RegisterFPSMapEvent event) {
        ModeMapPersistenceRegistry.register(PERSISTENCE_PROVIDER);
        if (event != null) {
            event.registerGameType(BuiltInGameModes.ZOMBIES, ZombiesMap::new);
        }
    }

    @SubscribeEvent
    public static void onRegisterSaveData(RegisterFPSMSaveDataEvent event) {
        ModeMapPersistenceRegistry.register(PERSISTENCE_PROVIDER);
        SaveHolder<MapData> saveHolder = new SaveHolder.Builder<>(MapData.CODEC)
                .withReadHandler(ZombiesMapData::loadMap)
                .withWriteHandler(ZombiesMapData::saveAllMaps)
                .isGlobal(false)
                .build();

        event.registerData(MapData.class, BuiltInGameModes.ZOMBIES, saveHolder);
    }

    private static void loadMap(MapData data) {
        try {
            CommonModeMapData commonData = toCommonData(data);
            Optional<ServerLevel> level = ZombiesMapPersistenceSupport.resolveLevel(commonData, LOGGER, "zombies");
            if (level.isEmpty()) {
                return;
            }

            ZombiesMap map = (ZombiesMap) PERSISTENCE_PROVIDER.createMap(
                    level.get(),
                    commonData,
                    toPayload(data));
            FpsMatchMapRegistry.register(BuiltInGameModes.ZOMBIES, map);
        } catch (Exception e) {
            LOGGER.error("Failed to load zombies map {}", data == null ? "<null>" : data.mapName(), e);
        }
    }

    private static void saveAllMaps(FPSMDataManager manager) {
        FpsMatchMapRegistry.listMaps(BuiltInGameModes.ZOMBIES)
                .forEach(map -> PERSISTENCE_PROVIDER.save(map, manager));
    }

    public static MapData mapToData(ZombiesMap map) {
        ZombiesMapPersistenceSupport.ZombiesPayload payload = ZombiesMapPersistenceSupport.capturePayload(map);
        return new MapData(
                ZombiesMapPersistenceSupport.SCHEMA_VERSION,
                BuiltInGameModes.ZOMBIES,
                map.getMapName(),
                map.getServerLevel().dimension().location().toString(),
                map.getMapArea(),
                payload.matchEndTeleportPoint(),
                payload.objects());
    }

    private static CommonModeMapData toCommonData(MapData data) {
        return new CommonModeMapData(
                data.schemaVersion(),
                BuiltInGameModes.ZOMBIES,
                data.mapName(),
                data.levelName(),
                data.areaData(),
                data.endtp());
    }

    private static ZombiesMapPersistenceSupport.ZombiesPayload toPayload(MapData data) {
        return new ZombiesMapPersistenceSupport.ZombiesPayload(data.endtp(), data.objects());
    }

    private static final class ZombiesPersistenceProvider implements ModeMapPersistenceProvider {
        @Override
        public String gameType() {
            return BuiltInGameModes.ZOMBIES;
        }

        @Override
        public ZombiesMap createMap(ServerLevel level, CommonModeMapData commonData, Object payload) {
            ZombiesMap map = new ZombiesMap(level, commonData.mapName(), commonData.areaData());
            applyPayload(map, payload);
            return map;
        }

        @Override
        public Object capturePayload(BaseMap map) {
            return ZombiesMapPersistenceSupport.capturePayload(zombiesMap(map));
        }

        @Override
        public void applyPayload(BaseMap map, Object payload) {
            if (!(payload instanceof ZombiesMapPersistenceSupport.ZombiesPayload zombiesPayload)) {
                throw new IllegalArgumentException("Unsupported zombies map payload: " + payload);
            }
            ZombiesMapPersistenceSupport.applyPayload(zombiesMap(map), zombiesPayload);
        }

        @Override
        public void save(BaseMap map, FPSMDataManager manager) {
            ZombiesMap zombiesMap = zombiesMap(map);
            manager.saveData(mapToData(zombiesMap), zombiesMap.getMapName(), true);
        }

        @Override
        public FPSMDataManager.DeleteStatus delete(String mapName, FPSMDataManager manager) {
            return manager.deleteData(MapData.class, mapName);
        }

        private ZombiesMap zombiesMap(BaseMap map) {
            if (map instanceof ZombiesMap zombiesMap && BuiltInGameModes.ZOMBIES.equals(zombiesMap.getGameType())) {
                return zombiesMap;
            }
            throw new IllegalArgumentException("Unsupported zombies map type: " + map.getClass().getName());
        }
    }
}
