package com.cdp.codpattern.client.state;

import com.cdp.codpattern.app.tdm.service.TdmSettlementTiming;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.verification.phase7.MainOnlyVerificationBootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TdmSettlementStateCompatTest {
    public static void main(String[] args) throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Field bootstrapFlag = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapFlag.setAccessible(true);
        bootstrapFlag.setBoolean(null, true);
        if (BuiltInRegistries.REGISTRY.keySet().isEmpty()) throw new AssertionError("missing built-in registries");
        MainOnlyVerificationBootstrap.install();
        long start = 8_000_000_000L;
        List<PlayerInfo> team = new ArrayList<>(List.of(
                new PlayerInfo(UUID.randomUUID(), "Player", true, 10, 3, 5, true, false, 30)));
        TdmSettlementState state = new TdmSettlementState(75, 67, Map.of("kortac", team), TdmSettlementTiming.TOTAL_TICKS, start);
        team.clear();
        if (state.rosters().get("kortac").size() != 1) throw new AssertionError("scoreboard changed after final kill");
        check(.4002, state.elapsedSeconds(start + 400_200_000L, false));
        check(.4008, state.elapsedSeconds(start + 400_800_000L, true));
        check(.4008, state.elapsedSeconds(start + 1_400_800_000L, true));
        check(.4008, state.elapsedSeconds(start + 1_400_800_000L, false));
        check(.4013, state.elapsedSeconds(start + 1_401_300_000L, false));
        check(12.5, state.elapsedSeconds(start + 40_000_000_000L, false));
        TdmSettlementState rejoined = new TdmSettlementState(75, 67, Map.of(), 120, start);
        check(6.5, rejoined.elapsedSeconds(start, false));
        verifyPacketLifecycle();
        System.out.println("PASS TDM settlement clock and roster");
    }

    private static void verifyPacketLifecycle() {
        ClientMatchStateStore store = new ClientMatchStateStore();
        PlayerInfo finalKill = new PlayerInfo(UUID.randomUUID(), "Player", false, 10, 2, 5, true, false, 20);
        store.updateTeamPlayers("frontline|first", 5, Map.of("kortac", List.of(finalKill)));
        store.updateScore(Map.of("kortac", 75, "specgru", 67), 0, 0, 1234);
        store.updatePhase("ENDED", 250);
        TdmSettlementState first = store.settlement();
        store.updatePhase("ENDED", 120);
        if (first != store.settlement()) throw new AssertionError("duplicate ENDED restarted the clock");
        store.updateTeamPlayers("frontline|first", 6, Map.of());
        store.updateScore(0, 0, 0);
        if (first.kortacScore() != 75 || first.specgruScore() != 67
                || !first.rosters().get("kortac").equals(List.of(finalKill))
                || !"kortac".equals(first.teamOf(finalKill.uuid())) || !first.teamOf(null).isEmpty()) {
            throw new AssertionError("live room reset overwrote final scores");
        }
        store.updateTeamPlayers("teamdeathmatch|second", 1, Map.of());
        if (store.settlement() != null) throw new AssertionError("switched rooms with an old result");
        store.updateScore(31, 29, 500);
        store.updatePhase("ENDED", 110);
        if (store.settlement() == first || store.settlement().kortacScore() != 31) {
            throw new AssertionError("new room did not start its own presentation");
        }
        store.clearRoomContext();
        if (store.settlement() != null || !"WAITING".equals(store.currentPhase())) {
            throw new AssertionError("leaving room kept result active");
        }
        store.updatePhase("ENDED", 100);
        if (store.settlement() != null) throw new AssertionError("unknown room started presentation");
        store.updateTeamPlayers("frontline|third", 1, Map.of());
        store.updateScore(50, 49, 400);
        store.updatePhase("ENDED", 98);
        if (store.settlement() == null || store.settlement().specgruScore() != 49) {
            throw new AssertionError("late room identity lost rejoin presentation");
        }
        store.updatePhase("WAITING", 0);
        if (store.settlement() != null) throw new AssertionError("force-end/reset left presentation active");
        store.setRoomContext("external|first");
        store.updatePhase("ENDED", 150);
        if (store.settlement() != null) throw new AssertionError("external mode used built-in settlement");
        store.clearRoomContext();
        if (!"ENDED".equals(store.currentPhase())) throw new AssertionError("external room reset its own phase");
    }

    private static void check(double expected, double actual) {
        if (Math.abs(expected - actual) > 1.0e-10) throw new AssertionError(expected + " != " + actual);
    }
}
