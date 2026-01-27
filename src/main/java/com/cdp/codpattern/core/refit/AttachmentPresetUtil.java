package com.cdp.codpattern.core.refit;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public class AttachmentPresetUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static CompoundTag buildPresetFromGun(ItemStack gunStack) {
        CompoundTag preset = new CompoundTag();
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) {
            return preset;
        }
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                continue;
            }
            if (!iGun.allowAttachmentType(gunStack, type)) {
                continue;
            }
            String key = GunItemDataAccessor.GUN_ATTACHMENT_BASE + type.name();
            ItemStack attachment = iGun.getAttachment(gunStack, type);
            CompoundTag attachmentTag = new CompoundTag();
            if (attachment.isEmpty()) {
                ItemStack.EMPTY.save(attachmentTag);
            } else {
                attachment.save(attachmentTag);
            }
            preset.put(key, attachmentTag);
        }
        if (iGun.hasCustomLaserColor(gunStack)) {
            preset.putInt(GunItemDataAccessor.LASER_COLOR_TAG, iGun.getLaserColor(gunStack));
        }
        return preset;
    }

    public static void applyPresetToGun(ItemStack gunStack, CompoundTag preset) {
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) {
            return;
        }
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                continue;
            }
            if (!iGun.allowAttachmentType(gunStack, type)) {
                continue;
            }
            String key = GunItemDataAccessor.GUN_ATTACHMENT_BASE + type.name();
            if (!preset.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }
            ItemStack attachment = ItemStack.of(preset.getCompound(key));
            if (attachment.isEmpty()) {
                iGun.unloadAttachment(gunStack, type);
            } else if (iGun.allowAttachment(gunStack, attachment)) {
                iGun.installAttachment(gunStack, attachment);
            }
        }
        if (preset.contains(GunItemDataAccessor.LASER_COLOR_TAG, Tag.TAG_INT)) {
            iGun.setLaserColor(gunStack, preset.getInt(GunItemDataAccessor.LASER_COLOR_TAG));
        }
    }

    public static CompoundTag parsePresetString(String payload) {
        if (payload == null || payload.isBlank()) {
            return new CompoundTag();
        }
        try {
            return TagParser.parseTag(payload);
        } catch (CommandSyntaxException e) {
            LOGGER.warn("Failed to parse attachment preset payload", e);
            return new CompoundTag();
        }
    }
}
