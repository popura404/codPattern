package com.cdp.codpattern.compat.fpsmatch.data;

import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmMap;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmMapAccess;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import com.cdp.codpattern.app.tdm.model.CodTdmTeamPersistenceSnapshot;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.TeamSpawnProfile;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.data.save.SaveHolder;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TDM 地图数据序列化和保存
 * 数据将保存到 fpsmatch/<world>/cdptdm/ 文件夹
 */
@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CodTdmMapData {
    private static final Logger LOGGER = LogUtils.getLogger();

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
        SaveHolder<MapData> saveHolder = new SaveHolder.Builder<>(MapData.CODEC)
                .withReadHandler(CodTdmMapData::loadMap)
                .withWriteHandler(CodTdmMapData::saveAllMaps)
                .isGlobal(false) // 保存到世界文件夹
                .build();

        event.registerData(MapData.class, TdmGameTypes.CDP_TDM, saveHolder);
    }

    /**
     * 加载单个地图
     */
    private static void loadMap(MapData data) {
        try {
            if (ServerLifecycleHooks.getCurrentServer() == null) {
                LOGGER.error("Failed to load TDM map {}: server not ready", data.mapName());
                return;
            }
            ResourceLocation levelId = ResourceLocation.tryParse(data.levelName());
            if (levelId == null) {
                LOGGER.error("Failed to load TDM map {}: invalid levelName={}", data.mapName(), data.levelName());
                return;
            }
            ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, levelId);
            ServerLevel level = ServerLifecycleHooks.getCurrentServer().getLevel(levelKey);
            if (level == null) {
                LOGGER.error("Failed to load TDM map {}: dimension {} not found", data.mapName(), data.levelName());
                return;
            }

            // 创建地图
            CodTdmMap map = CodTdmMapAccess.createMap(level, data.mapName(), data.areaData());

            CodTdmActionPort actionPort = CodTdmMapAccess.actionPort(map);

            // 添加队伍和复活点
            for (Map.Entry<String, TeamData> entry : data.teams().entrySet()) {
                TeamData teamData = entry.getValue();
                actionPort.applyTeamSpawnProfile(teamData.name(), teamData.playerLimit(), teamData.toSpawnProfile());
            }
            data.matchEndTeleportPoint().ifPresent(actionPort::setMatchEndTeleportPoint);

            CodTdmMapAccess.registerMap(map);

        } catch (Exception e) {
            LOGGER.error("Failed to load TDM map {}", data.mapName(), e);
        }
    }

    /**
     * 保存所有地图
     */
    private static void saveAllMaps(FPSMDataManager manager) {
        CodTdmMapAccess.listReadPorts().forEach(readPort -> {
            MapData data = mapToData(readPort);
            manager.saveData(data, readPort.mapName(), true);
        });
    }

    /**
     * 将地图转换为数据对象
     */
    public static MapData mapToData(CodTdmReadPort readPort) {
        Map<String, TeamData> teams = new HashMap<>();

        for (CodTdmTeamPersistenceSnapshot team : readPort.teamPersistenceSnapshots()) {
            teams.put(team.name(), TeamData.fromSpawnProfile(team.name(), team.playerLimit(), team.spawnProfile()));
        }

        return new MapData(
                readPort.mapName(),
                readPort.dimensionId(),
                readPort.mapArea(),
                teams,
                readPort.matchEndTeleportPoint());
    }
}
