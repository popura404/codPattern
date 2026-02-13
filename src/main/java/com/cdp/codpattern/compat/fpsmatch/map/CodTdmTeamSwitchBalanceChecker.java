package com.cdp.codpattern.compat.fpsmatch.map;

@FunctionalInterface
interface CodTdmTeamSwitchBalanceChecker {
    boolean canSwitch(String currentTeam, String targetTeam, int maxTeamDiff);
}
