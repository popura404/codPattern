package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.teammatch.TeamMatchPolicy;
import com.cdp.codpattern.app.tdm.model.TdmTeamMatchPolicies;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.data.SpawnSelectionReason;

import java.util.List;

public final class FrontlineTeamMatchPolicyCompatTest {
    private FrontlineTeamMatchPolicyCompatTest() {
    }

    public static void main(String[] args) {
        TeamMatchPolicy policy = TdmTeamMatchPolicies.frontline();

        requireEquals(BuiltInGameModes.FRONTLINE, policy.gameType(),
                "Frontline canonical game type");
        requireEquals(List.of(BuiltInGameModes.LEGACY_CDP_TDM), policy.aliases(),
                "Frontline legacy alias");
        requireFalse(policy.dynamicRespawnEnabled(),
                "Frontline must not opt into dynamic respawn");
        requireFalse(policy.tacticalCompatibilityPorts(),
                "Frontline must retain base PVP port types");
        requireEquals(List.of(SpawnPointKind.INITIAL),
                policy.spawnSelectionOrder(SpawnSelectionReason.ROUND_START),
                "Frontline round-start spawn order");
        requireEquals(List.of(SpawnPointKind.INITIAL),
                policy.spawnSelectionOrder(SpawnSelectionReason.RESPAWN),
                "Frontline respawn order");
        requireEquals(List.of(SpawnPointKind.INITIAL),
                policy.spawnSelectionOrder(SpawnSelectionReason.MID_MATCH_JOIN),
                "Frontline mid-match join spawn order");
        requireEquals(BuiltInGameModes.FRONTLINE, policy.runtimeProvider().gameType(),
                "Frontline runtime provider identity");
        requireEquals(BuiltInGameModes.FRONTLINE, policy.persistenceProvider().gameType(),
                "Frontline persistence provider identity");
        requireFalse(policy.editorSchema().supportsPointLayer(SpawnPointKind.DYNAMIC_CANDIDATE.serializedName()),
                "Frontline editor must not expose dynamic respawn candidates");
        requireEquals("frontline", policy.clientPresentation().overlayStyle(),
                "Frontline presentation style");
        requireEquals("frontline", policy.recordModeLabel(),
                "Frontline record label");

        System.out.println("PASS Frontline team-match policy compat");
    }

    private static void requireFalse(boolean value, String message) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private static void requireEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
