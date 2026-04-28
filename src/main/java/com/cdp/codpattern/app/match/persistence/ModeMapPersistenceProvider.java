package com.cdp.codpattern.app.match.persistence;

import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerLevel;

public interface ModeMapPersistenceProvider {
    String gameType();

    BaseMap createMap(ServerLevel level, CommonModeMapData commonData, Object payload);

    Object capturePayload(BaseMap map);

    void applyPayload(BaseMap map, Object payload);

    void save(BaseMap map, FPSMDataManager manager);

    FPSMDataManager.DeleteStatus delete(String mapName, FPSMDataManager manager);
}
