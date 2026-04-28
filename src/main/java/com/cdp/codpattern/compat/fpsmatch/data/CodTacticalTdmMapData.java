package com.cdp.codpattern.compat.fpsmatch.data;

import com.cdp.codpattern.app.match.persistence.CommonModeMapData;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceProvider;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceRegistry;
import com.cdp.codpattern.app.tactical.port.CodTacticalTdmActionPort;
import com.cdp.codpattern.app.tactical.port.CodTacticalTdmReadPort;
import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.compat.fpsmatch.map.CodTacticalTdmMap;
import com.cdp.codpattern.compat.fpsmatch.map.CodTacticalTdmMapAccess;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
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

@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CodTacticalTdmMapData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ModeMapPersistenceProvider PERSISTENCE_PROVIDER = new TacticalTdmPersistenceProvider();

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
        ModeMapPersistenceRegistry.register(PERSISTENCE_PROVIDER);
        SaveHolder<MapData> saveHolder = new SaveHolder.Builder<>(MapData.CODEC)
                .withReadHandler(CodTacticalTdmMapData::loadMap)
                .withWriteHandler(CodTacticalTdmMapData::saveAllMaps)
                .isGlobal(false)
                .build();

        event.registerData(MapData.class, TdmGameTypes.CDP_TACTICAL_TDM, saveHolder);
    }

    private static void loadMap(MapData data) {
        try {
            CommonModeMapData commonData = toCommonData(data);
            Optional<ServerLevel> level = CodTdmMapPersistenceSupport.resolveLevel(commonData, LOGGER, "tactical TDM");
            if (level.isEmpty()) {
                return;
            }

            CodTacticalTdmMap map = (CodTacticalTdmMap) PERSISTENCE_PROVIDER.createMap(
                    level.get(),
                    commonData,
                    toPayload(data));
            CodTacticalTdmMapAccess.registerMap(map);
        } catch (Exception e) {
            LOGGER.error("Failed to load tactical TDM map {}", data.mapName(), e);
        }
    }

    private static void saveAllMaps(FPSMDataManager manager) {
        FPSMCore.getInstance()
                .getAllMaps()
                .getOrDefault(TdmGameTypes.CDP_TACTICAL_TDM, List.of())
                .forEach(map -> PERSISTENCE_PROVIDER.save(map, manager));
    }

    public static MapData mapToData(CodTacticalTdmReadPort readPort) {
        CodTdmMapPersistenceSupport.TeamPayload payload = CodTdmMapPersistenceSupport.capturePayload(readPort);
        return new MapData(
                readPort.mapName(),
                readPort.dimensionId(),
                readPort.mapArea(),
                payload.teams(),
                payload.matchEndTeleportPoint()
        );
    }

    private static CommonModeMapData toCommonData(MapData data) {
        return new CommonModeMapData(
                CodTdmMapPersistenceSupport.SCHEMA_VERSION,
                TdmGameTypes.CDP_TACTICAL_TDM,
                data.mapName(),
                data.levelName(),
                data.areaData(),
                data.matchEndTeleportPoint());
    }

    private static CodTdmMapPersistenceSupport.TeamPayload toPayload(MapData data) {
        return new CodTdmMapPersistenceSupport.TeamPayload(data.teams(), data.matchEndTeleportPoint());
    }

    private static final class TacticalTdmPersistenceProvider implements ModeMapPersistenceProvider {
        @Override
        public String gameType() {
            return TdmGameTypes.CDP_TACTICAL_TDM;
        }

        @Override
        public CodTacticalTdmMap createMap(ServerLevel level, CommonModeMapData commonData, Object payload) {
            CodTacticalTdmMap map = CodTacticalTdmMapAccess.createMap(level, commonData.mapName(), commonData.areaData());
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
                throw new IllegalArgumentException("Unsupported tactical TDM map payload: " + payload);
            }
            CodTacticalTdmActionPort actionPort = CodTacticalTdmMapAccess.actionPort(tacticalMap(map));
            CodTdmMapPersistenceSupport.applyPayload(actionPort, teamPayload);
        }

        @Override
        public void save(com.phasetranscrystal.fpsmatch.core.map.BaseMap map, FPSMDataManager manager) {
            CodTacticalTdmReadPort readPort = readPort(map);
            manager.saveData(mapToData(readPort), readPort.mapName(), true);
        }

        @Override
        public FPSMDataManager.DeleteStatus delete(String mapName, FPSMDataManager manager) {
            return manager.deleteData(MapData.class, mapName);
        }

        private CodTacticalTdmReadPort readPort(com.phasetranscrystal.fpsmatch.core.map.BaseMap map) {
            return CodTacticalTdmMapAccess.readPort(tacticalMap(map));
        }

        private CodTacticalTdmMap tacticalMap(com.phasetranscrystal.fpsmatch.core.map.BaseMap map) {
            if (map instanceof CodTacticalTdmMap tacticalMap) {
                return tacticalMap;
            }
            throw new IllegalArgumentException("Unsupported tactical TDM map type: " + map.getClass().getName());
        }
    }
}
