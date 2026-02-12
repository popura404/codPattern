package com.cdp.codpattern.app.backpack.service;

import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class UpdateWeaponService {
    private static final Set<String> ALLOWED_SLOTS = Set.of("primary", "secondary", "tactical", "lethal");
    private static final String THROWABLE_ITEM_ID = "lrtactical:throwable";

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
            return fail(playerData, weaponSlot, "BAG_NOT_FOUND", "目标背包不存在");
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
            return fail(playerData, weaponSlot, "NBT_INVALID", "NBT 解析失败");
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
            return ValidationResult.fail("SLOT_INVALID", "非法槽位");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateItem(String itemId) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(itemId);
        if (resourceLocation == null) {
            return ValidationResult.fail("ITEM_ID_INVALID", "物品 ID 非法");
        }
        Item item = ForgeRegistries.ITEMS.getValue(resourceLocation);
        if (item == null) {
            return ValidationResult.fail("ITEM_NOT_REGISTERED", "物品未注册");
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

        ResourceLocation itemRl = ResourceLocation.tryParse(itemId);
        Item item = itemRl == null ? null : ForgeRegistries.ITEMS.getValue(itemRl);
        if (item == null) {
            return ValidationResult.fail("ITEM_NOT_REGISTERED", "物品未注册");
        }

        if ("primary".equals(slot) || "secondary".equals(slot)) {
            ItemStack gunStack = new ItemStack(item, 1);
            if (nbtTag != null && !nbtTag.isEmpty()) {
                gunStack.setTag(nbtTag.copy());
            }
            IGun iGun = IGun.getIGunOrNull(gunStack);
            if (iGun == null) {
                return ValidationResult.fail("ITEM_CATEGORY_INVALID", "该槽位仅允许枪械");
            }
            ResourceLocation gunId = iGun.getGunId(gunStack);
            if (gunId == null || TimelessAPI.getCommonGunIndex(gunId).isEmpty()) {
                return ValidationResult.fail("ITEM_CATEGORY_INVALID", "枪械数据无效");
            }
            String gunType = TimelessAPI.getCommonGunIndex(gunId).get().getType();
            List<String> allowedTypes = "primary".equals(slot)
                    ? filterConfig.getPrimaryWeaponTabs()
                    : filterConfig.getSecondaryWeaponTabs();
            if (allowedTypes != null && !allowedTypes.isEmpty() && !allowedTypes.contains(gunType)) {
                return ValidationResult.fail("ITEM_CATEGORY_INVALID", "该武器分类不允许写入此槽位");
            }
            return ValidationResult.ok();
        }

        if (!filterConfig.isThrowablesEnabled()) {
            return ValidationResult.fail("THROWABLES_DISABLED", "当前配置禁用投掷物槽位");
        }
        if (!THROWABLE_ITEM_ID.equals(itemId)) {
            return ValidationResult.fail("ITEM_CATEGORY_INVALID", "投掷物槽位物品非法");
        }
        if (nbtTag == null || !nbtTag.contains("ThrowableId", Tag.TAG_STRING)) {
            return ValidationResult.fail("NBT_INVALID", "投掷物 NBT 缺少 ThrowableId");
        }
        return ValidationResult.ok();
    }

    private static CompoundTag parseNbt(String rawNbt) throws Exception {
        if (rawNbt == null || rawNbt.isBlank()) {
            return new CompoundTag();
        }
        return TagParser.parseTag(rawNbt);
    }
}
