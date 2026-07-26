package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tactical.port.CodTacticalTdmActionPort;
import com.cdp.codpattern.app.tactical.port.CodTacticalTdmReadPort;
import com.cdp.codpattern.app.tdm.model.TdmTeamMatchPolicies;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.server.level.ServerLevel;

public class CodTacticalTdmMap extends CodTdmMap {
    public CodTacticalTdmMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        super(serverLevel, mapName, areaData, TdmTeamMatchPolicies.teamDeathmatch());
    }

    public CodTacticalTdmActionPort tacticalActionPort() {
        return (CodTacticalTdmActionPort) actionPort();
    }

    public CodTacticalTdmReadPort tacticalReadPort() {
        return (CodTacticalTdmReadPort) readPort();
    }
}
