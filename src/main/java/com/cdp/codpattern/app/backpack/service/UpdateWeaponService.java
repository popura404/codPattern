package com.cdp.codpattern.app.backpack.service;

import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
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
import java.util.Set;

public final class UpdateWeaponService {
    private static final Set<String> ALLOWED_SLOTS = Set.of("primary", "secondary", "tactical", "lethal");
    private static final String THROWABLE_ITEM_ID = "lrtactical:throwable";
    private static final String THROWABLE_ID_TAG = "ThrowableId";
    private static final String MELEE_ITEM_ID = "lrtactical:melee";
    private static final String MELEE_WEAPON_ID_TAG = "MeleeWeaponId";
    private static final String MELEE_TAB = "melee";
    private static final String LR_MELEE_INTERFACE = "me.xjqsh.lrtactical.api.item.IMeleeWeapon";

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

        ValidationResult slotResult = validateSlot(weaponSlot);
        if (!slotResult.success()) {
            return fail(playerData, weaponSlot, slotResult.code(), slotResult.message());
        }

        ValidationResult itemResult = validateItem(itemId);
        if (!itemResult.success()) {
            return fail(playerData, weaponSlot, itemResult.code(), itemResult.message());
        }

        CompoundTag parsedNbt;
        try {
            parsedNbt = parseNbt(nbt);
        } catch (Exception e) {
            return fail(playerData, weaponSlot, "NBT_INVALID", "");
        }

        WeaponFilterConfig filterConfig = WeaponFilterConfigRepository.loadOrCreate(filterPath);
        ValidationResult categoryResult = validateCategory(weaponSlot, itemId, parsedNbt, filterConfig);
        if (!categoryResult.success()) {
            return fail(playerData, weaponSlot, categoryResult.code(), categoryResult.message());
        }

        backpack.getItem_MAP().put(weaponSlot, new BackpackConfig.Backpack.ItemData(itemId, 1, nbt));
        BackpackConfigRepository.save();

        return new Result(playerData, true, weaponSlot, "OK", "");
    }

    private static Result fail(BackpackConfig.PlayerBackpackData playerData, String slot, String code, String message) {
        return new Result(playerData, false, slot, code, message);
    }

    private static ValidationResult validateSlot(String slot) {
        if (slot == null || !ALLOWED_SLOTS.contains(slot)) {
            return ValidationResult.fail("SLOT_INVALID", "");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateItem(String itemId) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(itemId);
        if (resourceLocation == null) {
            return ValidationResult.fail("ITEM_ID_INVALID", "");
        }
        if (resolveRegisteredItem(resourceLocation) == null) {
            return ValidationResult.fail("ITEM_NOT_REGISTERED", "");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateCategory(String slot,
            String itemId,
            CompoundTag nbtTag,
            WeaponFilterConfig filterConfig) {
        if (filterConfig == null) {
            filterConfig = new WeaponFilterConfig();
        }

        ResourceLocation itemResourceLocation = ResourceLocation.tryParse(itemId);
        Item item = itemResourceLocation == null ? null : resolveRegisteredItem(itemResourceLocation);
        if (item == null) {
            return ValidationResult.fail("ITEM_NOT_REGISTERED", "");
        }

        ItemStack candidateStack = new ItemStack(item, 1);
        if (nbtTag != null && !nbtTag.isEmpty()) {
            candidateStack.setTag(nbtTag.copy());
        }
        if (BackpackNamespaceFilter.isBlocked(filterConfig, candidateStack, itemResourceLocation)) {
            return ValidationResult.fail("ITEM_NAMESPACE_BLOCKED", "");
        }

        if ("primary".equals(slot) || "secondary".equals(slot)) {
            Optional<String> weaponCategoryOpt = resolveWeaponCategory(itemId, candidateStack, nbtTag);
            if (weaponCategoryOpt.isEmpty()) {
                return ValidationResult.fail("ITEM_CATEGORY_INVALID", "");
            }
            String weaponCategory = weaponCategoryOpt.get();
            List<String> allowedTypes = "primary".equals(slot)
                    ? filterConfig.getPrimaryWeaponTabs()
                    : filterConfig.getSecondaryWeaponTabs();
            if (allowedTypes != null && !allowedTypes.isEmpty() && !allowedTypes.contains(weaponCategory)) {
                return ValidationResult.fail("ITEM_CATEGORY_INVALID", "");
            }
            return ValidationResult.ok();
        }

        if (!filterConfig.isThrowablesEnabled()) {
            return ValidationResult.fail("THROWABLES_DISABLED", "");
        }
        if (!THROWABLE_ITEM_ID.equals(itemId)) {
            return ValidationResult.fail("ITEM_CATEGORY_INVALID", "");
        }
        if (nbtTag == null || !nbtTag.contains(THROWABLE_ID_TAG, Tag.TAG_STRING)) {
            return ValidationResult.fail("NBT_INVALID", "");
        }
        return ValidationResult.ok();
    }

    private static Optional<String> resolveWeaponCategory(String itemId, ItemStack weaponStack, CompoundTag nbtTag) {
        Optional<String> gunTypeOpt = TaczGatewayProvider.gateway().resolveGunType(weaponStack);
        if (gunTypeOpt.isPresent()) {
            return gunTypeOpt;
        }

        if (isLrTacticalMelee(itemId, weaponStack, nbtTag)) {
            return Optional.of(MELEE_TAB);
        }
        return Optional.empty();
    }

    private static boolean isLrTacticalMelee(String itemId, ItemStack stack, CompoundTag nbtTag) {
        if (nbtTag == null || !nbtTag.contains(MELEE_WEAPON_ID_TAG, Tag.TAG_STRING)) {
            return false;
        }
        if (MELEE_ITEM_ID.equals(itemId)) {
            return true;
        }
        return implementsInterface(stack.getItem().getClass(), LR_MELEE_INTERFACE);
    }

    private static boolean implementsInterface(Class<?> type, String interfaceName) {
        if (type == null) {
            return false;
        }
        for (Class<?> iface : type.getInterfaces()) {
            if (interfaceName.equals(iface.getName()) || implementsInterface(iface, interfaceName)) {
                return true;
            }
        }
        return implementsInterface(type.getSuperclass(), interfaceName);
    }

    private static Item resolveRegisteredItem(ResourceLocation resourceLocation) {
        Item item = BuiltInRegistries.ITEM.get(resourceLocation);
        if (item == null || item == Items.AIR) {
            return null;
        }
        return item;
    }

    private static CompoundTag parseNbt(String rawNbt) throws Exception {
        if (rawNbt == null || rawNbt.isBlank()) {
            return new CompoundTag();
        }
        return TagParser.parseTag(rawNbt);
    }
}
