package com.cdp.codpattern.compat.taczaddon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

public final class TaczAddonRefitCompat {
    private static final String TACZ_ADDON_MODID = "taczaddon";
    private static final String COMBINED_ITEMS_TAG = "CombinedItems";

    private TaczAddonRefitCompat() {
    }

    /**
     * tacz-addon 会基于 CombinedItems 构建虚拟库存并接管卸载逻辑。
     * 在背包改装会话中移除此标签，强制其走真实库存路径，避免虚拟库存满格导致卸载失败。
     */
    public static void sanitizeGunForBackpackRefitSession(ItemStack gunStack) {
        if (gunStack == null || gunStack.isEmpty()) {
            return;
        }
        if (!ModList.get().isLoaded(TACZ_ADDON_MODID)) {
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
}
