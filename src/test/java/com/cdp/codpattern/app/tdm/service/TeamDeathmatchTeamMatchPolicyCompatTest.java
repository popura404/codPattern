package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.teammatch.TeamMatchPolicy;
import com.cdp.codpattern.app.tdm.model.TdmTeamMatchPolicies;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.data.SpawnSelectionReason;

import java.util.List;

public final class TeamDeathmatchTeamMatchPolicyCompatTest {
    private TeamDeathmatchTeamMatchPolicyCompatTest() {
    }

    public static void main(String[] args) {
        TeamMatchPolicy policy = TdmTeamMatchPolicies.teamDeathmatch();

        requireEquals(BuiltInGameModes.TEAM_DEATHMATCH, policy.gameType(),
                "Team Deathmatch canonical game type");
        requireEquals(List.of(BuiltInGameModes.LEGACY_CDP_TACTICAL_TDM), policy.aliases(),
                "Team Deathmatch legacy alias");
        requireTrue(policy.dynamicRespawnEnabled(),
                "Team Deathmatch must retain dynamic respawn");
        requireTrue(policy.tacticalCompatibilityPorts(),
                "Team Deathmatch must retain tactical compatibility port types");
        requireEquals(List.of(SpawnPointKind.INITIAL),
                policy.spawnSelectionOrder(SpawnSelectionReason.ROUND_START),
                "Team Deathmatch round-start spawn order");
        requireEquals(List.of(SpawnPointKind.DYNAMIC_CANDIDATE, SpawnPointKind.INITIAL),
                policy.spawnSelectionOrder(SpawnSelectionReason.RESPAWN),
                "Team Deathmatch respawn order");
        requireEquals(List.of(SpawnPointKind.DYNAMIC_CANDIDATE, SpawnPointKind.INITIAL),
                policy.spawnSelectionOrder(SpawnSelectionReason.MID_MATCH_JOIN),
                "Team Deathmatch mid-match join spawn order");
        requireEquals(BuiltInGameModes.TEAM_DEATHMATCH, policy.runtimeProvider().gameType(),
                "Team Deathmatch runtime provider identity");
        requireEquals(BuiltInGameModes.TEAM_DEATHMATCH, policy.persistenceProvider().gameType(),
                "Team Deathmatch persistence provider identity");
        requireTrue(policy.editorSchema().supportsPointLayer(SpawnPointKind.DYNAMIC_CANDIDATE.serializedName()),
                "Team Deathmatch editor must retain dynamic respawn candidates");
        requireEquals("teamdeathmatch", policy.clientPresentation().overlayStyle(),
                "Team Deathmatch presentation style");
        requireEquals("teamdeathmatch", policy.recordModeLabel(),
                "Team Deathmatch record label");

        System.out.println("PASS Team Deathmatch team-match policy compat");
    }

    private static void requireTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void requireEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
