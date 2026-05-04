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
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ZombiesEquipmentSnapshotService {
    private static final ZombiesErrorCode EQUIPMENT_INVALID_PLAYER = ZombiesErrorCode.of("equipment.invalid_player");

    private final ZombiesWeaponItemStackService weaponItemStackService;

    public ZombiesEquipmentSnapshotService() {
        this(new ZombiesWeaponItemStackService());
    }

    public ZombiesEquipmentSnapshotService(ZombiesWeaponItemStackService weaponItemStackService) {
        this.weaponItemStackService = Objects.requireNonNull(weaponItemStackService, "weaponItemStackService");
    }

    public ZombiesServiceResult<ZombiesEquipmentSnapshot> captureDeathEquipment(
            RoomId roomId,
            ServerPlayer player,
            ZombiesPlayerRuntimeState playerState
    ) {
        if (player == null || playerState == null) {
            return ZombiesServiceResult.failure(EQUIPMENT_INVALID_PLAYER, playerParams(playerState), "");
        }
        ZombiesEquipmentSnapshot snapshot = captureInventorySnapshot(roomId, player.getInventory(), playerState);
        playerState.setDeathEquipmentSnapshot(snapshot);
        removeSnapshotItems(player.getInventory(), snapshot);
        syncInventory(player);
        return ZombiesServiceResult.success(snapshot);
    }

    public ZombiesEquipmentSnapshot captureInventorySnapshot(
            RoomId roomId,
            Inventory inventory,
            ZombiesPlayerRuntimeState playerState
    ) {
        if (inventory == null || playerState == null) {
            return ZombiesEquipmentSnapshot.empty(encodeRoomId(roomId), playerState == null ? null : playerState.playerId());
        }

        Map<ZombiesEquipmentSlot, ZombiesEquipmentSlotSnapshot> snapshotsBySlot = new LinkedHashMap<>();
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            ItemStack stack = inventory.getItem(index);
            ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> tagResult =
                    weaponItemStackService.readWeaponTags(stack);
            if (!tagResult.success() || tagResult.value().isEmpty()) {
                continue;
            }

            ZombiesWeaponItemStackService.ZombiesWeaponTagData tagData = tagResult.value().get();
            if (!matchesRoom(roomId, tagData.roomId()) || snapshotsBySlot.containsKey(tagData.slot())) {
                continue;
            }

            ZombiesWeaponInstanceState weaponState = runtimeWeaponState(
                    playerState,
                    tagData.slot(),
                    tagData.toWeaponState());
            ItemStack taggedCopy = stack.copy();
            weaponItemStackService.writeWeaponTags(
                    taggedCopy,
                    roomId,
                    tagData.instanceId(),
                    tagData.slot(),
                    weaponState);
            snapshotsBySlot.put(tagData.slot(), new ZombiesEquipmentSlotSnapshot(
                    index,
                    tagData.slot(),
                    taggedCopy,
                    weaponState));
        }

        return new ZombiesEquipmentSnapshot(
                encodeRoomId(roomId),
                playerState.playerId(),
                List.copyOf(snapshotsBySlot.values()));
    }

    public void removeSnapshotItems(Inventory inventory, ZombiesEquipmentSnapshot snapshot) {
        if (inventory == null || snapshot == null || snapshot.isEmpty()) {
            return;
        }
        for (ZombiesEquipmentSlotSnapshot slotSnapshot : snapshot.slots()) {
            int inventorySlot = slotSnapshot.inventorySlot();
            if (inventorySlot >= 0 && inventorySlot < inventory.getContainerSize()) {
                inventory.setItem(inventorySlot, ItemStack.EMPTY);
            }
        }
    }

    private ZombiesWeaponInstanceState runtimeWeaponState(
            ZombiesPlayerRuntimeState playerState,
            ZombiesEquipmentSlot slot,
            ZombiesWeaponInstanceState fallback
    ) {
        if (slot == ZombiesEquipmentSlot.STARTER) {
            return playerState.starterWeapon().orElse(fallback);
        }
        if (slot == ZombiesEquipmentSlot.PRIMARY) {
            return playerState.primaryWeapon().orElse(fallback);
        }
        return fallback;
    }

    private static void syncInventory(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.inventoryMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
        ThrowableInventoryService.sync(player);
    }

    private static boolean matchesRoom(RoomId roomId, String taggedRoomId) {
        String expectedRoomId = encodeRoomId(roomId);
        return expectedRoomId.isBlank() || expectedRoomId.equals(Objects.requireNonNullElse(taggedRoomId, "").trim());
    }

    private static String encodeRoomId(RoomId roomId) {
        return roomId == null ? "" : roomId.encode();
    }

    private static Map<String, ModePlayerValue> playerParams(ZombiesPlayerRuntimeState playerState) {
        Map<String, ModePlayerValue> params = new LinkedHashMap<>();
        params.put("playerId", ModePlayerValue.ofString(playerState == null ? "" : playerState.playerId().toString()));
        return params;
    }
}
