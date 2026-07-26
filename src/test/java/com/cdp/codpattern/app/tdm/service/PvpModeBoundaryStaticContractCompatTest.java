package com.cdp.codpattern.app.tdm.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PvpModeBoundaryStaticContractCompatTest {
    private static final Path TDM_MAP = Path.of(
            "src/main/java/com/cdp/codpattern/compat/fpsmatch/map/CodTdmMap.java");
    private static final Path TACTICAL_MAP = Path.of(
            "src/main/java/com/cdp/codpattern/compat/fpsmatch/map/CodTacticalTdmMap.java");
    private static final Path TEAM_MATCH_RUNTIME = Path.of(
            "src/main/java/com/cdp/codpattern/app/teammatch/TeamMatchRuntime.java");
    private static final Path TEAM_MATCH_POLICY = Path.of(
            "src/main/java/com/cdp/codpattern/app/teammatch/TeamMatchPolicy.java");
    private static final Path TDM_POLICIES = Path.of(
            "src/main/java/com/cdp/codpattern/app/tdm/model/TdmTeamMatchPolicies.java");
    private static final Path ACTION_PORTS = Path.of(
            "src/main/java/com/cdp/codpattern/compat/fpsmatch/map/CodTdmMapActions.java");
    private static final Path READ_PORTS = Path.of(
            "src/main/java/com/cdp/codpattern/compat/fpsmatch/map/CodTdmMapReadPortAdapter.java");
    private static final Path READY = Path.of(
            "src/main/java/com/cdp/codpattern/compat/fpsmatch/map/CodTdmVoteCoordinator.java");
    private static final Path READY_ENGINE = Path.of(
            "src/main/java/com/cdp/codpattern/app/match/runtime/ready/DefaultReadyStateService.java");
    private static final Path VOTE = Path.of(
            "src/main/java/com/cdp/codpattern/app/tdm/service/VoteService.java");
    private static final Path VOTE_ENGINE = Path.of(
            "src/main/java/com/cdp/codpattern/app/match/runtime/vote/RoomVoteEngine.java");
    private static final Path MEMBERSHIP = Path.of(
            "src/main/java/com/cdp/codpattern/compat/fpsmatch/map/CodTdmTeamMembershipCoordinator.java");
    private static final Path ROSTER = Path.of(
            "src/main/java/com/cdp/codpattern/compat/fpsmatch/map/CodTdmClientSyncCoordinator.java");
    private static final Path ROSTER_ENGINE = Path.of(
            "src/main/java/com/cdp/codpattern/app/match/runtime/roster/RoomRosterSyncCoordinator.java");
    private static final Path RESET = Path.of(
            "src/main/java/com/cdp/codpattern/compat/fpsmatch/map/CodTdmResetRuntime.java");

    private PvpModeBoundaryStaticContractCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        String tdmMap = Files.readString(TDM_MAP);
        String tacticalMap = Files.readString(TACTICAL_MAP);
        String teamMatchRuntime = Files.readString(TEAM_MATCH_RUNTIME);
        String teamMatchPolicy = Files.readString(TEAM_MATCH_POLICY);
        String tdmPolicies = Files.readString(TDM_POLICIES);
        String actionPorts = Files.readString(ACTION_PORTS);
        String readPorts = Files.readString(READ_PORTS);
        String ready = Files.readString(READY);
        String readyEngine = Files.readString(READY_ENGINE);
        String vote = Files.readString(VOTE);
        String voteEngine = Files.readString(VOTE_ENGINE);
        String membership = Files.readString(MEMBERSHIP);
        String roster = Files.readString(ROSTER);
        String rosterEngine = Files.readString(ROSTER_ENGINE);
        String reset = Files.readString(RESET);

        verifyReadyAndVoteRestrictions(ready, readyEngine, vote, voteEngine);
        verifyJoinLeaveBalanceAndSpectatorBoundaries(teamMatchRuntime, membership);
        verifyFrontlineAndTeamDeathmatchSpawnDifference(
                tdmMap,
                tacticalMap,
                teamMatchRuntime,
                teamMatchPolicy,
                tdmPolicies,
                actionPorts,
                readPorts);
        verifyRosterAndCleanupOrdering(roster, rosterEngine, reset);
        System.out.println("PASS PVP mode boundary static contract compat");
    }

    private static void verifyReadyAndVoteRestrictions(
            String ready,
            String readyEngine,
            String vote,
            String voteEngine
    ) {
        requireContains(ready,
                "phaseSupplier.get() == TdmGamePhase.WAITING",
                "ready changes remain restricted to WAITING");
        requireContains(ready,
                "&& joinedPlayerChecker.test(playerId)",
                "ready changes remain restricted to joined team players");
        requireContains(readyEngine,
                "public record OperationResult(boolean accepted, boolean changed)",
                "ready acceptance and stored-value mutation remain separate outcomes");
        requireContains(vote,
                "private static final int VOTE_TIMEOUT_TICKS = 15 * 20;",
                "PVP vote timeout remains 15 seconds");
        requireContains(vote,
                "if (!hooks.isWaitingPhase())",
                "start votes remain restricted to WAITING");
        requireContains(vote,
                "else if (!hooks.isPlayingOrWarmupPhase())",
                "end votes remain restricted to WARMUP or PLAYING");
        requireContains(vote,
                "long unreadyCount = joinedPlayers.stream()",
                "start vote still requires all current joined players ready");
        requireContains(vote,
                "if (!hooks.hasMatchEndTeleportPoint())",
                "start vote still requires a configured end teleport");
        requireContains(vote,
                "RoomVoteEngine.MemberDeparturePolicy.REMOVE_AND_RECALCULATE",
                "PVP facade should select recalculation on voter departure");
        requireContains(voteEngine,
                "session.members.remove(playerId)",
                "departing PVP player is removed from the engine voter set");
        requireContains(voteEngine,
                "boolean resolved = resolve(session) || activeVote == null;",
                "departure immediately recalculates the PVP vote");
    }

    private static void verifyJoinLeaveBalanceAndSpectatorBoundaries(String teamMatchRuntime, String membership) {
        requireContains(teamMatchRuntime,
                "if (!readPort.isWaitingPhase())",
                "PVP room join and spectator admission remain phase-locked");
        requireContains(teamMatchRuntime,
                "if (resolvedRequest.spectator())",
                "explicit spectator join remains supported");
        requireContains(teamMatchRuntime,
                "actionPort.joinSpectator(player);",
                "spectator join remains separate from team membership");
        requireContains(teamMatchRuntime,
                "readPort.chooseAutoJoinTeam(maxTeamDifference)",
                "automatic team join preserves balance policy");
        requireContains(teamMatchRuntime,
                "if (!readPort.canJoinWithBalance(targetTeam, maxTeamDifference))",
                "explicit team join preserves balance rejection");
        requireContains(teamMatchRuntime,
                "actionPort.initializeReadyState(player);",
                "team join initializes the current unready state");
        requireContains(teamMatchRuntime,
                "actionPort.leaveRoom(player);",
                "room leave stays delegated to lifecycle cleanup");
        requireContains(membership,
                "port.removePlayerFromVote(playerId);",
                "leave cleanup removes the player from the active vote");
        requireContains(membership,
                "port.clearSpectatorPreferredTeam(playerId);",
                "leave cleanup clears spectator preferred team");
        requireContains(membership,
                "if (!port.isWaitingPhase() && !port.hasJoinedPlayers())",
                "empty active PVP room still resets after leave");
    }

    private static void verifyFrontlineAndTeamDeathmatchSpawnDifference(
            String tdmMap,
            String tacticalMap,
            String teamMatchRuntime,
            String teamMatchPolicy,
            String tdmPolicies,
            String actionPorts,
            String readPorts
    ) {
        requireContains(tdmMap,
                "this(serverLevel, mapName, areaData, TdmTeamMatchPolicies.frontline());",
                "base PVP map remains bound to the Frontline policy");
        requireContains(tacticalMap,
                "super(serverLevel, mapName, areaData, TdmTeamMatchPolicies.teamDeathmatch());",
                "tactical map remains bound to the Team Deathmatch policy");
        requireContains(teamMatchRuntime,
                "for (SpawnPointKind kind : policy.spawnSelectionOrder(reason))",
                "both PVP modes should use the shared policy-driven spawn runtime");
        requireContains(teamMatchPolicy,
                "if (!dynamicRespawnEnabled || reason == SpawnSelectionReason.ROUND_START)",
                "round start and Frontline should remain initial-spawn only");
        requireContains(teamMatchPolicy,
                "return List.of(SpawnPointKind.DYNAMIC_CANDIDATE, SpawnPointKind.INITIAL);",
                "Team Deathmatch respawn should prefer dynamic candidates with initial fallback");
        requireContains(tdmPolicies,
                "BuiltInGameModes.FRONTLINE,",
                "Frontline canonical identity should remain explicit in its policy");
        requireContains(tdmPolicies,
                "BuiltInGameModes.TEAM_DEATHMATCH,",
                "Team Deathmatch canonical identity should remain explicit in its policy");
        requireNotContains(tacticalMap,
                "CodTacticalTdmPorts",
                "the tactical map should no longer install a large delegation wrapper");
        requireContains(actionPorts,
                "extends MapActionPort implements CodTacticalTdmActionPort",
                "legacy tactical action-port typing should be provided by the shared port implementation");
        requireContains(readPorts,
                "implements CodTacticalTdmReadPort",
                "legacy tactical read-port typing should be provided by the shared port implementation");
    }

    private static void verifyRosterAndCleanupOrdering(String roster, String rosterEngine, String reset) {
        requireContains(roster,
                "private static final long ROSTER_DELTA_FLUSH_MS = 150L;",
                "roster delta cadence remains frozen");
        requireContains(roster,
                "private static final long ROSTER_FULL_SNAPSHOT_MS = 7000L;",
                "roster calibration snapshot cadence remains frozen");
        requireContains(roster,
                "for (ServerPlayer player : port.getJoinedPlayers())",
                "joined players remain roster recipients");
        requireContains(roster,
                "for (ServerPlayer player : port.getSpectatorPlayers())",
                "spectators remain roster recipients");
        requireContains(roster,
                "RoomRosterSyncCoordinator.Settings.deltaEnabled(",
                "TDM should configure the generic coordinator for delta delivery");
        requireContains(rosterEngine,
                "fullSnapshotPendingForAll = true;\n        pendingDeltaUpdates.clear();\n        flushRosterSync(recipients, true);",
                "explicit resync still forces a full snapshot and discards stale deltas");
        requireContains(rosterEngine,
                "int nextVersion = advanceVersion();",
                "delta sends monotonically advance roster version");
        requireContains(rosterEngine,
                "if (fromTick || now - lastDeltaFlushAtMs >= settings.deltaFlushMs())",
                "map-tick changes should remain eligible for immediate delta flush");
        requireContains(reset,
                "removeOfflinePlayersAction.get().forEach(CodTdmDeferredLeaveRegistry::register);\n"
                        + "        matchState.resetCoreState();\n"
                        + "        playerState.clearAll();\n"
                        + "        voteRuntime.clearAll();",
                "PVP reset preserves offline removal, core reset, player clear, and vote clear ordering");
    }

    private static void requireContains(String text, String expected, String message) {
        if (!text.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }

    private static void requireNotContains(String text, String forbidden, String message) {
        if (text.contains(forbidden)) {
            throw new AssertionError(message + ": unexpected `" + forbidden + "`");
        }
    }
}
