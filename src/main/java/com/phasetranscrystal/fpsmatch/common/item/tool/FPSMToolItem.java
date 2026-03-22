package com.phasetranscrystal.fpsmatch.common.item.tool;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public abstract class FPSMToolItem extends Item {
    public static final String TYPE_TAG = "SelectedType";
    public static final String MAP_TAG = "SelectedMap";
    public static final String TEAM_TAG = "SelectedTeam";

    protected FPSMToolItem(Properties properties) {
        super(properties);
    }

    protected static void setStringTag(ItemStack stack, String key, String value) {
        stack.getOrCreateTag().putString(key, value == null ? "" : value);
    }

    protected static String getStringTag(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(key);
    }

    protected static void removeTag(ItemStack stack, String key) {
        stack.getOrCreateTag().remove(key);
    }
}
