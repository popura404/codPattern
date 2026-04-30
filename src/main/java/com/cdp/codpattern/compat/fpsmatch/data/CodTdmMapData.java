package com.cdp.codpattern.compat.fpsmatch.data;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.GameModeBootstrap;
import com.cdp.codpattern.app.match.persistence.CommonModeMapData;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceProvider;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmMap;
import com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.TeamSpawnProfile;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.data.save.SaveHolder;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TDM 地图数据序列化和保存
 * 数据将保存到 fpsmatch/<world>/frontline/ 文件夹
 */
@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CodTdmMapData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ModeMapPersistenceProvider PERSISTENCE_PROVIDER = new FrontlinePersistenceProvider();

    public static ModeMapPersistenceProvider persistenceProvider() {
        return PERSISTENCE_PROVIDER;
    }

    /**
     * 地图数据记录
     */
    public record MapData(
            String mapName,
            String levelName,
            AreaData areaData,
            Map<String, TeamData> teams,
            Optional<SpawnPointData> matchEndTeleportPoint) {
        public static final Codec<MapData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("mapName").forGetter(MapData::mapName),
                Codec.STRING.fieldOf("levelName").forGetter(MapData::levelName),
                AreaData.CODEC.fieldOf("areaData").forGetter(MapData::areaData),
                Codec.unboundedMap(Codec.STRING, TeamData.CODEC).fieldOf("teams").forGetter(MapData::teams),
                SpawnPointData.CODEC.optionalFieldOf("matchEndTeleportPoint").forGetter(MapData::matchEndTeleportPoint))
                .apply(instance, MapData::new));
    }

    /**
     * 队伍数据记录
     */
    public record TeamData(
            String name,
            int playerLimit,
            List<SpawnPointData> initialSpawnPoints,
            List<SpawnPointData> dynamicSpawnCandidates) {
        public static final Codec<TeamData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(TeamData::name),
                Codec.INT.fieldOf("playerLimit").forGetter(TeamData::playerLimit),
                SpawnPointData.CODEC.listOf().optionalFieldOf("initialSpawnPoints", List.of()).forGetter(TeamData::initialSpawnPoints),
                SpawnPointData.CODEC.listOf().optionalFieldOf("dynamicSpawnCandidates", List.of()).forGetter(TeamData::dynamicSpawnCandidates),
                SpawnPointData.CODEC.listOf().optionalFieldOf("spawnPoints", List.of()).forGetter(data -> List.of()))
                .apply(instance, TeamData::fromCodec));

        private static TeamData fromCodec(
                String name,
                int playerLimit,
                List<SpawnPointData> initialSpawnPoints,
                List<SpawnPointData> dynamicSpawnCandidates,
                List<SpawnPointData> legacySpawnPoints
        ) {
            List<SpawnPointData> resolvedInitial = (initialSpawnPoints == null || initialSpawnPoints.isEmpty())
                    ? legacySpawnPoints
                    : initialSpawnPoints;
            return new TeamData(
                    name,
                    playerLimit,
                    resolvedInitial == null ? List.of() : resolvedInitial,
                    dynamicSpawnCandidates == null ? List.of() : dynamicSpawnCandidates
            );
        }

        public TeamSpawnProfile toSpawnProfile() {
            return new TeamSpawnProfile(initialSpawnPoints, dynamicSpawnCandidates);
        }

        public static TeamData fromSpawnProfile(String name, int playerLimit, TeamSpawnProfile spawnProfile) {
            TeamSpawnProfile profile = spawnProfile == null ? TeamSpawnProfile.empty() : spawnProfile;
            return new TeamData(name, playerLimit, profile.initialSpawnPoints(), profile.dynamicSpawnCandidates());
        }
    }

    /**
     * 注册数据保存处理器
     */
    @SubscribeEvent
    public static void onRegisterSaveData(RegisterFPSMSaveDataEvent event) {
        GameModeBootstrap.registerPersistenceProviders();
        SaveHolder<MapData> saveHolder = new SaveHolder.Builder<>(MapData.CODEC)
                .withReadHandler(CodTdmMapData::loadMap)
                .withWriteHandler(CodTdmMapData::saveAllMaps)
                .isGlobal(false) // 保存到世界文件夹
                .build();

        event.registerData(MapData.class, BuiltInGameModes.FRONTLINE, saveHolder);
    }

    /**
     * 加载单个地图
     */
    private static void loadMap(MapData data) {
        try {
            CommonModeMapData commonData = toCommonData(data);
            Optional<ServerLevel> level = CodTdmMapPersistenceSupport.resolveLevel(commonData, LOGGER, "TDM");
            if (level.isEmpty()) {
                return;
            }

            CodTdmMap map = (CodTdmMap) PERSISTENCE_PROVIDER.createMap(
                    level.get(),
                    commonData,
                    toPayload(data));
            FpsMatchMapRegistry.register(BuiltInGameModes.FRONTLINE, map);

        } catch (Exception e) {
            LOGGER.error("Failed to load TDM map {}", data.mapName(), e);
        }
    }

    /**
     * 保存所有地图
     */
    private static void saveAllMaps(FPSMDataManager manager) {
        FpsMatchMapRegistry.listMaps(BuiltInGameModes.FRONTLINE)
                .forEach(map -> PERSISTENCE_PROVIDER.save(map, manager));
    }

    /**
     * 将地图转换为数据对象
     */
    public static MapData mapToData(CodTdmReadPort readPort) {
        CodTdmMapPersistenceSupport.TeamPayload payload = CodTdmMapPersistenceSupport.capturePayload(readPort);
        return new MapData(
                readPort.mapName(),
                readPort.dimensionId(),
                readPort.mapArea(),
                payload.teams(),
                payload.matchEndTeleportPoint());
    }

    private static CommonModeMapData toCommonData(MapData data) {
        return new CommonModeMapData(
                CodTdmMapPersistenceSupport.SCHEMA_VERSION,
                BuiltInGameModes.FRONTLINE,
                data.mapName(),
                data.levelName(),
                data.areaData(),
                data.matchEndTeleportPoint());
    }

    private static CodTdmMapPersistenceSupport.TeamPayload toPayload(MapData data) {
        return new CodTdmMapPersistenceSupport.TeamPayload(data.teams(), data.matchEndTeleportPoint());
    }

    private static final class FrontlinePersistenceProvider implements ModeMapPersistenceProvider {
        @Override
        public String gameType() {
            return BuiltInGameModes.FRONTLINE;
        }

        @Override
        public CodTdmMap createMap(ServerLevel level, CommonModeMapData commonData, Object payload) {
            CodTdmMap map = new CodTdmMap(level, commonData.mapName(), commonData.areaData());
            applyPayload(map, payload);
            return map;
        }

        @Override
        public Object capturePayload(com.phasetranscrystal.fpsmatch.core.map.BaseMap map) {
            return CodTdmMapPersistenceSupport.capturePayload(readPort(map));
        }

        @Override
        public void applyPayload(com.phasetranscrystal.fpsmatch.core.map.BaseMap map, Object payload) {
            if (!(payload instanceof CodTdmMapPersistenceSupport.TeamPayload teamPayload)) {
                throw new IllegalArgumentException("Unsupported TDM map payload: " + payload);
            }
            CodTdmMapPersistenceSupport.applyPayload(tdmMap(map).actionPort(), teamPayload);
        }

        @Override
        public void save(com.phasetranscrystal.fpsmatch.core.map.BaseMap map, FPSMDataManager manager) {
            CodTdmReadPort readPort = readPort(map);
            manager.saveData(mapToData(readPort), readPort.mapName(), true);
        }

        @Override
        public FPSMDataManager.DeleteStatus delete(String mapName, FPSMDataManager manager) {
            return manager.deleteData(MapData.class, mapName);
        }

        private CodTdmReadPort readPort(com.phasetranscrystal.fpsmatch.core.map.BaseMap map) {
            return tdmMap(map).readPort();
        }

        private CodTdmMap tdmMap(com.phasetranscrystal.fpsmatch.core.map.BaseMap map) {
            if (map instanceof CodTdmMap tdmMap && BuiltInGameModes.FRONTLINE.equals(tdmMap.getGameType())) {
                return tdmMap;
            }
            throw new IllegalArgumentException("Unsupported TDM map type: " + map.getClass().getName());
        }
    }
}
