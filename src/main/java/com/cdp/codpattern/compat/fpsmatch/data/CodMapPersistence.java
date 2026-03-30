package com.cdp.codpattern.compat.fpsmatch.data;

import com.cdp.codpattern.compat.fpsmatch.map.CodTacticalTdmMap;
import com.cdp.codpattern.compat.fpsmatch.map.CodTacticalTdmMapAccess;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmMap;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmMapAccess;
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
        FPSMCore core = FPSMCore.getInstance();
        try {
            if (map instanceof CodTacticalTdmMap tacticalMap) {
                core.getFPSMDataManager().saveData(
                        CodTacticalTdmMapData.mapToData(CodTacticalTdmMapAccess.readPort(tacticalMap)),
                        tacticalMap.getMapName(),
                        true);
                return;
            }
            if (map instanceof CodTdmMap tdmMap) {
                core.getFPSMDataManager().saveData(
                        CodTdmMapData.mapToData(CodTdmMapAccess.readPort(tdmMap)),
                        tdmMap.getMapName(),
                        true);
                return;
            }
        } catch (RuntimeException e) {
            LOGGER.error("Failed to persist map {}/{}", map.getGameType(), map.getMapName(), e);
            throw e;
        }

        throw new IllegalArgumentException("Unsupported map type: " + map.getClass().getName());
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
