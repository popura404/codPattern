package com.cdp.codpattern.app.tactical.port;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;

public interface CodTacticalTdmActionPort extends CodTdmActionPort {
    @Override
    default String gameType() {
        return BuiltInGameModes.TEAM_DEATHMATCH;
    }
}
