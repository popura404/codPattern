package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.model.ZombiesEquipmentSlot;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ZombiesWeaponInventoryServiceCompatTest {
    private ZombiesWeaponInventoryServiceCompatTest() {
    }

    public static void main(String[] args) {
        if (!bootstrapMinecraft()) {
            return;
        }
        purchasedPrimaryStackGetsZombiesTags();
        reserveAmmoSyncUpdatesTaggedWeaponItem();
        reserveAmmoSyncFailureLeavesItemUntouched();
    }

    private static boolean bootstrapMinecraft() {
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            return true;
        } catch (Throwable throwable) {
            System.err.println("Skipping ZombiesWeaponInventoryServiceCompatTest outside a bootstrapped Minecraft runtime: "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            return false;
        }
    }

    private static void purchasedPrimaryStackGetsZombiesTags() {
        ZombiesWeaponItemStackService itemStackService = new ZombiesWeaponItemStackService();
        ZombiesWeaponInventoryService inventoryService = new ZombiesWeaponInventoryService(
                itemStackService,
                ignoredGunId -> new ItemStack(Items.CROSSBOW));
        RoomId roomId = roomId();
        ZombiesWeaponInstanceState weapon = ZombiesWeaponInstanceState.primary(
                "tacz:m4a1",
                2,
                1.25D,
                210);

        ZombiesServiceResult<ZombiesWeaponInventoryService.PreparedWeaponStack> prepared =
                inventoryService.preparePurchasedPrimaryWeapon(roomId, weapon);

        requireSuccess(prepared, "primary purchase stack should prepare");
        ItemStack stack = prepared.value().orElseThrow().itemStack();
        ZombiesWeaponItemStackService.ZombiesWeaponTagData tag = itemStackService.readWeaponTags(stack)
                .value()
                .orElseThrow();
        require(roomId.encode().equals(tag.roomId()), "prepared stack should be tagged for the room");
        require(!tag.instanceId().isBlank(), "prepared stack should have a unique instance id");
        require(tag.slot() == ZombiesEquipmentSlot.PRIMARY, "prepared stack should be tagged as primary");
        require("tacz:m4a1".equals(tag.gunId()), "prepared stack should preserve gun id");
        require(tag.weaponLevel() == 2, "prepared stack should preserve weapon level");
        require(tag.reserveAmmo() == 210, "prepared stack should start with full reserve ammo");
        require(tag.maxReserveAmmo() == 210, "prepared stack should preserve max reserve ammo");
    }

    private static void reserveAmmoSyncUpdatesTaggedWeaponItem() {
        ZombiesWeaponItemStackService itemStackService = new ZombiesWeaponItemStackService();
        ZombiesWeaponInventoryService inventoryService = new ZombiesWeaponInventoryService(
                itemStackService,
                ignoredGunId -> new ItemStack(Items.CROSSBOW));
        RoomId roomId = roomId();
        ItemStack stack = new ItemStack(Items.CROSSBOW);
        ZombiesWeaponInstanceState depletedWeapon = new ZombiesWeaponInstanceState(
                "tacz:m4a1",
                2,
                0,
                1.25D,
                12,
                210);
        requireSuccess(
                itemStackService.writeWeaponTags(
                        stack,
                        roomId,
                        "primary-instance",
                        ZombiesEquipmentSlot.PRIMARY,
                        depletedWeapon),
                "setup primary tag should succeed");

        ZombiesServiceResult<ZombiesWeaponInventoryService.InventoryMutationResult> sync =
                inventoryService.syncReserveAmmo(
                        stack,
                        roomId,
                        ZombiesEquipmentSlot.PRIMARY,
                        depletedWeapon.refillReserveAmmo());

        requireSuccess(sync, "reserve ammo sync should succeed");
        ZombiesWeaponItemStackService.ZombiesWeaponTagData tag = itemStackService.readWeaponTags(stack)
                .value()
                .orElseThrow();
        require("primary-instance".equals(tag.instanceId()), "sync should preserve weapon instance id");
        require(tag.reserveAmmo() == 210, "sync should write refilled reserve ammo");
        require(tag.maxReserveAmmo() == 210, "sync should keep max reserve ammo");
    }

    private static void reserveAmmoSyncFailureLeavesItemUntouched() {
        ZombiesWeaponItemStackService itemStackService = new ZombiesWeaponItemStackService();
        ZombiesWeaponInventoryService inventoryService = new ZombiesWeaponInventoryService(
                itemStackService,
                ignoredGunId -> new ItemStack(Items.CROSSBOW));
        RoomId roomId = roomId();
        ItemStack stack = new ItemStack(Items.CROSSBOW);
        ZombiesWeaponInstanceState starterWeapon = new ZombiesWeaponInstanceState(
                "tacz:glock_17",
                0,
                0,
                1.0D,
                7,
                84);
        requireSuccess(
                itemStackService.writeWeaponTags(
                        stack,
                        roomId,
                        "starter-instance",
                        ZombiesEquipmentSlot.STARTER,
                        starterWeapon),
                "setup starter tag should succeed");

        ZombiesServiceResult<ZombiesWeaponInventoryService.InventoryMutationResult> sync =
                inventoryService.syncReserveAmmo(
                        stack,
                        roomId,
                        ZombiesEquipmentSlot.PRIMARY,
                        ZombiesWeaponInstanceState.primary("tacz:m4a1", 2, 1.25D, 210));

        requireFailure(sync, ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                "syncing the wrong slot should fail");
        ZombiesWeaponItemStackService.ZombiesWeaponTagData tag = itemStackService.readWeaponTags(stack)
                .value()
                .orElseThrow();
        require("starter-instance".equals(tag.instanceId()), "failed sync should keep instance id");
        require(tag.slot() == ZombiesEquipmentSlot.STARTER, "failed sync should keep starter slot");
        require(tag.reserveAmmo() == 7, "failed sync should not change reserve ammo");
        require(tag.maxReserveAmmo() == 84, "failed sync should not change max reserve ammo");
    }

    private static RoomId roomId() {
        return RoomId.of(BuiltInGameModes.ZOMBIES, "inventory-service");
    }

    private static void requireSuccess(ZombiesServiceResult<?> result, String message) {
        require(result.success(), message + ": " + result.code());
    }

    private static void requireFailure(ZombiesServiceResult<?> result, ZombiesErrorCode expectedCode, String message) {
        require(!result.success(), message + ": expected failure");
        require(expectedCode.equals(result.code()),
                message + ": expected " + expectedCode + " but was " + result.code());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
