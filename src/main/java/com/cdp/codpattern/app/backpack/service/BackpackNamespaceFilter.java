package com.cdp.codpattern.app.backpack.service;

import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class BackpackNamespaceFilter {
    private BackpackNamespaceFilter() {
    }

    public static boolean isBlocked(WeaponFilterConfig filterConfig, ItemStack stack, ResourceLocation fallbackItemId) {
        if (filterConfig == null) {
            return false;
        }
        Optional<ResourceLocation> gunId = resolveGunId(stack, fallbackItemId);
        if (gunId.isEmpty()) {
            return false;
        }
        ResourceLocation resolvedGunId = gunId.get();
        return isNamespaceBlocked(filterConfig.getBlockedItemNamespaces(), resolvedGunId)
                || isWeaponIdBlocked(filterConfig.getBlockedWeaponIds(), resolvedGunId);
    }

    private static boolean isNamespaceBlocked(List<String> blockedNamespaces, ResourceLocation gunId) {
        if (blockedNamespaces == null || blockedNamespaces.isEmpty()) {
            return false;
        }
        String normalizedNamespace = gunId.getNamespace().toLowerCase(Locale.ROOT);
        for (String value : blockedNamespaces) {
            if (value != null && normalizedNamespace.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWeaponIdBlocked(List<String> blockedWeaponIds, ResourceLocation gunId) {
        if (blockedWeaponIds == null || blockedWeaponIds.isEmpty()) {
            return false;
        }
        String normalizedGunId = gunId.toString().toLowerCase(Locale.ROOT);
        for (String value : blockedWeaponIds) {
            if (value != null && normalizedGunId.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static Optional<ResourceLocation> resolveGunId(ItemStack stack, ResourceLocation ignoredFallbackItemId) {
        if (stack == null || stack.isEmpty() || !TaczGatewayProvider.gateway().isGun(stack)) {
            return Optional.empty();
        }
        Optional<String> gunId = TaczGatewayProvider.gateway().resolveGunId(stack);
        if (gunId.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation gunResourceLocation = ResourceLocation.tryParse(gunId.get());
        if (gunResourceLocation == null) {
            return Optional.empty();
        }
        return Optional.of(gunResourceLocation);
    }
}
