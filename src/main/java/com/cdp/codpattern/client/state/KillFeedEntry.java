package com.cdp.codpattern.client.state;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public record KillFeedEntry(String killerName, String victimName, ItemStack weaponStack, boolean blunder, float alpha) {
    public KillFeedEntry {
        killerName = sanitizeName(killerName);
        victimName = sanitizeName(victimName);
        weaponStack = weaponStack == null ? ItemStack.EMPTY : weaponStack.copy();
        alpha = Mth.clamp(alpha, 0.0f, 1.0f);
    }

    private static String sanitizeName(String name) {
        return name == null || name.isBlank()
                ? Component.translatable("common.codpattern.unknown_player").getString()
                : name;
    }
}
