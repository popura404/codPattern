package com.cdp.codpattern.app.tdm.service;

public final class TeamDeathmatchRegressionSuite {
    private TeamDeathmatchRegressionSuite() {
    }

    public static void main(String[] args) throws Exception {
        TeamDeathmatchTeamMatchPolicyCompatTest.main(args);
        PvpTeamRosterBaselineCompatTest.main(args);
        System.out.println("PASS Team Deathmatch regression suite (2/2)");
    }
}
