package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.model.ZombiesEquipmentSlot;
import com.cdp.codpattern.app.zombies.model.ZombiesEquipmentSlotSnapshot;
import com.cdp.codpattern.app.zombies.model.ZombiesEquipmentSnapshot;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ZombiesReviveLoadoutService {
    private static final ZombiesErrorCode EQUIPMENT_INVALID_PLAYER = ZombiesErrorCode.of("equipment.invalid_player");
    private static final ZombiesErrorCode EQUIPMENT_ROOM_MISMATCH = ZombiesErrorCode.of("equipment.room_mismatch");

    private final ZombiesWeaponItemStackService weaponItemStackService;

    public ZombiesReviveLoadoutService() {
        this(new ZombiesWeaponItemStackService());
    }

    public ZombiesReviveLoadoutService(ZombiesWeaponItemStackService weaponItemStackService) {
        this.weaponItemStackService = Objects.requireNonNull(weaponItemStackService, "weaponItemStackService");
    }

    public ZombiesServiceResult<ReviveLoadoutRestoreResult> restoreDeathSnapshot(
            RoomId roomId,
            ServerPlayer player,
            ZombiesPlayerRuntimeState playerState
    ) {
        if (player == null || playerState == null) {
            return ZombiesServiceResult.failure(EQUIPMENT_INVALID_PLAYER, playerParams(playerState), "");
        }
        ZombiesEquipmentSnapshot snapshot = playerState.deathEquipmentSnapshot().orElse(null);
        if (snapshot == null || snapshot.isEmpty()) {
            return ZombiesServiceResult.success(ReviveLoadoutRestoreResult.empty());
        }
        return restoreSnapshot(roomId, player, playerState, snapshot);
    }

    public ZombiesServiceResult<ReviveLoadoutRestoreResult> restoreSnapshot(
            RoomId roomId,
            ServerPlayer player,
            ZombiesPlayerRuntimeState playerState,
            ZombiesEquipmentSnapshot snapshot
    ) {
        if (player == null || playerState == null || snapshot == null) {
            return ZombiesServiceResult.failure(EQUIPMENT_INVALID_PLAYER, playerParams(playerState), "");
        }
        if (!matchesRoom(roomId, snapshot.roomId())) {
            return ZombiesServiceResult.failure(
                    EQUIPMENT_ROOM_MISMATCH,
                    roomParams(roomId, snapshot.roomId()),
                    "");
        }

        Inventory inventory = player.getInventory();
        removeCurrentZombiesWeapons(roomId, inventory);
        int restored = 0;
        int fallbackSlots = 0;
        for (ZombiesEquipmentSlotSnapshot slotSnapshot : snapshot.slots()) {
            ZombiesWeaponInstanceState weaponState = slotSnapshot.weaponState();
            ItemStack stack = slotSnapshot.itemStack();
            String instanceId = weaponItemStackService.readWeaponTags(stack)
                    .value()
                    .map(ZombiesWeaponItemStackService.ZombiesWeaponTagData::instanceId)
                    .orElse("");
            ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> tagResult =
                    weaponItemStackService.writeWeaponTags(
                            stack,
                            roomId,
                            instanceId,
                            slotSnapshot.slot(),
                            weaponState);
            if (!tagResult.success()) {
                continue;
            }
            boolean usedFallback = !setInventoryItem(inventory, slotSnapshot.inventorySlot(), stack);
            if (usedFallback) {
                fallbackSlots++;
            }
            applyRuntimeWeaponState(playerState, slotSnapshot.slot(), weaponState);
            restored++;
        }
        if (restored > 0) {
            playerState.clearDeathEquipmentSnapshot();
        }
        syncInventory(player);
        return ZombiesServiceResult.success(new ReviveLoadoutRestoreResult(restored, fallbackSlots));
    }

    public void syncRuntimeStateFromSnapshot(
            ZombiesPlayerRuntimeState playerState,
            ZombiesEquipmentSnapshot snapshot
    ) {
        if (playerState == null || snapshot == null) {
            return;
        }
        for (ZombiesEquipmentSlotSnapshot slotSnapshot : snapshot.slots()) {
            applyRuntimeWeaponState(playerState, slotSnapshot.slot(), slotSnapshot.weaponState());
        }
    }

    private void removeCurrentZombiesWeapons(RoomId roomId, Inventory inventory) {
        if (inventory == null) {
            return;
        }
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            ItemStack stack = inventory.getItem(index);
            if (weaponItemStackService.belongsToRoom(stack, roomId)) {
                inventory.setItem(index, ItemStack.EMPTY);
            }
        }
    }

    private static boolean setInventoryItem(Inventory inventory, int inventorySlot, ItemStack stack) {
        if (inventory == null || stack == null || stack.isEmpty()) {
            return false;
        }
        if (inventorySlot >= 0 && inventorySlot < inventory.getContainerSize()) {
            inventory.setItem(inventorySlot, stack);
            return true;
        }
        return inventory.add(stack);
    }

    private static void applyRuntimeWeaponState(
            ZombiesPlayerRuntimeState playerState,
            ZombiesEquipmentSlot slot,
            ZombiesWeaponInstanceState weaponState
    ) {
        if (playerState == null || slot == null || weaponState == null) {
            return;
        }
        if (slot == ZombiesEquipmentSlot.STARTER) {
            playerState.setStarterWeapon(weaponState);
        } else if (slot == ZombiesEquipmentSlot.PRIMARY) {
            playerState.setPrimaryWeapon(weaponState);
        }
    }

    private static void syncInventory(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
        ThrowableInventoryService.sync(player);
    }

    private static boolean matchesRoom(RoomId roomId, String snapshotRoomId) {
        String expectedRoomId = encodeRoomId(roomId);
        String actualRoomId = Objects.requireNonNullElse(snapshotRoomId, "").trim();
        return expectedRoomId.isBlank() || actualRoomId.isBlank() || expectedRoomId.equals(actualRoomId);
    }

    private static String encodeRoomId(RoomId roomId) {
        return roomId == null ? "" : roomId.encode();
    }

    private static Map<String, ModePlayerValue> playerParams(ZombiesPlayerRuntimeState playerState) {
        Map<String, ModePlayerValue> params = new LinkedHashMap<>();
        params.put("playerId", ModePlayerValue.ofString(playerState == null ? "" : playerState.playerId().toString()));
        return params;
    }

    private static Map<String, ModePlayerValue> roomParams(RoomId roomId, String snapshotRoomId) {
        Map<String, ModePlayerValue> params = new LinkedHashMap<>();
        params.put("roomId", ModePlayerValue.ofString(encodeRoomId(roomId)));
        params.put("snapshotRoomId", ModePlayerValue.ofString(snapshotRoomId));
        return params;
    }

    public record ReviveLoadoutRestoreResult(
            int restoredSlots,
            int fallbackSlots
    ) {
        public ReviveLoadoutRestoreResult {
            restoredSlots = Math.max(0, restoredSlots);
            fallbackSlots = Math.max(0, fallbackSlots);
        }

        public static ReviveLoadoutRestoreResult empty() {
            return new ReviveLoadoutRestoreResult(0, 0);
        }
    }
}
