package com.cdp.codpattern.compat.taczaddon;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.item.AttachmentItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class TaczAddonRefitCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TACZ_ADDON_MODID = "taczaddon";
    private static final String COMBINED_ITEMS_TAG = "CombinedItems";
    private static final String GUN_SMITHING_MANAGER_CLASS = "com.mafuyu404.taczaddon.init.GunSmithingManager";
    private static volatile Method gunSmithingGetResultMethod;
    private static volatile boolean gunSmithingGetResultLookupAttempted = false;

    private TaczAddonRefitCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(TACZ_ADDON_MODID);
    }

    /**
     * tacz-addon 会基于 CombinedItems 构建虚拟库存并接管卸载逻辑。
     * 在背包改装会话中移除此标签，强制其走真实库存路径，避免虚拟库存满格导致卸载失败。
     */
    public static void sanitizeGunForBackpackRefitSession(ItemStack gunStack) {
        if (gunStack == null || gunStack.isEmpty()) {
            return;
        }
        if (!isLoaded()) {
            return;
        }
        CompoundTag tag = gunStack.getTag();
        if (tag == null || !tag.contains(COMBINED_ITEMS_TAG, Tag.TAG_LIST)) {
            return;
        }
        tag.remove(COMBINED_ITEMS_TAG);
        if (tag.isEmpty()) {
            gunStack.setTag(null);
        }
    }

    public static List<ItemStack> resolveBackpackRefitCandidates(Player player, ItemStack originalGunStack) {
        if (originalGunStack == null || originalGunStack.isEmpty() || !isLoaded()) {
            return List.of();
        }
        List<ItemStack> combinedItems = resolveCombinedAttachmentItems(originalGunStack);
        if (combinedItems.isEmpty()) {
            return snapshot(resolveAllAttachmentItems());
        }
        List<ItemStack> merged = new ArrayList<>(combinedItems.size() + 16);
        merged.addAll(combinedItems);
        merged.addAll(resolveAllAttachmentItems());
        return snapshot(merged);
    }

    private static List<ItemStack> resolveCombinedAttachmentItems(ItemStack gunStack) {
        Method method = resolveGunSmithingGetResultMethod();
        if (method == null) {
            return List.of();
        }
        try {
            Object value = method.invoke(null, gunStack.copy());
            if (!(value instanceof List<?> rawIds) || rawIds.isEmpty()) {
                return List.of();
            }
            List<ItemStack> result = new ArrayList<>();
            for (Object rawId : rawIds) {
                if (!(rawId instanceof String attachmentId) || attachmentId.isBlank()) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(attachmentId);
                if (id == null) {
                    continue;
                }
                result.add(AttachmentItemBuilder.create().setId(id).build());
            }
            return result;
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to resolve TaCZAddon smithing candidates", e);
            return List.of();
        }
    }

    private static List<ItemStack> resolveAllAttachmentItems() {
        List<ItemStack> result = new ArrayList<>();
        result.addAll(AttachmentItem.fillItemCategory(AttachmentType.SCOPE));
        result.addAll(AttachmentItem.fillItemCategory(AttachmentType.MUZZLE));
        result.addAll(AttachmentItem.fillItemCategory(AttachmentType.STOCK));
        result.addAll(AttachmentItem.fillItemCategory(AttachmentType.GRIP));
        result.addAll(AttachmentItem.fillItemCategory(AttachmentType.LASER));
        result.addAll(AttachmentItem.fillItemCategory(AttachmentType.EXTENDED_MAG));
        return result;
    }

    private static Method resolveGunSmithingGetResultMethod() {
        if (gunSmithingGetResultMethod != null) {
            return gunSmithingGetResultMethod;
        }
        if (gunSmithingGetResultLookupAttempted || !isLoaded()) {
            return null;
        }
        gunSmithingGetResultLookupAttempted = true;
        try {
            Class<?> clazz = Class.forName(GUN_SMITHING_MANAGER_CLASS);
            gunSmithingGetResultMethod = clazz.getMethod("getResult", ItemStack.class);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to resolve TaCZAddon GunSmithingManager#getResult", e);
            return null;
        }
        return gunSmithingGetResultMethod;
    }

    private static List<ItemStack> snapshot(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<ItemStack> snapshot = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            snapshot.add(item.copy());
        }
        return List.copyOf(snapshot);
    }
}
