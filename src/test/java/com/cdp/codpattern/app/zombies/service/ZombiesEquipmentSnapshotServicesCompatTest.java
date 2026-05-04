package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.model.ZombiesEquipmentSlot;
import com.cdp.codpattern.app.zombies.model.ZombiesEquipmentSlotSnapshot;
import com.cdp.codpattern.app.zombies.model.ZombiesEquipmentSnapshot;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.UUID;

public final class ZombiesEquipmentSnapshotServicesCompatTest {
    private ZombiesEquipmentSnapshotServicesCompatTest() {
    }

    public static void main(String[] args) {
        weaponTagsRoundTripLayeredState();
        weaponDamageMultiplierRequiresSameRoom();
        deathSnapshotCanSyncStarterAndPrimaryRuntimeState();
    }

    private static void weaponTagsRoundTripLayeredState() {
        ZombiesWeaponItemStackService service = new ZombiesWeaponItemStackService();
        ZombiesWeaponInstanceState state = new ZombiesWeaponInstanceState(
                "tacz:m4a1",
                3,
                2,
                1.40D,
                2.25D,
                77,
                140);
        ZombiesWeaponItemStackService.ZombiesWeaponTagData data =
                ZombiesWeaponItemStackService.ZombiesWeaponTagData.fromState(
                        "zombies|test-map",
                        "instance-1",
                        ZombiesEquipmentSlot.PRIMARY,
                        state);
        CompoundTag tag = new CompoundTag();

        service.writeTag(tag, data);
        ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> read = service.readWeaponTags(tag);

        requireSuccess(read, "tag read should succeed");
        ZombiesWeaponItemStackService.ZombiesWeaponTagData restored = read.value().orElseThrow();
        require(restored.roomId().equals("zombies|test-map"), "room id should round-trip");
        require(restored.instanceId().equals("instance-1"), "instance id should round-trip");
        require(restored.slot() == ZombiesEquipmentSlot.PRIMARY, "slot should round-trip");
        require(restored.weaponLevel() == 3, "weapon level should round-trip");
        requireClose(restored.levelDamageMultiplier(), 1.40D, "level multiplier should round-trip");
        require(restored.upgradeLevel() == 2, "upgrade level should round-trip");
        requireClose(restored.upgradeDamageMultiplier(), 2.25D, "upgrade multiplier should round-trip");
        require(restored.reserveAmmo() == 77, "reserve ammo should round-trip");
        require(restored.maxReserveAmmo() == 140, "max reserve ammo should round-trip");
        requireClose(restored.toWeaponState().finalDamageMultiplier(), 3.15D,
                "restored state should expose final damage multiplier");
    }

    private static void weaponDamageMultiplierRequiresSameRoom() {
        ZombiesWeaponItemStackService service = new ZombiesWeaponItemStackService();
        RoomId roomId = RoomId.of("zombies", "test-map");
        ZombiesWeaponInstanceState state = new ZombiesWeaponInstanceState(
                "tacz:m4a1",
                2,
                1,
                1.50D,
                2.0D,
                30,
                120);
        ZombiesWeaponItemStackService.ZombiesWeaponTagData data =
                ZombiesWeaponItemStackService.ZombiesWeaponTagData.fromState(
                        roomId.encode(),
                        "instance-2",
                        ZombiesEquipmentSlot.PRIMARY,
                        state);
        CompoundTag tag = new CompoundTag();

        service.writeTag(tag, data);

        requireClose(service.sameRoomDamageMultiplier(tag, roomId), 3.0D,
                "same-room weapon tag should expose level * upgrade multiplier");
        requireClose(service.sameRoomDamageMultiplier(tag, RoomId.of("zombies", "other-map")), 1.0D,
                "other-room weapon tag should not affect damage");
        requireClose(service.sameRoomDamageMultiplier(new CompoundTag(), roomId), 1.0D,
                "missing weapon tag should not affect damage");
    }

    private static void deathSnapshotCanSyncStarterAndPrimaryRuntimeState() {
        UUID playerId = new UUID(0L, 42L);
        ZombiesPlayerRuntimeState runtimeState = new ZombiesPlayerRuntimeState(playerId);
        ZombiesWeaponInstanceState starter = new ZombiesWeaponInstanceState(
                "tacz:glock_17",
                0,
                1,
                1.0D,
                1.50D,
                21,
                119);
        ZombiesWeaponInstanceState primary = new ZombiesWeaponInstanceState(
                "tacz:m4a1",
                2,
                2,
                1.25D,
                2.0D,
                88,
                210);
        ZombiesEquipmentSnapshot snapshot = new ZombiesEquipmentSnapshot(
                "zombies|test-map",
                playerId,
                List.of(
                        new ZombiesEquipmentSlotSnapshot(0, ZombiesEquipmentSlot.STARTER, null, starter),
                        new ZombiesEquipmentSlotSnapshot(5, ZombiesEquipmentSlot.PRIMARY, null, primary)));

        runtimeState.setDeathEquipmentSnapshot(snapshot);
        new ZombiesReviveLoadoutService().syncRuntimeStateFromSnapshot(runtimeState, snapshot);

        require(runtimeState.deathEquipmentSnapshot().isPresent(), "sync-only path should not clear death snapshot");
        ZombiesWeaponInstanceState syncedStarter = runtimeState.starterWeapon().orElseThrow();
        ZombiesWeaponInstanceState syncedPrimary = runtimeState.primaryWeapon().orElseThrow();
        require(syncedStarter.gunId().equals("tacz:glock_17"), "starter gun id should sync");
        require(syncedStarter.weaponLevel() == 0, "starter weapon level should sync");
        require(syncedStarter.upgradeLevel() == 1, "starter upgrade level should sync");
        require(syncedStarter.reserveAmmo() == 21, "starter reserve should sync");
        require(syncedPrimary.gunId().equals("tacz:m4a1"), "primary gun id should sync");
        require(syncedPrimary.weaponLevel() == 2, "primary weapon level should sync");
        require(syncedPrimary.upgradeLevel() == 2, "primary upgrade level should sync");
        requireClose(syncedPrimary.levelDamageMultiplier(), 1.25D, "primary level multiplier should sync");
        requireClose(syncedPrimary.upgradeDamageMultiplier(), 2.0D, "primary upgrade multiplier should sync");
        require(syncedPrimary.reserveAmmo() == 88, "primary reserve should sync");
    }

    private static void requireSuccess(ZombiesServiceResult<?> result, String message) {
        require(result.success(), message + ": " + result.code());
    }

    private static void requireClose(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 0.000001D,
                message + ": expected " + expected + " but was " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
