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
        players.getOrCreate(playerId).setPoints(1500.0D);
        players.getOrCreate(playerId).setArmor(new ZombiesArmorState(3, 0.65D));

        Map<String, ModePlayerValue> values = players.survivorValues();
        requireValue(values, ZombiesRuntimeStateKeys.survivorLifeState(playerId.toString()), "ALIVE");
        requireValue(values, ZombiesRuntimeStateKeys.survivorConnectionState(playerId.toString()), "ONLINE");
        requireValue(values, ZombiesRuntimeStateKeys.survivorPoints(playerId.toString()), "1500");
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
