package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.model.ZombiesEquipmentSlot;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackItemStackFactory;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ZombiesWeaponInventoryService {
    private static final String DEFAULT_TACZ_GUN_ITEM_ID = "tacz:modern_kinetic_gun";
    private static final ZombiesErrorCode WEAPON_ITEM_UNAVAILABLE = ZombiesErrorCode.of("weapon.item_unavailable");
    private static final ZombiesErrorCode WEAPON_ITEM_TAG_FAILED = ZombiesErrorCode.of("weapon.item_tag_failed");
    private static final ZombiesErrorCode WEAPON_ITEM_COMMIT_FAILED = ZombiesErrorCode.of("weapon.item_commit_failed");

    private final ZombiesWeaponItemStackService weaponItemStackService;
    private final WeaponStackFactory purchasedPrimaryWeaponFactory;

    public ZombiesWeaponInventoryService() {
        this(new ZombiesWeaponItemStackService(), ZombiesWeaponInventoryService::createDefaultTaczGunStack);
    }

    public ZombiesWeaponInventoryService(
            ZombiesWeaponItemStackService weaponItemStackService,
            WeaponStackFactory purchasedPrimaryWeaponFactory
    ) {
        this.weaponItemStackService = Objects.requireNonNull(weaponItemStackService, "weaponItemStackService");
        this.purchasedPrimaryWeaponFactory = Objects.requireNonNull(
                purchasedPrimaryWeaponFactory,
                "purchasedPrimaryWeaponFactory");
    }

    public ZombiesServiceResult<PreparedWeaponStack> preparePurchasedPrimaryWeapon(
            RoomId roomId,
            ZombiesWeaponInstanceState weaponState
    ) {
        if (weaponState == null || !ZombiesWeaponInstanceState.isValidGunId(weaponState.gunId())) {
            return ZombiesServiceResult.failure(
                    ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                    weaponParams(weaponState),
                    "");
        }
        ItemStack stack = purchasedPrimaryWeaponFactory.create(weaponState.gunId());
        if (stack == null || stack.isEmpty()) {
            return ZombiesServiceResult.failure(WEAPON_ITEM_UNAVAILABLE, weaponParams(weaponState), "");
        }
        stack = stack.copy();
        if (TaczGatewayProvider.gateway().isGun(stack)) {
            TaczGatewayProvider.gateway().configureGunAmmo(stack, 0);
        }
        ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> tagResult =
                weaponItemStackService.writeWeaponTags(
                        stack,
                        roomId,
                        ZombiesEquipmentSlot.PRIMARY,
                        weaponState);
        if (!tagResult.success() || tagResult.value().isEmpty()) {
            return ZombiesServiceResult.failure(
                    WEAPON_ITEM_TAG_FAILED,
                    mergeParams(weaponParams(weaponState), tagResult.params()),
                    tagResult.logMessage());
        }
        return ZombiesServiceResult.success(new PreparedWeaponStack(stack, tagResult.value().get()));
    }

    public ZombiesServiceResult<InventoryMutationResult> applyPreparedPrimaryWeapon(
            ServerPlayer player,
            RoomId roomId,
            PreparedWeaponStack preparedWeapon,
            ZombiesWeaponInstanceState weaponState
    ) {
        if (player == null) {
            return ZombiesServiceResult.failure(
                    ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                    weaponParams(weaponState),
                    "");
        }
        InventorySnapshot snapshot = InventorySnapshot.capture(player.getInventory());
        try {
            ZombiesServiceResult<InventoryMutationResult> result =
                    applyPreparedPrimaryWeapon(player.getInventory(), roomId, preparedWeapon, weaponState);
            if (!result.success()) {
                snapshot.restore(player.getInventory());
                return result;
            }
            syncInventory(player);
            return result;
        } catch (RuntimeException exception) {
            snapshot.restore(player.getInventory());
            return itemCommitFailure(weaponState, exception);
        }
    }

    public ZombiesServiceResult<InventoryMutationResult> applyPreparedPrimaryWeapon(
            Inventory inventory,
            RoomId roomId,
            PreparedWeaponStack preparedWeapon,
            ZombiesWeaponInstanceState weaponState
    ) {
        if (inventory == null || preparedWeapon == null || weaponState == null) {
            return ZombiesServiceResult.failure(
                    ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                    weaponParams(weaponState),
                    "");
        }

        ItemStack stack = preparedWeapon.itemStack();
        String instanceId = preparedWeapon.tagData().instanceId();
        ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> tagResult =
                weaponItemStackService.writeWeaponTags(
                        stack,
                        roomId,
                        instanceId,
                        ZombiesEquipmentSlot.PRIMARY,
                        weaponState);
        if (!tagResult.success()) {
            return ZombiesServiceResult.failure(
                    WEAPON_ITEM_TAG_FAILED,
                    mergeParams(weaponParams(weaponState), tagResult.params()),
                    tagResult.logMessage());
        }

        removeRoomWeaponSlot(inventory, roomId, ZombiesEquipmentSlot.PRIMARY);
        int inventorySlot = setInventoryItem(inventory, ZombiesEquipmentSlot.PRIMARY.defaultInventorySlot(), stack);
        return ZombiesServiceResult.success(new InventoryMutationResult(
                ZombiesEquipmentSlot.PRIMARY,
                inventorySlot,
                tagResult.value()
                        .map(ZombiesWeaponItemStackService.ZombiesWeaponTagData::instanceId)
                        .orElse(instanceId),
                weaponState));
    }

    public Optional<ZombiesWeaponItemStackService.ZombiesWeaponTagData> currentWeaponTag(
            RoomId roomId,
            ItemStack currentItemStack
    ) {
        return weaponItemStackService.readWeaponTags(currentItemStack)
                .value()
                .filter(tag -> matchesRoom(roomId, tag.roomId()));
    }

    public ZombiesServiceResult<InventoryMutationResult> validateReserveAmmoSync(
            ServerPlayer player,
            RoomId roomId,
            ZombiesEquipmentSlot slot,
            ZombiesWeaponInstanceState weaponState,
            ItemStack currentItemStack
    ) {
        if (player == null) {
            return ZombiesServiceResult.failure(
                    ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                    weaponParams(weaponState),
                    "");
        }
        return validateReserveAmmoSync(player.getInventory(), roomId, slot, weaponState, currentItemStack);
    }

    public ZombiesServiceResult<InventoryMutationResult> validateReserveAmmoSync(
            Inventory inventory,
            RoomId roomId,
            ZombiesEquipmentSlot slot,
            ZombiesWeaponInstanceState weaponState,
            ItemStack currentItemStack
    ) {
        Optional<IndexedWeaponStack> entry = findWeaponStack(inventory, roomId, slot, currentItemStack);
        if (entry.isEmpty()) {
            return ZombiesServiceResult.failure(
                    ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                    weaponParams(weaponState),
                    "");
        }
        ItemStack copy = entry.get().stack().copy();
        ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> existingTag =
                weaponItemStackService.readWeaponTags(copy);
        String instanceId = existingTag.value()
                .map(ZombiesWeaponItemStackService.ZombiesWeaponTagData::instanceId)
                .orElse("");
        ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> tagResult =
                weaponItemStackService.writeWeaponTags(copy, roomId, instanceId, slot, weaponState);
        if (!tagResult.success()) {
            return ZombiesServiceResult.failure(
                    WEAPON_ITEM_TAG_FAILED,
                    mergeParams(weaponParams(weaponState), tagResult.params()),
                    tagResult.logMessage());
        }
        return ZombiesServiceResult.success(new InventoryMutationResult(
                slot,
                entry.get().index(),
                tagResult.value()
                        .map(ZombiesWeaponItemStackService.ZombiesWeaponTagData::instanceId)
                        .orElse(instanceId),
                weaponState));
    }

    public ZombiesServiceResult<InventoryMutationResult> syncReserveAmmo(
            ServerPlayer player,
            RoomId roomId,
            ZombiesEquipmentSlot slot,
            ZombiesWeaponInstanceState weaponState,
            ItemStack currentItemStack
    ) {
        if (player == null) {
            return ZombiesServiceResult.failure(
                    ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                    weaponParams(weaponState),
                    "");
        }
        InventorySnapshot snapshot = InventorySnapshot.capture(player.getInventory());
        try {
            ZombiesServiceResult<InventoryMutationResult> result =
                    syncReserveAmmo(player.getInventory(), roomId, slot, weaponState, currentItemStack);
            if (!result.success()) {
                snapshot.restore(player.getInventory());
                return result;
            }
            syncInventory(player);
            return result;
        } catch (RuntimeException exception) {
            snapshot.restore(player.getInventory());
            return itemCommitFailure(weaponState, exception);
        }
    }

    public ZombiesServiceResult<InventoryMutationResult> syncReserveAmmo(
            Inventory inventory,
            RoomId roomId,
            ZombiesEquipmentSlot slot,
            ZombiesWeaponInstanceState weaponState,
            ItemStack currentItemStack
    ) {
        Optional<IndexedWeaponStack> entry = findWeaponStack(inventory, roomId, slot, currentItemStack);
        if (entry.isEmpty()) {
            return ZombiesServiceResult.failure(
                    ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                    weaponParams(weaponState),
                    "");
        }
        ZombiesServiceResult<InventoryMutationResult> result =
                syncReserveAmmo(entry.get().stack(), roomId, slot, weaponState);
        return result.success()
                ? ZombiesServiceResult.success(new InventoryMutationResult(
                        slot,
                        entry.get().index(),
                        result.value().map(InventoryMutationResult::instanceId).orElse(""),
                        weaponState))
                : result;
    }

    public ZombiesServiceResult<InventoryMutationResult> syncReserveAmmo(
            ItemStack stack,
            RoomId roomId,
            ZombiesEquipmentSlot slot,
            ZombiesWeaponInstanceState weaponState
    ) {
        if (stack == null || stack.isEmpty() || slot == null || weaponState == null) {
            return ZombiesServiceResult.failure(
                    ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                    weaponParams(weaponState),
                    "");
        }
        ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> existingTag =
                weaponItemStackService.readWeaponTags(stack);
        if (!existingTag.success() || existingTag.value().isEmpty()) {
            return ZombiesServiceResult.failure(
                    ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                    weaponParams(weaponState),
                    "");
        }
        ZombiesWeaponItemStackService.ZombiesWeaponTagData tag = existingTag.value().get();
        if (tag.slot() != slot || !matchesRoom(roomId, tag.roomId())) {
            return ZombiesServiceResult.failure(
                    ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                    weaponParams(weaponState),
                    "");
        }
        ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> tagResult =
                weaponItemStackService.writeWeaponTags(stack, roomId, tag.instanceId(), slot, weaponState);
        if (!tagResult.success()) {
            return ZombiesServiceResult.failure(
                    WEAPON_ITEM_TAG_FAILED,
                    mergeParams(weaponParams(weaponState), tagResult.params()),
                    tagResult.logMessage());
        }
        return ZombiesServiceResult.success(new InventoryMutationResult(
                slot,
                -1,
                tagResult.value()
                        .map(ZombiesWeaponItemStackService.ZombiesWeaponTagData::instanceId)
                        .orElse(tag.instanceId()),
                weaponState));
    }

    private Optional<IndexedWeaponStack> findWeaponStack(
            Inventory inventory,
            RoomId roomId,
            ZombiesEquipmentSlot slot,
            ItemStack currentItemStack
    ) {
        if (inventory == null || slot == null) {
            return Optional.empty();
        }
        Optional<ZombiesWeaponItemStackService.ZombiesWeaponTagData> currentTag =
                currentWeaponTag(roomId, currentItemStack)
                        .filter(tag -> tag.slot() == slot);
        if (currentTag.isPresent()) {
            String currentInstanceId = currentTag.get().instanceId();
            Optional<IndexedWeaponStack> byInstance = findWeaponStack(
                    inventory,
                    roomId,
                    slot,
                    currentInstanceId);
            if (byInstance.isPresent()) {
                return byInstance;
            }
        }
        return findWeaponStack(inventory, roomId, slot, "");
    }

    private Optional<IndexedWeaponStack> findWeaponStack(
            Inventory inventory,
            RoomId roomId,
            ZombiesEquipmentSlot slot,
            String instanceId
    ) {
        String expectedInstanceId = Objects.requireNonNullElse(instanceId, "").trim();
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            ItemStack stack = inventory.getItem(index);
            ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> tagResult =
                    weaponItemStackService.readWeaponTags(stack);
            if (!tagResult.success() || tagResult.value().isEmpty()) {
                continue;
            }
            ZombiesWeaponItemStackService.ZombiesWeaponTagData tag = tagResult.value().get();
            if (tag.slot() != slot || !matchesRoom(roomId, tag.roomId())) {
                continue;
            }
            if (!expectedInstanceId.isBlank() && !expectedInstanceId.equals(tag.instanceId())) {
                continue;
            }
            return Optional.of(new IndexedWeaponStack(index, stack));
        }
        return Optional.empty();
    }

    private void removeRoomWeaponSlot(
            Inventory inventory,
            RoomId roomId,
            ZombiesEquipmentSlot slot
    ) {
        if (inventory == null || slot == null) {
            return;
        }
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            ItemStack stack = inventory.getItem(index);
            ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> tagResult =
                    weaponItemStackService.readWeaponTags(stack);
            if (!tagResult.success() || tagResult.value().isEmpty()) {
                continue;
            }
            ZombiesWeaponItemStackService.ZombiesWeaponTagData tag = tagResult.value().get();
            if (tag.slot() == slot && matchesRoom(roomId, tag.roomId())) {
                inventory.setItem(index, ItemStack.EMPTY);
            }
        }
    }

    private static int setInventoryItem(Inventory inventory, int preferredSlot, ItemStack stack) {
        if (inventory == null || stack == null || stack.isEmpty()) {
            return -1;
        }
        if (preferredSlot >= 0 && preferredSlot < inventory.getContainerSize()) {
            inventory.setItem(preferredSlot, stack.copy());
            return preferredSlot;
        }
        ItemStack copy = stack.copy();
        boolean added = inventory.add(copy);
        return added ? -1 : -1;
    }

    private static ItemStack createDefaultTaczGunStack(String gunId) {
        ResourceLocation parsedGunId = ResourceLocation.tryParse(Objects.requireNonNullElse(gunId, "").trim());
        if (parsedGunId == null) {
            return ItemStack.EMPTY;
        }
        String normalizedGunId = parsedGunId.toString();
        String nbt = "{GunId:\"" + normalizedGunId
                + "\",GunCurrentAmmoCount:0,GunFireMode:\"AUTO\",HasBulletInBarrel:1}";
        ItemStack stack = BackpackItemStackFactory.create(new BackpackConfig.Backpack.ItemData(
                DEFAULT_TACZ_GUN_ITEM_ID,
                1,
                nbt));
        if (stack.isEmpty() || !TaczGatewayProvider.gateway().isGun(stack)) {
            return ItemStack.EMPTY;
        }
        Optional<String> resolvedGunId = TaczGatewayProvider.gateway().resolveGunId(stack);
        if (resolvedGunId.isEmpty() || !normalizedGunId.equals(resolvedGunId.get())) {
            return ItemStack.EMPTY;
        }
        return stack;
    }

    public static ItemStack createDefaultTaczGunStackForRules(String gunId) {
        return createDefaultTaczGunStack(gunId);
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
        String expectedRoomId = roomId == null ? "" : roomId.encode();
        String actualRoomId = Objects.requireNonNullElse(taggedRoomId, "").trim();
        return expectedRoomId.isBlank() || actualRoomId.isBlank() || expectedRoomId.equals(actualRoomId);
    }

    private static Map<String, ModePlayerValue> weaponParams(ZombiesWeaponInstanceState weaponState) {
        Map<String, ModePlayerValue> params = new LinkedHashMap<>();
        params.put("gunId", ModePlayerValue.ofString(weaponState == null ? "" : weaponState.gunId()));
        params.put("weaponLevel", ModePlayerValue.ofInt(weaponState == null ? 0 : weaponState.weaponLevel()));
        return params;
    }

    private static Map<String, ModePlayerValue> mergeParams(
            Map<String, ModePlayerValue> first,
            Map<String, ModePlayerValue> second
    ) {
        Map<String, ModePlayerValue> merged = new LinkedHashMap<>();
        if (first != null) {
            merged.putAll(first);
        }
        if (second != null) {
            merged.putAll(second);
        }
        return merged;
    }

    private static ZombiesServiceResult<InventoryMutationResult> itemCommitFailure(
            ZombiesWeaponInstanceState weaponState,
            RuntimeException exception
    ) {
        return ZombiesServiceResult.failure(
                WEAPON_ITEM_COMMIT_FAILED,
                weaponParams(weaponState),
                "Zombies weapon inventory commit failed: " + exception.getClass().getName());
    }

    private record InventorySnapshot(List<ItemStack> items) {
        private InventorySnapshot {
            items = List.copyOf(items);
        }

        private static InventorySnapshot capture(Inventory inventory) {
            if (inventory == null) {
                return new InventorySnapshot(List.of());
            }
            List<ItemStack> snapshot = new ArrayList<>(inventory.getContainerSize());
            for (int index = 0; index < inventory.getContainerSize(); index++) {
                snapshot.add(inventory.getItem(index).copy());
            }
            return new InventorySnapshot(snapshot);
        }

        private void restore(Inventory inventory) {
            if (inventory == null) {
                return;
            }
            int restoreSize = Math.min(inventory.getContainerSize(), items.size());
            for (int index = 0; index < restoreSize; index++) {
                inventory.setItem(index, items.get(index).copy());
            }
            for (int index = restoreSize; index < inventory.getContainerSize(); index++) {
                inventory.setItem(index, ItemStack.EMPTY);
            }
        }
    }

    @FunctionalInterface
    public interface WeaponStackFactory {
        ItemStack create(String gunId);
    }

    public record PreparedWeaponStack(
            ItemStack itemStack,
            ZombiesWeaponItemStackService.ZombiesWeaponTagData tagData
    ) {
        public PreparedWeaponStack {
            itemStack = itemStack == null ? ItemStack.EMPTY : itemStack.copy();
            Objects.requireNonNull(tagData, "tagData");
        }

        @Override
        public ItemStack itemStack() {
            return itemStack.copy();
        }
    }

    public record InventoryMutationResult(
            ZombiesEquipmentSlot slot,
            int inventorySlot,
            String instanceId,
            ZombiesWeaponInstanceState weaponState
    ) {
        public InventoryMutationResult {
            Objects.requireNonNull(slot, "slot");
            inventorySlot = Math.max(-1, inventorySlot);
            instanceId = Objects.requireNonNullElse(instanceId, "").trim();
            Objects.requireNonNull(weaponState, "weaponState");
        }
    }

    private record IndexedWeaponStack(
            int index,
            ItemStack stack
    ) {
        private IndexedWeaponStack {
            index = Math.max(0, index);
            stack = stack == null ? ItemStack.EMPTY : stack;
        }
    }
}
