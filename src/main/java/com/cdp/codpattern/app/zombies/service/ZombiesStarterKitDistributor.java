package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.model.ZombiesEquipmentSlot;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.config.zombies.ZombiesBackpackConfig;
import com.cdp.codpattern.config.zombies.ZombiesWeaponFilterConfig;
import com.cdp.codpattern.core.refit.AttachmentPresetUtil;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ZombiesStarterKitDistributor {
    private static final int STARTER_SLOT = 0;
    private final ZombiesWeaponItemStackService weaponItemStackService = new ZombiesWeaponItemStackService();

    public ZombiesServiceResult<PreparedStarterKits> prepareStarterWeapons(
            Collection<UUID> playerIds,
            ZombiesBackpackConfig backpackConfig,
            ZombiesWeaponFilterConfig filterConfig
    ) {
        return prepareStarterWeapons(null, playerIds, backpackConfig, filterConfig);
    }

    public ZombiesServiceResult<PreparedStarterKits> prepareStarterWeapons(
            RoomId roomId,
            Collection<UUID> playerIds,
            ZombiesBackpackConfig backpackConfig,
            ZombiesWeaponFilterConfig filterConfig
    ) {
        List<UUID> members = normalizeMembers(playerIds);
        Map<UUID, ItemStack> weapons = new LinkedHashMap<>();
        Map<UUID, ZombiesWeaponInstanceState> starterWeaponStates = new LinkedHashMap<>();
        ZombiesBackpackConfig resolvedBackpackConfig = backpackConfig == null ? new ZombiesBackpackConfig() : backpackConfig;
        ZombiesWeaponFilterConfig resolvedFilterConfig = filterConfig == null ? new ZombiesWeaponFilterConfig() : filterConfig;
        resolvedBackpackConfig.normalize();
        resolvedFilterConfig.normalize();

        for (UUID playerId : members) {
            ZombiesBackpackConfig.PlayerZombiesBackpackData playerData =
                    resolvedBackpackConfig.getOrCreatePlayerData(playerId.toString());
            ZombiesServiceResult<ItemStack> weaponResult = createStarterWeapon(
                    playerData.getWeapon(),
                    resolvedFilterConfig);
            if (!weaponResult.success() || weaponResult.value().isEmpty() || weaponResult.value().get().isEmpty()) {
                return ZombiesServiceResult.failure(
                        ZombiesErrorCode.STARTUP_STARTER_WEAPON_MISSING,
                        weaponResult.params(),
                        weaponResult.logMessage());
            }
            ItemStack weapon = weaponResult.value().get().copy();
            if (roomId != null) {
                ZombiesWeaponInstanceState starterWeaponState = starterWeaponState(weapon);
                ZombiesServiceResult<ZombiesWeaponItemStackService.ZombiesWeaponTagData> tagResult =
                        weaponItemStackService.writeWeaponTags(
                                weapon,
                                roomId,
                                ZombiesEquipmentSlot.STARTER,
                                starterWeaponState);
                if (!tagResult.success()) {
                    return ZombiesServiceResult.failure(
                            ZombiesErrorCode.STARTUP_STARTER_WEAPON_MISSING,
                            tagResult.params(),
                            tagResult.logMessage());
                }
                starterWeaponStates.put(playerId, starterWeaponState);
            }
            weapons.put(playerId, weapon);
        }
        return ZombiesServiceResult.success(new PreparedStarterKits(weapons, starterWeaponStates));
    }

    public ZombiesServiceResult<Void> applyStarterWeapons(ServerLevel level, PreparedStarterKits starterKits) {
        if (level == null || starterKits == null) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.STARTUP_STARTER_WEAPON_MISSING);
        }
        for (UUID playerId : starterKits.playerIds()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            ItemStack weapon = starterKits.weapon(playerId).orElse(ItemStack.EMPTY);
            if (player == null || weapon.isEmpty()) {
                return ZombiesServiceResult.failure(
                        ZombiesErrorCode.STARTUP_STARTER_WEAPON_MISSING,
                        Map.of("playerId", com.cdp.codpattern.app.match.model.ModePlayerValue.ofString(
                                playerId == null ? "" : playerId.toString())),
                        "Zombies starter weapon could not be applied");
            }
        }

        for (UUID playerId : starterKits.playerIds()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            ItemStack weapon = starterKits.weapon(playerId).orElse(ItemStack.EMPTY).copy();
            player.getInventory().clearContent();
            ThrowableInventoryService.clearRuntime(player, true);
            player.getInventory().setItem(STARTER_SLOT, weapon);
            player.inventoryMenu.broadcastChanges();
            player.inventoryMenu.slotsChanged(player.getInventory());
            ThrowableInventoryService.sync(player);
        }
        return ZombiesServiceResult.ok();
    }

    public ZombiesServiceResult<ItemStack> createStarterWeapon(
            ZombiesBackpackConfig.WeaponData weaponData,
            ZombiesWeaponFilterConfig filterConfig
    ) {
        ZombiesBackpackConfig.WeaponData data = weaponData == null
                ? ZombiesBackpackConfig.defaultWeapon()
                : weaponData;
        ResourceLocation itemId = ResourceLocation.tryParse(data.getItem());
        if (itemId == null) {
            return starterWeaponFailure("invalid_item_id");
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null || item == Items.AIR) {
            return starterWeaponFailure("unknown_item");
        }

        try {
            ItemStack stack = new ItemStack(item, Math.max(1, data.getCount()));
            if (data.getNbt() != null && !data.getNbt().isBlank()) {
                stack.setTag(TagParser.parseTag(data.getNbt()));
            }

            String attachmentPreset = data.getAttachmentPreset();
            if (attachmentPreset != null
                    && !attachmentPreset.isBlank()
                    && TaczGatewayProvider.gateway().isGun(stack)) {
                CompoundTag presetTag = AttachmentPresetUtil.parsePresetString(attachmentPreset);
                if (!presetTag.isEmpty()) {
                    AttachmentPresetUtil.applyPresetToGun(stack, presetTag);
                }
            }

            ZombiesWeaponFilterConfig resolvedFilter = filterConfig == null ? new ZombiesWeaponFilterConfig() : filterConfig;
            resolvedFilter.normalize();
            if (isBlocked(resolvedFilter, stack, itemId)) {
                return starterWeaponFailure("blocked_weapon");
            }
            if (TaczGatewayProvider.gateway().isGun(stack)) {
                int ammoMultiple = Math.max(0, (int) Math.floor(resolvedFilter.getAmmunitionPerMagazineMultiple()));
                TaczGatewayProvider.gateway().configureGunAmmo(stack, ammoMultiple);
            }
            return ZombiesServiceResult.success(stack);
        } catch (Exception exception) {
            return starterWeaponFailure("exception:" + exception.getClass().getSimpleName());
        }
    }

    private static ZombiesServiceResult<ItemStack> starterWeaponFailure(String reason) {
        return ZombiesServiceResult.failure(
                ZombiesErrorCode.STARTUP_STARTER_WEAPON_MISSING,
                Map.of("reason", com.cdp.codpattern.app.match.model.ModePlayerValue.ofString(reason)),
                "Zombies starter weapon failed: " + reason);
    }

    private static boolean isBlocked(
            ZombiesWeaponFilterConfig filterConfig,
            ItemStack stack,
            ResourceLocation fallbackItemId
    ) {
        Optional<ResourceLocation> weaponId = resolveWeaponId(stack, fallbackItemId);
        if (weaponId.isEmpty()) {
            return false;
        }
        ResourceLocation id = weaponId.get();
        return stringListContains(filterConfig.getBlockedItemNamespaces(), id.getNamespace())
                || stringListContains(filterConfig.getBlockedWeaponIds(), id.toString())
                || hasBlockedInstalledAttachment(filterConfig, stack);
    }

    private static Optional<ResourceLocation> resolveWeaponId(ItemStack stack, ResourceLocation fallbackItemId) {
        if (stack != null && !stack.isEmpty() && TaczGatewayProvider.gateway().isGun(stack)) {
            Optional<String> gunId = TaczGatewayProvider.gateway().resolveGunId(stack);
            if (gunId.isPresent()) {
                ResourceLocation parsedGunId = ResourceLocation.tryParse(gunId.get());
                if (parsedGunId != null) {
                    return Optional.of(parsedGunId);
                }
            }
        }
        return Optional.ofNullable(fallbackItemId);
    }

    private static ZombiesWeaponInstanceState starterWeaponState(ItemStack stack) {
        String gunId = TaczGatewayProvider.gateway().resolveGunId(stack)
                .orElseGet(() -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        int reserveAmmo = Math.max(0, TaczGatewayProvider.gateway().resolveReserveAmmo(stack));
        int maxReserveAmmo = Math.max(reserveAmmo, TaczGatewayProvider.gateway().resolveMaxReserveAmmo(stack));
        return new ZombiesWeaponInstanceState(
                gunId,
                0,
                0,
                1.0D,
                1.0D,
                reserveAmmo,
                maxReserveAmmo);
    }

    private static boolean hasBlockedInstalledAttachment(ZombiesWeaponFilterConfig filterConfig, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !TaczGatewayProvider.gateway().isGun(stack)) {
            return false;
        }
        for (String attachmentId : TaczGatewayProvider.gateway().resolveInstalledAttachmentIds(stack)) {
            ResourceLocation parsed = ResourceLocation.tryParse(attachmentId);
            if (parsed == null) {
                continue;
            }
            if (stringListContains(filterConfig.getBlockedAttachmentNamespaces(), parsed.getNamespace())
                    || stringListContains(filterConfig.getBlockedAttachmentIds(), parsed.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean stringListContains(List<String> values, String expected) {
        if (values == null || expected == null) {
            return false;
        }
        String normalizedExpected = expected.trim().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value != null && normalizedExpected.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static List<UUID> normalizeMembers(Collection<UUID> playerIds) {
        List<UUID> members = new ArrayList<>();
        if (playerIds == null) {
            return members;
        }
        for (UUID playerId : playerIds) {
            if (playerId != null && !members.contains(playerId)) {
                members.add(playerId);
            }
        }
        return List.copyOf(members);
    }

    public record PreparedStarterKits(
            Map<UUID, ItemStack> weapons,
            Map<UUID, ZombiesWeaponInstanceState> starterWeaponStates
    ) {
        public PreparedStarterKits(Map<UUID, ItemStack> weapons) {
            this(weapons, Map.of());
        }

        public PreparedStarterKits {
            Objects.requireNonNull(weapons, "weapons");
            Map<UUID, ItemStack> copied = new LinkedHashMap<>();
            for (Map.Entry<UUID, ItemStack> entry : weapons.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    copied.put(entry.getKey(), entry.getValue().copy());
                }
            }
            weapons = Map.copyOf(copied);
            Map<UUID, ZombiesWeaponInstanceState> copiedStates = new LinkedHashMap<>();
            if (starterWeaponStates != null) {
                for (Map.Entry<UUID, ZombiesWeaponInstanceState> entry : starterWeaponStates.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        copiedStates.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            starterWeaponStates = Map.copyOf(copiedStates);
        }

        public List<UUID> playerIds() {
            return List.copyOf(weapons.keySet());
        }

        public Optional<ItemStack> weapon(UUID playerId) {
            ItemStack stack = weapons.get(playerId);
            return stack == null || stack.isEmpty() ? Optional.empty() : Optional.of(stack.copy());
        }

        public Optional<ZombiesWeaponInstanceState> starterWeaponState(UUID playerId) {
            return Optional.ofNullable(starterWeaponStates.get(playerId));
        }
    }
}
