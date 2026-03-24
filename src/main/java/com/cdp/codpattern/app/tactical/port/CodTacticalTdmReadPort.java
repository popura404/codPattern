package com.cdp.codpattern.app.tactical.port;

import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;

public interface CodTacticalTdmReadPort extends CodTdmReadPort {
    @Override
    default String gameType() {
        return TdmGameTypes.CDP_TACTICAL_TDM;
    }
}
