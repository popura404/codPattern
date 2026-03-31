package com.cdp.codpattern.app.backpack.service;

import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class BackpackAttachmentFilter {
    private BackpackAttachmentFilter() {
    }

    public static boolean isAttachmentBlocked(WeaponFilterConfig filterConfig, ItemStack attachmentStack) {
        return resolveBlockedAttachmentId(
                filterConfig,
                TaczGatewayProvider.gateway().resolveAttachmentId(attachmentStack).stream()).isPresent();
    }

    public static Optional<ResourceLocation> findFirstBlockedInstalledAttachmentId(WeaponFilterConfig filterConfig,
            ItemStack gunStack) {
        return resolveBlockedAttachmentId(
                filterConfig,
                TaczGatewayProvider.gateway().resolveInstalledAttachmentIds(gunStack).stream());
    }

    public static boolean removeBlockedInstalledAttachments(WeaponFilterConfig filterConfig, ItemStack gunStack) {
        if (filterConfig == null || gunStack == null || gunStack.isEmpty()) {
            return false;
        }
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) {
            return false;
        }

        boolean changed = false;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE || !iGun.allowAttachmentType(gunStack, type)) {
                continue;
            }
            ItemStack attachmentStack = iGun.getAttachment(gunStack, type);
            if (attachmentStack.isEmpty() || !isAttachmentBlocked(filterConfig, attachmentStack)) {
                continue;
            }
            iGun.unloadAttachment(gunStack, type);
            changed = true;
        }
        return changed;
    }

    private static Optional<ResourceLocation> resolveBlockedAttachmentId(WeaponFilterConfig filterConfig,
            java.util.stream.Stream<String> attachmentIdStream) {
        if (filterConfig == null) {
            return Optional.empty();
        }

        return attachmentIdStream
                .map(ResourceLocation::tryParse)
                .filter(java.util.Objects::nonNull)
                .filter(attachmentId -> isNamespaceBlocked(filterConfig.getBlockedAttachmentNamespaces(), attachmentId)
                        || isAttachmentIdBlocked(filterConfig.getBlockedAttachmentIds(), attachmentId))
                .findFirst();
    }

    private static boolean isNamespaceBlocked(List<String> blockedNamespaces, ResourceLocation attachmentId) {
        if (blockedNamespaces == null || blockedNamespaces.isEmpty()) {
            return false;
        }
        String normalizedNamespace = attachmentId.getNamespace().toLowerCase(Locale.ROOT);
        for (String value : blockedNamespaces) {
            if (value != null && normalizedNamespace.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAttachmentIdBlocked(List<String> blockedAttachmentIds, ResourceLocation attachmentId) {
        if (blockedAttachmentIds == null || blockedAttachmentIds.isEmpty()) {
            return false;
        }
        String normalizedAttachmentId = attachmentId.toString().toLowerCase(Locale.ROOT);
        for (String value : blockedAttachmentIds) {
            if (value != null && normalizedAttachmentId.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
