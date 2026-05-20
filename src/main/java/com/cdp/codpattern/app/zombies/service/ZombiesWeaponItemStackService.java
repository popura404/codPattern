package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.model.ZombiesEquipmentSlot;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ZombiesWeaponItemStackService {
    public static final String TAG_ROOM_ID = "codpattern.zombies.roomId";
    public static final String TAG_INSTANCE_ID = "codpattern.zombies.instanceId";
    public static final String TAG_SLOT = "codpattern.zombies.slot";
    public static final String TAG_GUN_ID = "codpattern.zombies.gunId";
    public static final String TAG_RARITY_ID = "codpattern.zombies.rarityId";
    public static final String TAG_WEAPON_LEVEL = "codpattern.zombies.weaponLevel";
    public static final String TAG_LEVEL_DAMAGE_MULTIPLIER = "codpattern.zombies.levelDamageMultiplier";
    public static final String TAG_UPGRADE_LEVEL = "codpattern.zombies.upgradeLevel";
    public static final String TAG_UPGRADE_DAMAGE_MULTIPLIER = "codpattern.zombies.upgradeDamageMultiplier";
    public static final String TAG_RESERVE_AMMO = "codpattern.zombies.reserveAmmo";
    public static final String TAG_MAX_RESERVE_AMMO = "codpattern.zombies.maxReserveAmmo";

    private static final ZombiesErrorCode INVALID_ITEM_STACK = ZombiesErrorCode.of("weapon.invalid_item_stack");
    private static final ZombiesErrorCode INVALID_WEAPON_TAG = ZombiesErrorCode.of("weapon.invalid_tag");

    public ZombiesServiceResult<ZombiesWeaponTagData> writeWeaponTags(
            ItemStack stack,
            RoomId roomId,
            ZombiesEquipmentSlot slot,
            ZombiesWeaponInstanceState weaponState
    ) {
        return writeWeaponTags(stack, roomId, newInstanceId(), slot, weaponState);
    }

    public ZombiesServiceResult<ZombiesWeaponTagData> writeWeaponTags(
            ItemStack stack,
            RoomId roomId,
            String instanceId,
            ZombiesEquipmentSlot slot,
            ZombiesWeaponInstanceState weaponState
    ) {
        if (stack == null || stack.isEmpty()) {
            return ZombiesServiceResult.failure(INVALID_ITEM_STACK);
        }
        String encodedRoomId = encodeRoomId(roomId);
        if (encodedRoomId.isBlank() || weaponState == null || !validGunId(weaponState.gunId()) || slot == null) {
            return ZombiesServiceResult.failure(INVALID_WEAPON_TAG, tagParams(roomId, slot, weaponState), "");
        }

        ZombiesWeaponTagData data = ZombiesWeaponTagData.fromState(
                encodedRoomId,
                normalizeInstanceId(instanceId),
                slot,
                weaponState);
        writeTag(stack.getOrCreateTag(), data);
        syncTaczReserveAmmo(stack, data);
        return ZombiesServiceResult.success(data);
    }

    public ZombiesServiceResult<ZombiesWeaponTagData> readWeaponTags(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getTag() == null) {
            return ZombiesServiceResult.failure(INVALID_WEAPON_TAG);
        }
        ZombiesServiceResult<ZombiesWeaponTagData> result = readWeaponTags(stack.getTag());
        return result.value()
                .map(data -> ZombiesServiceResult.success(withLiveTaczReserveAmmo(stack, data)))
                .orElse(result);
    }

    public ZombiesServiceResult<ZombiesWeaponTagData> readWeaponTags(CompoundTag tag) {
        if (tag == null) {
            return ZombiesServiceResult.failure(INVALID_WEAPON_TAG);
        }
        String roomId = stringTag(tag, TAG_ROOM_ID);
        String instanceId = stringTag(tag, TAG_INSTANCE_ID);
        Optional<ZombiesEquipmentSlot> slot = ZombiesEquipmentSlot.fromKey(stringTag(tag, TAG_SLOT));
        String gunId = stringTag(tag, TAG_GUN_ID);
        if (roomId.isBlank() || instanceId.isBlank() || slot.isEmpty() || gunId.isBlank()) {
            return ZombiesServiceResult.failure(INVALID_WEAPON_TAG);
        }
        int reserveAmmo = positiveIntTag(tag, TAG_RESERVE_AMMO);
        int maxReserveAmmo = tag.contains(TAG_MAX_RESERVE_AMMO, Tag.TAG_INT)
                ? positiveIntTag(tag, TAG_MAX_RESERVE_AMMO)
                : reserveAmmo;
        ZombiesWeaponTagData data = new ZombiesWeaponTagData(
                roomId,
                instanceId,
                slot.get(),
                gunId,
                stringTag(tag, TAG_RARITY_ID),
                positiveIntTag(tag, TAG_WEAPON_LEVEL),
                positiveDoubleTag(tag, TAG_LEVEL_DAMAGE_MULTIPLIER, 1.0D),
                positiveIntTag(tag, TAG_UPGRADE_LEVEL),
                positiveDoubleTag(tag, TAG_UPGRADE_DAMAGE_MULTIPLIER, 1.0D),
                reserveAmmo,
                maxReserveAmmo);
        if (!validGunId(data.gunId())) {
            return ZombiesServiceResult.failure(INVALID_WEAPON_TAG);
        }
        return ZombiesServiceResult.success(data);
    }

    public boolean isZombiesWeapon(ItemStack stack) {
        return readWeaponTags(stack).success();
    }

    public boolean belongsToRoom(ItemStack stack, RoomId roomId) {
        String encodedRoomId = encodeRoomId(roomId);
        return readWeaponTags(stack)
                .value()
                .filter(data -> data.roomId().equals(encodedRoomId))
                .isPresent();
    }

    public double sameRoomDamageMultiplier(ItemStack stack, RoomId roomId) {
        return readWeaponTags(stack)
                .value()
                .filter(data -> data.roomId().equals(encodeRoomId(roomId)))
                .map(this::damageMultiplier)
                .orElse(1.0D);
    }

    public double sameRoomDamageMultiplier(CompoundTag tag, RoomId roomId) {
        return readWeaponTags(tag)
                .value()
                .filter(data -> data.roomId().equals(encodeRoomId(roomId)))
                .map(this::damageMultiplier)
                .orElse(1.0D);
    }

    public double damageMultiplier(ZombiesWeaponTagData data) {
        if (data == null) {
            return 1.0D;
        }
        double multiplier = data.levelDamageMultiplier() * data.upgradeDamageMultiplier();
        return ZombiesWeaponInstanceState.isValidDamageMultiplier(multiplier) ? multiplier : 1.0D;
    }

    public ItemStack retagCopyForRoom(
            ItemStack stack,
            RoomId roomId,
            ZombiesEquipmentSlot slot,
            ZombiesWeaponInstanceState weaponState
    ) {
        ItemStack copy = stack == null ? ItemStack.EMPTY : stack.copy();
        if (!copy.isEmpty()) {
            writeWeaponTags(copy, roomId, slot, weaponState);
        }
        return copy;
    }

    public void stripWeaponTags(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getTag() == null) {
            return;
        }
        CompoundTag tag = stack.getTag();
        tag.remove(TAG_ROOM_ID);
        tag.remove(TAG_INSTANCE_ID);
        tag.remove(TAG_SLOT);
        tag.remove(TAG_GUN_ID);
        tag.remove(TAG_RARITY_ID);
        tag.remove(TAG_WEAPON_LEVEL);
        tag.remove(TAG_LEVEL_DAMAGE_MULTIPLIER);
        tag.remove(TAG_UPGRADE_LEVEL);
        tag.remove(TAG_UPGRADE_DAMAGE_MULTIPLIER);
        tag.remove(TAG_RESERVE_AMMO);
        tag.remove(TAG_MAX_RESERVE_AMMO);
    }

    public void writeTag(CompoundTag tag, ZombiesWeaponTagData data) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(data, "data");
        tag.putString(TAG_ROOM_ID, data.roomId());
        tag.putString(TAG_INSTANCE_ID, data.instanceId());
        tag.putString(TAG_SLOT, data.slot().key());
        tag.putString(TAG_GUN_ID, data.gunId());
        if (data.rarityId().isBlank()) {
            tag.remove(TAG_RARITY_ID);
        } else {
            tag.putString(TAG_RARITY_ID, data.rarityId());
        }
        tag.putInt(TAG_WEAPON_LEVEL, data.weaponLevel());
        tag.putFloat(TAG_LEVEL_DAMAGE_MULTIPLIER, (float) data.levelDamageMultiplier());
        tag.putInt(TAG_UPGRADE_LEVEL, data.upgradeLevel());
        tag.putFloat(TAG_UPGRADE_DAMAGE_MULTIPLIER, (float) data.upgradeDamageMultiplier());
        tag.putInt(TAG_RESERVE_AMMO, data.reserveAmmo());
        tag.putInt(TAG_MAX_RESERVE_AMMO, data.maxReserveAmmo());
    }

    private void syncTaczReserveAmmo(ItemStack stack, ZombiesWeaponTagData data) {
        if (stack == null || stack.isEmpty() || !TaczGatewayProvider.gateway().isGun(stack)) {
            return;
        }
        TaczGatewayProvider.gateway().setReserveAmmo(stack, data.reserveAmmo(), data.maxReserveAmmo());
    }

    private ZombiesWeaponTagData withLiveTaczReserveAmmo(ItemStack stack, ZombiesWeaponTagData data) {
        if (stack == null || stack.isEmpty() || data == null || !TaczGatewayProvider.gateway().isGun(stack)) {
            return data;
        }
        int liveMaxReserveAmmo = Math.max(0, TaczGatewayProvider.gateway().resolveMaxReserveAmmo(stack));
        if (liveMaxReserveAmmo <= 0) {
            return data;
        }
        int liveReserveAmmo = Math.max(
                0,
                Math.min(TaczGatewayProvider.gateway().resolveReserveAmmo(stack), liveMaxReserveAmmo));
        return new ZombiesWeaponTagData(
                data.roomId(),
                data.instanceId(),
                data.slot(),
                data.gunId(),
                data.rarityId(),
                data.weaponLevel(),
                data.levelDamageMultiplier(),
                data.upgradeLevel(),
                data.upgradeDamageMultiplier(),
                liveReserveAmmo,
                liveMaxReserveAmmo);
    }

    private static Map<String, ModePlayerValue> tagParams(
            RoomId roomId,
            ZombiesEquipmentSlot slot,
            ZombiesWeaponInstanceState weaponState
    ) {
        Map<String, ModePlayerValue> params = new LinkedHashMap<>();
        params.put("roomId", ModePlayerValue.ofString(encodeRoomId(roomId)));
        params.put("slot", ModePlayerValue.ofString(slot == null ? "" : slot.key()));
        params.put("gunId", ModePlayerValue.ofString(weaponState == null ? "" : weaponState.gunId()));
        return params;
    }

    private static String stringTag(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key).trim() : "";
    }

    private static int positiveIntTag(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_INT) ? Math.max(0, tag.getInt(key)) : 0;
    }

    private static double positiveDoubleTag(CompoundTag tag, String key, double fallback) {
        double value;
        if (tag.contains(key, Tag.TAG_FLOAT)) {
            value = tag.getFloat(key);
        } else if (tag.contains(key, Tag.TAG_DOUBLE)) {
            value = tag.getDouble(key);
        } else {
            value = fallback;
        }
        return ZombiesWeaponInstanceState.isValidDamageMultiplier(value) ? value : fallback;
    }

    private static String encodeRoomId(RoomId roomId) {
        return roomId == null ? "" : roomId.encode();
    }

    private static String normalizeInstanceId(String instanceId) {
        String normalized = Objects.requireNonNullElse(instanceId, "").trim();
        return normalized.isEmpty() ? newInstanceId() : normalized;
    }

    private static String newInstanceId() {
        return UUID.randomUUID().toString();
    }

    private static boolean validGunId(String gunId) {
        return ZombiesWeaponInstanceState.isValidGunId(gunId);
    }

    public record ZombiesWeaponTagData(
            String roomId,
            String instanceId,
            ZombiesEquipmentSlot slot,
            String gunId,
            String rarityId,
            int weaponLevel,
            double levelDamageMultiplier,
            int upgradeLevel,
            double upgradeDamageMultiplier,
            int reserveAmmo,
            int maxReserveAmmo
    ) {
        public ZombiesWeaponTagData {
            roomId = Objects.requireNonNullElse(roomId, "").trim();
            instanceId = normalizeInstanceId(instanceId);
            slot = Objects.requireNonNull(slot, "slot");
            gunId = Objects.requireNonNullElse(gunId, "").trim();
            rarityId = Objects.requireNonNullElse(rarityId, "").trim();
            weaponLevel = Math.max(0, weaponLevel);
            levelDamageMultiplier = ZombiesWeaponInstanceState.isValidDamageMultiplier(levelDamageMultiplier)
                    ? levelDamageMultiplier
                    : 1.0D;
            upgradeLevel = Math.max(0, upgradeLevel);
            upgradeDamageMultiplier = ZombiesWeaponInstanceState.isValidDamageMultiplier(upgradeDamageMultiplier)
                    ? upgradeDamageMultiplier
                    : 1.0D;
            reserveAmmo = Math.max(0, reserveAmmo);
            maxReserveAmmo = Math.max(reserveAmmo, maxReserveAmmo);
        }

        public static ZombiesWeaponTagData fromState(
                String roomId,
                String instanceId,
                ZombiesEquipmentSlot slot,
                ZombiesWeaponInstanceState weaponState
        ) {
            Objects.requireNonNull(weaponState, "weaponState");
            return new ZombiesWeaponTagData(
                    roomId,
                    instanceId,
                    slot,
                    weaponState.gunId(),
                    weaponState.rarityId(),
                    weaponState.weaponLevel(),
                    weaponState.levelDamageMultiplier(),
                    weaponState.upgradeLevel(),
                    weaponState.upgradeDamageMultiplier(),
                    weaponState.reserveAmmo(),
                    weaponState.maxReserveAmmo());
        }

        public ZombiesWeaponInstanceState toWeaponState() {
            return new ZombiesWeaponInstanceState(
                    gunId,
                    rarityId,
                    weaponLevel,
                    upgradeLevel,
                    levelDamageMultiplier,
                    upgradeDamageMultiplier,
                    reserveAmmo,
                    maxReserveAmmo);
        }
    }
}
