package com.cdp.codpattern.client.zombies;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffType;
import com.cdp.codpattern.app.zombies.sync.ZombiesRuntimeStateKeys;
import com.cdp.codpattern.client.ClientModeRuntimeState;

import java.util.List;
import java.util.Map;

public final class ClientZombiesStateGrowthCompatTest {
    private ClientZombiesStateGrowthCompatTest() {
    }

    public static void main(String[] args) {
        missingGrowthKeysUseDefaults();
        growthKeysReadFromPlayerValues();
    }

    private static void missingGrowthKeysUseDefaults() {
        ClientModeRuntimeState.clearAll();
        ClientModeRuntimeState.update(snapshot(Map.of()));

        require(!ClientZombiesState.powerEnabled(), "missing power key should default false");
        require(ClientZombiesState.armorLevel() == 0, "missing armor key should default 0");
        require(ClientZombiesState.primaryUpgradeLevel() == 0, "missing upgrade key should default 0");
        require(!ClientZombiesState.buffEnabled(ZombiesBuffType.DOUBLE_HEALTH.id()), "missing buff key should default false");
        require(ClientZombiesState.ownedBuffIds().isEmpty(), "missing buff keys should expose no owned buffs");
    }

    private static void growthKeysReadFromPlayerValues() {
        ClientModeRuntimeState.clearAll();
        ClientModeRuntimeState.update(snapshot(Map.of(
                ZombiesRuntimeStateKeys.PLAYER_POWER_ENABLED, ModePlayerValue.ofBoolean(true),
                ZombiesRuntimeStateKeys.PLAYER_ARMOR_LEVEL, ModePlayerValue.ofInt(2),
                ZombiesRuntimeStateKeys.PLAYER_WEAPON_PRIMARY_UPGRADE, ModePlayerValue.ofInt(3),
                ZombiesRuntimeStateKeys.playerBuff(ZombiesBuffType.DOUBLE_HEALTH.id()), ModePlayerValue.ofBoolean(true),
                ZombiesRuntimeStateKeys.playerBuff(ZombiesBuffType.SCORE_MULTIPLIER.id()), ModePlayerValue.ofBoolean(true),
                ZombiesRuntimeStateKeys.playerBuff(ZombiesBuffType.DOUBLE_AMMO.id()), ModePlayerValue.ofBoolean(false)
        )));

        require(ClientZombiesState.powerEnabled(), "power key should read true");
        require(ClientZombiesState.armorLevel() == 2, "armor key should read level");
        require(ClientZombiesState.primaryUpgradeLevel() == 3, "upgrade key should read level");
        require(ClientZombiesState.buffEnabled(ZombiesBuffType.DOUBLE_HEALTH.id()), "enabled buff should read true");
        require(!ClientZombiesState.buffEnabled(ZombiesBuffType.DOUBLE_AMMO.id()), "disabled buff should read false");
        require(ClientZombiesState.ownedBuffIds().equals(List.of(
                ZombiesBuffType.DOUBLE_HEALTH.id(),
                ZombiesBuffType.SCORE_MULTIPLIER.id()
        )), "owned buff ids should include enabled buffs in stable order");
    }

    private static ModeRuntimeStateSnapshot snapshot(Map<String, ModePlayerValue> values) {
        return new ModeRuntimeStateSnapshot(
                RoomId.of(BuiltInGameModes.ZOMBIES, "growth").encode(),
                "WAVE_ACTIVE",
                0,
                List.of(),
                values,
                List.of(),
                1L);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
