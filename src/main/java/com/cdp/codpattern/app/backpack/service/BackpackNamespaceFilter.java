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
        List<String> blocked = filterConfig.getBlockedItemNamespaces();
        if (blocked == null || blocked.isEmpty()) {
            return false;
        }

        Optional<String> namespace = resolveNamespace(stack, fallbackItemId);
        if (namespace.isEmpty()) {
            return false;
        }
        String normalized = namespace.get().toLowerCase(Locale.ROOT);
        for (String value : blocked) {
            if (value != null && normalized.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> resolveNamespace(ItemStack stack, ResourceLocation ignoredFallbackItemId) {
        if (stack == null || stack.isEmpty() || !TaczGatewayProvider.gateway().isGun(stack)) {
            return Optional.empty();
        }
        Optional<String> gunId = TaczGatewayProvider.gateway().resolveGunId(stack);
        if (gunId.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation gunResource = ResourceLocation.tryParse(gunId.get());
        if (gunResource == null) {
            return Optional.empty();
        }
        return Optional.of(gunResource.getNamespace());
    }
}
