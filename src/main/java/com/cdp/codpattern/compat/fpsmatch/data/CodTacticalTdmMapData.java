package com.cdp.codpattern.compat.fpsmatch.data;

import com.cdp.codpattern.app.tactical.port.CodTacticalTdmActionPort;
import com.cdp.codpattern.app.tactical.port.CodTacticalTdmReadPort;
import com.cdp.codpattern.app.tdm.model.CodTdmTeamPersistenceSnapshot;
import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.compat.fpsmatch.map.CodTacticalTdmMap;
import com.cdp.codpattern.compat.fpsmatch.map.CodTacticalTdmMapAccess;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
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
import java.util.Map;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CodTacticalTdmMapData {
    private static final Logger LOGGER = LogUtils.getLogger();

    public record MapData(
            String mapName,
            String levelName,
            AreaData areaData,
            Map<String, CodTdmMapData.TeamData> teams,
            Optional<SpawnPointData> matchEndTeleportPoint
    ) {
        public static final Codec<MapData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("mapName").forGetter(MapData::mapName),
                Codec.STRING.fieldOf("levelName").forGetter(MapData::levelName),
                AreaData.CODEC.fieldOf("areaData").forGetter(MapData::areaData),
                Codec.unboundedMap(Codec.STRING, CodTdmMapData.TeamData.CODEC).fieldOf("teams").forGetter(MapData::teams),
                SpawnPointData.CODEC.optionalFieldOf("matchEndTeleportPoint").forGetter(MapData::matchEndTeleportPoint))
                .apply(instance, MapData::new));
    }

    @SubscribeEvent
    public static void onRegisterSaveData(RegisterFPSMSaveDataEvent event) {
        SaveHolder<MapData> saveHolder = new SaveHolder.Builder<>(MapData.CODEC)
                .withReadHandler(CodTacticalTdmMapData::loadMap)
                .withWriteHandler(CodTacticalTdmMapData::saveAllMaps)
                .isGlobal(false)
                .build();

        event.registerData(MapData.class, TdmGameTypes.CDP_TACTICAL_TDM, saveHolder);
    }

    private static void loadMap(MapData data) {
        try {
            if (ServerLifecycleHooks.getCurrentServer() == null) {
                LOGGER.error("Failed to load tactical TDM map {}: server not ready", data.mapName());
                return;
            }
            ResourceLocation levelId = ResourceLocation.tryParse(data.levelName());
            if (levelId == null) {
                LOGGER.error("Failed to load tactical TDM map {}: invalid levelName={}", data.mapName(), data.levelName());
                return;
            }
            ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, levelId);
            ServerLevel level = ServerLifecycleHooks.getCurrentServer().getLevel(levelKey);
            if (level == null) {
                LOGGER.error("Failed to load tactical TDM map {}: dimension {} not found", data.mapName(), data.levelName());
                return;
            }

            CodTacticalTdmMap map = CodTacticalTdmMapAccess.createMap(level, data.mapName(), data.areaData());
            CodTacticalTdmActionPort actionPort = CodTacticalTdmMapAccess.actionPort(map);

            for (Map.Entry<String, CodTdmMapData.TeamData> entry : data.teams().entrySet()) {
                CodTdmMapData.TeamData teamData = entry.getValue();
                actionPort.applyTeamSpawnProfile(teamData.name(), teamData.playerLimit(), teamData.toSpawnProfile());
            }
            data.matchEndTeleportPoint().ifPresent(actionPort::setMatchEndTeleportPoint);
            CodTacticalTdmMapAccess.registerMap(map);
        } catch (Exception e) {
            LOGGER.error("Failed to load tactical TDM map {}", data.mapName(), e);
        }
    }

    private static void saveAllMaps(FPSMDataManager manager) {
        CodTacticalTdmMapAccess.listReadPorts().forEach(readPort -> {
            MapData data = mapToData(readPort);
            manager.saveData(data, readPort.mapName(), true);
        });
    }

    public static MapData mapToData(CodTacticalTdmReadPort readPort) {
        Map<String, CodTdmMapData.TeamData> teams = new HashMap<>();
        for (CodTdmTeamPersistenceSnapshot team : readPort.teamPersistenceSnapshots()) {
            teams.put(team.name(), CodTdmMapData.TeamData.fromSpawnProfile(team.name(), team.playerLimit(), team.spawnProfile()));
        }
        return new MapData(
                readPort.mapName(),
                readPort.dimensionId(),
                readPort.mapArea(),
                teams,
                readPort.matchEndTeleportPoint()
        );
    }
}
