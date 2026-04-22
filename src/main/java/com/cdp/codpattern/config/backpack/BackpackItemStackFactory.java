package com.cdp.codpattern.config.backpack;

import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.core.refit.AttachmentPresetUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BackpackItemStackFactory {
    private BackpackItemStackFactory() {
    }

    public static ItemStack create(BackpackConfig.Backpack.ItemData itemData) {
        if (itemData == null || itemData.getItem() == null || itemData.getItem().isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            ResourceLocation itemId = ResourceLocation.tryParse(itemData.getItem());
            if (itemId == null) {
                return ItemStack.EMPTY;
            }

            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }

            ItemStack stack = new ItemStack(item, Math.max(1, itemData.getCount()));
            String nbt = itemData.getNbt();
            if (nbt != null && !nbt.isBlank()) {
                stack.setTag(TagParser.parseTag(nbt));
            }

            String attachmentPreset = itemData.getAttachmentPreset();
            if (attachmentPreset != null
                    && !attachmentPreset.isBlank()
                    && TaczGatewayProvider.gateway().isGun(stack)) {
                CompoundTag presetTag = AttachmentPresetUtil.parsePresetString(attachmentPreset);
                if (!presetTag.isEmpty()) {
                    AttachmentPresetUtil.applyPresetToGun(stack, presetTag);
                }
            }

            return stack;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
