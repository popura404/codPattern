package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;

record CodTdmMapAssemblyOutput(
        CodTdmMapLifecycleRuntime lifecycleRuntime,
        CodTdmActionPort actionPort,
        CodTdmReadPort readPort
) {
}
