package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.app.match.runtime.roster.RoomRosterSyncCoordinator;
import com.cdp.codpattern.app.match.runtime.roster.RoomRosterSyncCoordinatorCompatTest;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.match.RoomRosterDelta;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public final class PvpTeamRosterBaselineCompatTest {
    private PvpTeamRosterBaselineCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        verifyTeamBalance();
        verifyRosterDeltaMasksAndPingBuckets();
        RoomRosterSyncCoordinatorCompatTest.main(args);
        System.out.println("PASS PVP team/roster baseline compat");
    }

    private static void verifyTeamBalance() {
        List<TeamBalanceService.TeamSnapshot> balanced = List.of(
                new TeamBalanceService.TeamSnapshot("kortac", 1),
                new TeamBalanceService.TeamSnapshot("specgru", 2));
        requireEquals("kortac",
                TeamBalanceService.chooseAutoJoinTeam(balanced, ignored -> false, 1).orElseThrow(),
                "auto join chooses the smaller team");
        requireTrue(TeamBalanceService.canJoinWithBalance(balanced, "kortac", 1),
                "joining the smaller team preserves the configured balance");
        requireFalse(TeamBalanceService.canJoinWithBalance(balanced, "specgru", 1),
                "joining the larger team is rejected by the configured balance");
        requireFalse(TeamBalanceService.canSwitchWithBalance(balanced, "kortac", "specgru", 1),
                "switching from smaller to larger team is rejected");
        requireTrue(TeamBalanceService.canSwitchWithBalance(balanced, "specgru", "kortac", 1),
                "switching from larger to smaller team is allowed");
        requireTrue(TeamBalanceService.canJoinWithBalance(balanced, "specgru", -1),
                "negative max-team-difference keeps the existing unlimited behavior");
    }

    private static void verifyRosterDeltaMasksAndPingBuckets() throws Exception {
        Method changedMask = RoomRosterSyncCoordinator.class.getDeclaredMethod(
                "buildChangedMask", PlayerInfo.class, PlayerInfo.class);
        changedMask.setAccessible(true);
        Method pingBucket = RoomRosterSyncCoordinator.class.getDeclaredMethod("pingBucket", int.class);
        pingBucket.setAccessible(true);

        UUID id = UUID.randomUUID();
        PlayerInfo previous = new PlayerInfo(id, "alpha", false, 1, 2, 3, true, false, 149);
        PlayerInfo current = new PlayerInfo(id, "alpha", true, 2, 3, 4, false, true, 150);
        int mask = (int) changedMask.invoke(null, previous, current);
        int allExpected = RoomRosterDelta.CHANGE_READY
                | RoomRosterDelta.CHANGE_STATS
                | RoomRosterDelta.CHANGE_LIFE
                | RoomRosterDelta.CHANGE_INVINCIBLE
                | RoomRosterDelta.CHANGE_PING_BUCKET
                | RoomRosterDelta.CHANGE_STREAK;
        requireEquals(allExpected, mask, "roster delta preserves every changed-field bit");

        requireEquals(0, pingBucket.invoke(null, 0), "ping bucket below 150 ms");
        requireEquals(1, pingBucket.invoke(null, 150), "ping bucket begins at 150 ms");
        requireEquals(2, pingBucket.invoke(null, 300), "ping bucket begins at 300 ms");
        requireEquals(3, pingBucket.invoke(null, 600), "ping bucket begins at 600 ms");
        requireEquals(4, pingBucket.invoke(null, 1000), "ping bucket begins at 1000 ms");
        requireEquals(5, pingBucket.invoke(null, -1), "negative ping keeps the unknown bucket");
    }

    private static void requireTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void requireFalse(boolean value, String message) {
        requireTrue(!value, message);
    }

    private static void requireEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
