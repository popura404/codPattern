package com.cdp.codpattern.app.tdm.service;

public final class FrontlineRegressionSuite {
    private FrontlineRegressionSuite() {
    }

    public static void main(String[] args) {
        FrontlineTeamMatchPolicyCompatTest.main(args);
        PvpPhaseScoreBaselineCompatTest.main(args);
        System.out.println("PASS Frontline regression suite (2/2)");
    }
}
