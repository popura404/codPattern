package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.tactical.port.CodTacticalTdmActionPort;
import com.cdp.codpattern.app.tactical.port.CodTacticalTdmReadPort;
import com.cdp.codpattern.app.match.BuiltInGameModes;
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
        return BuiltInGameModes.TEAM_DEATHMATCH;
    }

    @Override
    public boolean teleportPlayerToSpawnPoint(ServerPlayer player, SpawnSelectionReason reason) {
        if (reason == SpawnSelectionReason.ROUND_START) {
            return super.teleportPlayerToSpawnPoint(player, SpawnPointKind.INITIAL);
        }
        return super.teleportPlayerToSpawnPoint(player, SpawnPointKind.DYNAMIC_CANDIDATE)
                || super.teleportPlayerToSpawnPoint(player, SpawnPointKind.INITIAL);
    }

    @Override
    public ModeRoomHandle roomHandle() {
        return createRoomHandle(tacticalReadPort, tacticalActionPort);
    }

    public CodTacticalTdmActionPort tacticalActionPort() {
        return tacticalActionPort;
    }

    public CodTacticalTdmReadPort tacticalReadPort() {
        return tacticalReadPort;
    }
}
