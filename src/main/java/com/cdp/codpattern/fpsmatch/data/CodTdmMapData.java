package com.cdp.codpattern.fpsmatch.data;

import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.data.save.SaveHolder;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TDM 地图数据序列化和保存
 * 数据将保存到 fpsmatch/<world>/cdptdm/ 文件夹
 */
@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CodTdmMapData {

    /**
     * 地图数据记录
     */
    public record MapData(
            String mapName,
            String levelName,
            AreaData areaData,
            Map<String, TeamData> teams) {
        public static final Codec<MapData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("mapName").forGetter(MapData::mapName),
                Codec.STRING.fieldOf("levelName").forGetter(MapData::levelName),
                AreaData.CODEC.fieldOf("areaData").forGetter(MapData::areaData),
                Codec.unboundedMap(Codec.STRING, TeamData.CODEC).fieldOf("teams").forGetter(MapData::teams))
                .apply(instance, MapData::new));
    }

    /**
     * 队伍数据记录
     */
    public record TeamData(
            String name,
            int playerLimit,
            List<SpawnPointData> spawnPoints) {
        public static final Codec<TeamData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(TeamData::name),
                Codec.INT.fieldOf("playerLimit").forGetter(TeamData::playerLimit),
                SpawnPointData.CODEC.listOf().fieldOf("spawnPoints").forGetter(TeamData::spawnPoints))
                .apply(instance, TeamData::new));
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

        event.registerData(MapData.class, CodTdmMap.GAME_TYPE, saveHolder);
    }

    /**
     * 加载单个地图
     */
    private static void loadMap(MapData data) {
        try {
            // 获取服务器世界
            ServerLevel level = ServerLifecycleHooks.getCurrentServer()
                    .getLevel(ServerLevel.OVERWORLD);

            if (level == null) {
                System.err.println("Failed to load TDM map: world not available");
                return;
            }

            // 创建地图
            CodTdmMap map = new CodTdmMap(level, data.mapName(), data.areaData());

            // 添加队伍和复活点
            for (Map.Entry<String, TeamData> entry : data.teams().entrySet()) {
                TeamData teamData = entry.getValue();
                map.addTeam(teamData.name(), teamData.playerLimit());

                BaseTeam team = map.getMapTeams().getTeamByName(teamData.name()).orElse(null);
                if (team != null) {
                    team.addAllSpawnPointData(teamData.spawnPoints());
                }
            }

            // 注册到 FPSMCore
            FPSMCore.getInstance().registerMap(CodTdmMap.GAME_TYPE, map);

        } catch (Exception e) {
            System.err.println("Failed to load TDM map: " + data.mapName());
            e.printStackTrace();
        }
    }

    /**
     * 保存所有地图
     */
    private static void saveAllMaps(FPSMDataManager manager) {
        FPSMCore.getInstance().getAllMaps().getOrDefault(CodTdmMap.GAME_TYPE, List.of())
                .stream()
                .filter(map -> map instanceof CodTdmMap)
                .map(map -> (CodTdmMap) map)
                .forEach(map -> {
                    MapData data = mapToData(map);
                    manager.saveData(data, map.mapName, true);
                });
    }

    /**
     * 将地图转换为数据对象
     */
    public static MapData mapToData(CodTdmMap map) {
        Map<String, TeamData> teams = new HashMap<>();

        for (BaseTeam team : map.getMapTeams().getTeams()) {
            List<SpawnPointData> spawnPoints = team.getSpawnPointsData();
            teams.put(team.name, new TeamData(team.name, team.getPlayerLimit(), spawnPoints));
        }

        return new MapData(
                map.mapName,
                map.getServerLevel().dimension().location().toString(),
                map.getMapArea(),
                teams);
    }
}
