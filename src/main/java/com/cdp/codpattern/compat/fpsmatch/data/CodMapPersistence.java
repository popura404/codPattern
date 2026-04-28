package com.cdp.codpattern.compat.fpsmatch.data;

import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceProvider;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceRegistry;
import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.data.TeamSpawnProfile;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import org.slf4j.Logger;

public final class CodMapPersistence {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CodMapPersistence() {
    }

    public static void saveMap(BaseMap map) {
        if (map == null) {
            throw new IllegalArgumentException("Cannot persist null map");
        }
        FPSMCore core = FPSMCore.getInstance();
        try {
            ModeMapPersistenceProvider provider = ModeMapPersistenceRegistry.find(map.getGameType())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unsupported map type: " + map.getClass().getName()));
            provider.save(map, core.getFPSMDataManager());
            return;
        } catch (RuntimeException e) {
            LOGGER.error("Failed to persist map {}/{}", map.getGameType(), map.getMapName(), e);
            throw e;
        }
    }

    public static void saveMapOrRollback(BaseMap map, Runnable rollback) {
        try {
            saveMap(map);
        } catch (RuntimeException e) {
            if (rollback != null) {
                rollback.run();
            }
            throw e;
        }
    }

    public static void restoreSpawnProfile(BaseMap map, BaseTeam team, TeamSpawnProfile previousProfile) {
        if (team == null) {
            return;
        }
        team.setSpawnProfile(previousProfile);
        team.clearPlayerSpawnPointAssignments();
        if (map != null && map.isStart) {
            team.assignNextSpawnPoints(SpawnPointKind.INITIAL);
        }
    }
}
