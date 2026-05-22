package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.zombies.model.ZombiesArmorState;
import com.cdp.codpattern.app.zombies.sync.ZombiesRuntimeStateKeys;

import java.util.Map;
import java.util.UUID;

public final class ZombiesSurvivorRuntimeStateSyncCompatTest {
    private ZombiesSurvivorRuntimeStateSyncCompatTest() {
    }

    public static void main(String[] args) {
        survivorValuesIncludeRoomTeammateHudFields();
    }

    private static void survivorValuesIncludeRoomTeammateHudFields() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        players.recordPlayerName(playerId, "sync-player");
        players.getOrCreate(playerId).addPoints(1500.0D);
        players.getOrCreate(playerId).addKill();
        players.getOrCreate(playerId).addBarrierOpened();
        players.getOrCreate(playerId).markDeadSpectating();
        players.getOrCreate(playerId).setArmor(new ZombiesArmorState(3, 0.65D));

        Map<String, ModePlayerValue> values = players.survivorValues();
        requireValue(values, ZombiesRuntimeStateKeys.survivorName(playerId.toString()), "sync-player");
        requireValue(values, ZombiesRuntimeStateKeys.survivorLifeState(playerId.toString()), "DEAD_SPECTATING");
        requireValue(values, ZombiesRuntimeStateKeys.survivorConnectionState(playerId.toString()), "ONLINE");
        requireValue(values, ZombiesRuntimeStateKeys.survivorPoints(playerId.toString()), "1500");
        requireValue(values, ZombiesRuntimeStateKeys.survivorTotalEarnedPoints(playerId.toString()), "1500");
        requireValue(values, ZombiesRuntimeStateKeys.survivorKills(playerId.toString()), "1");
        requireValue(values, ZombiesRuntimeStateKeys.survivorDeaths(playerId.toString()), "1");
        requireValue(values, ZombiesRuntimeStateKeys.survivorBarriersOpened(playerId.toString()), "1");
        requireValue(values, ZombiesRuntimeStateKeys.survivorArmorLevel(playerId.toString()), "3");

        System.out.println("PASS zombies survivor runtime state sync compat");
    }

    private static void requireValue(Map<String, ModePlayerValue> values, String key, String expected) {
        ModePlayerValue value = values.get(key);
        if (value == null) {
            throw new AssertionError("missing survivor runtime value `" + key + "`");
        }
        if (!expected.equals(value.value())) {
            throw new AssertionError("survivor runtime value `" + key + "` expected `" + expected
                    + "` but was `" + value.value() + "`");
        }
    }
}
