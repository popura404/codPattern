package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tactical.port.CodTacticalTdmActionPort;
import com.cdp.codpattern.app.tactical.port.CodTacticalTdmReadPort;
import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.data.SpawnSelectionReason;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class CodTacticalTdmMap extends CodTdmMap {
    private final CodTacticalTdmActionPort tacticalActionPort;
    private final CodTacticalTdmReadPort tacticalReadPort;

    public CodTacticalTdmMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        super(serverLevel, mapName, areaData);
        this.tacticalActionPort = CodTacticalTdmPorts.wrapAction(super.actionPort());
        this.tacticalReadPort = CodTacticalTdmPorts.wrapRead(super.readPort());
    }

    @Override
    public String getGameType() {
        return TdmGameTypes.CDP_TACTICAL_TDM;
    }

    @Override
    public boolean teleportPlayerToSpawnPoint(ServerPlayer player, SpawnSelectionReason reason) {
        if (reason == SpawnSelectionReason.ROUND_START) {
            return super.teleportPlayerToSpawnPoint(player, SpawnPointKind.INITIAL);
        }
        return super.teleportPlayerToSpawnPoint(player, SpawnPointKind.DYNAMIC_CANDIDATE)
                || super.teleportPlayerToSpawnPoint(player, SpawnPointKind.INITIAL);
    }

    CodTacticalTdmActionPort tacticalActionPort() {
        return tacticalActionPort;
    }

    CodTacticalTdmReadPort tacticalReadPort() {
        return tacticalReadPort;
    }
}
