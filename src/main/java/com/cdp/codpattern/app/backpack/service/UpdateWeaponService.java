package com.cdp.codpattern.app.backpack.service;

import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.backpack.BackpackSlot;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.compat.lrtactical.LrTacticalGatewayProvider;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class UpdateWeaponService {
    private static final String THROWABLE_ID_TAG = "ThrowableId";
    private static final String MELEE_WEAPON_ID_TAG = "MeleeWeaponId";

    public record Result(BackpackConfig.PlayerBackpackData playerData, boolean success, String slot, String code,
            String message) {
    }

    private record ValidationResult(boolean success, String code, String message) {
        static ValidationResult ok() {
            return new ValidationResult(true, "OK", "");
        }

        static ValidationResult fail(String code, String message) {
            return new ValidationResult(false, code, message);
        }
    }

    private UpdateWeaponService() {
    }

    public static Result process(ServerPlayer player, int backpackId, String weaponSlot, String itemId, String nbt) {
        Path backpackPath = ConfigPath.SERVERBACKPACK.getPath(player.server);
        Path filterPath = ConfigPath.SERVER_FILTER.getPath(player.server);

        BackpackConfig.PlayerBackpackData playerData = BackpackConfigRepository.loadOrCreatePlayer(
                player.getUUID().toString(), backpackPath);
        BackpackConfig.Backpack backpack = playerData.getBackpacks_MAP().get(backpackId);
        if (backpack == null) {
            return fail(playerData, weaponSlot, "BAG_NOT_FOUND", "");
        }

        BackpackSlot slot = BackpackSlot.fromMenuKey(weaponSlot);
        if (slot == null) {
            return fail(playerData, weaponSlot, "SLOT_INVALID", "");
        }

        WeaponFilterConfig filterConfig = WeaponFilterConfigRepository.loadOrCreate(filterPath);
        ValidationResult throwableAvailabilityResult = validateThrowableAvailability(slot, filterConfig);
        if (!throwableAvailabilityResult.success()) {
            return fail(playerData, weaponSlot, throwableAvailabilityResult.code(), throwableAvailabilityResult.message());
        }

        CompoundTag parsedNbt;
        try {
            parsedNbt = parseNbt(nbt);
        } catch (Exception e) {
            return fail(playerData, weaponSlot, "NBT_INVALID", "");
        }

        ResourceLocation itemLocation = itemId == null || itemId.isBlank() ? null : ResourceLocation.tryParse(itemId);
        if (itemLocation == null) {
            return fail(playerData, weaponSlot, "ITEM_ID_INVALID", "");
        }
        Item item = BuiltInRegistries.ITEM.get(itemLocation);
        if (item == null || item == Items.AIR) {
            return fail(playerData, weaponSlot, "ITEM_NOT_REGISTERED", "");
        }
        ItemStack candidateStack = new ItemStack(item);
        if (!parsedNbt.isEmpty()) {
            candidateStack.setTag(parsedNbt.copy());
        }
        ValidationResult categoryResult = validateCategory(slot, candidateStack, itemLocation, filterConfig);
        if (!categoryResult.success()) {
            return fail(playerData, weaponSlot, categoryResult.code(), categoryResult.message());
        }

        String canonicalNbt = parsedNbt.isEmpty() ? "" : parsedNbt.toString();
        BackpackConfig.Backpack.ItemData previousItem = backpack.getItem(slot);
        backpack.setItem(slot, new BackpackConfig.Backpack.ItemData(itemId, 1, canonicalNbt));
        try {
            BackpackConfigRepository.save();
        } catch (BackpackConfigRepository.RepositoryException failure) {
            if (previousItem == null) {
                backpack.getItem_MAP().remove(slot.key());
            } else {
                backpack.setItem(slot, previousItem);
            }
            throw failure;
        }

        return new Result(playerData, true, weaponSlot, "OK", "");
    }

    private static Result fail(BackpackConfig.PlayerBackpackData playerData, String slot, String code, String message) {
        return new Result(playerData, false, slot, code, message);
    }

    private static ValidationResult validateThrowableAvailability(BackpackSlot slot, WeaponFilterConfig filterConfig) {
        if (!slot.isEquipmentSlot()) {
            return ValidationResult.ok();
        }
        if (!LrTacticalGatewayProvider.gateway().isLoaded()) {
            return ValidationResult.fail("THROWABLES_UNAVAILABLE", "");
        }
        if (filterConfig != null && !filterConfig.isThrowablesEnabled()) {
            return ValidationResult.fail("THROWABLES_DISABLED", "");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateCategory(BackpackSlot slot, ItemStack candidateStack,
            ResourceLocation itemResourceLocation, WeaponFilterConfig filterConfig) {
        if (filterConfig == null) {
            filterConfig = new WeaponFilterConfig();
        }

        if (BackpackNamespaceFilter.isBlocked(filterConfig, candidateStack, itemResourceLocation)) {
            return ValidationResult.fail("ITEM_BLOCKED", "");
        }

        if (slot.isGunSlot()) {
            Optional<String> category = TaczGatewayProvider.gateway().resolveGunType(candidateStack);
            List<String> allowedTypes = filterConfig.getGunTabs(slot == BackpackSlot.PRIMARY);
            if (category.isEmpty() || !allowedTypes.contains(category.get())) {
                return ValidationResult.fail("ITEM_CATEGORY_INVALID", "");
            }
            if (BackpackAttachmentFilter.findFirstBlockedInstalledAttachmentId(filterConfig, candidateStack).isPresent()) {
                return ValidationResult.fail("ATTACHMENT_BLOCKED", "");
            }
            return ValidationResult.ok();
        }

        CompoundTag nbtTag = candidateStack.getTag();
        String idTag = slot == BackpackSlot.MELEE ? MELEE_WEAPON_ID_TAG : THROWABLE_ID_TAG;
        if (nbtTag == null || !nbtTag.contains(idTag, Tag.TAG_STRING)) {
            return ValidationResult.fail("NBT_INVALID", "");
        }
        boolean matchesSlot = slot == BackpackSlot.MELEE
                ? LrTacticalGatewayProvider.gateway().isMeleeWeapon(candidateStack)
                : LrTacticalGatewayProvider.gateway().isThrowableStack(candidateStack);
        return matchesSlot ? ValidationResult.ok() : ValidationResult.fail("ITEM_CATEGORY_INVALID", "");
    }

    private static CompoundTag parseNbt(String rawNbt) throws Exception {
        if (rawNbt == null || rawNbt.isBlank()) {
            return new CompoundTag();
        }
        return TagParser.parseTag(rawNbt);
    }
}
