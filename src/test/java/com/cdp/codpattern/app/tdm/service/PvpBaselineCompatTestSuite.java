package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.client.gui.overlay.TdmHudOverlayStaticContractCompatTest;
import com.cdp.codpattern.client.state.TdmSettlementStateCompatTest;

public final class PvpBaselineCompatTestSuite {
    private PvpBaselineCompatTestSuite() {
    }

    public static void main(String[] args) throws Exception {
        PvpPhaseScoreBaselineCompatTest.main(args);
        PvpVoteBaselineCompatTest.main(args);
        PvpTeamRosterBaselineCompatTest.main(args);
        PvpModeBoundaryStaticContractCompatTest.main(args);
        TdmHudOverlayStaticContractCompatTest.main(args);
        TdmSettlementStateCompatTest.main(args);
        System.out.println("PASS PVP baseline compat suite (6/6)");
    }
}
