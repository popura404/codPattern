package com.cdp.codpattern.compat.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class TaczCoreGateway implements TaczGateway {
    @Override
    public boolean isGun(ItemStack stack) {
        return resolveGun(stack).isPresent();
    }

    @Override
    public boolean isValidGun(ItemStack stack) {
        return resolveGunIndex(stack).isPresent();
    }

    @Override
    public Optional<String> resolveGunType(ItemStack stack) {
        return resolveGunIndex(stack).map(CommonGunIndex::getType);
    }

    @Override
    public Optional<String> resolveGunId(ItemStack stack) {
        return resolveGun(stack)
                .map(iGun -> iGun.getGunId(stack))
                .filter(id -> id != null)
                .map(ResourceLocation::toString);
    }

    @Override
    public boolean canAttach(ItemStack gunStack, ItemStack attachmentStack) {
        if (attachmentStack == null || attachmentStack.isEmpty()) {
            return false;
        }
        return resolveGun(gunStack)
                .map(iGun -> iGun.allowAttachment(gunStack, attachmentStack))
                .orElse(false);
    }

    @Override
    public CompoundTag buildAttachmentPreset(ItemStack gunStack) {
        CompoundTag preset = new CompoundTag();
        Optional<IGun> iGunOpt = resolveGun(gunStack);
        if (iGunOpt.isEmpty()) {
            return preset;
        }
        IGun iGun = iGunOpt.get();

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

    @Override
    public void applyAttachmentPreset(ItemStack gunStack, CompoundTag preset) {
        Optional<IGun> iGunOpt = resolveGun(gunStack);
        if (iGunOpt.isEmpty() || preset == null || preset.isEmpty()) {
            return;
        }
        IGun iGun = iGunOpt.get();

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

    @Override
    public void postAttachmentChanged(Player player, ItemStack gunStack) {
        if (player == null || !isGun(gunStack)) {
            return;
        }
        AttachmentPropertyManager.postChangeEvent(player, gunStack);
    }

    @Override
    public void configureGunAmmo(ItemStack stack, int ammoMultiple) {
        Optional<IGun> iGunOpt = resolveGun(stack);
        if (iGunOpt.isEmpty()) {
            return;
        }
        IGun iGun = iGunOpt.get();

        Optional<CommonGunIndex> gunIndex = resolveGunIndex(stack);
        int currentAmmo = Math.max(0, iGun.getCurrentAmmoCount(stack));
        int magazineAmmo = gunIndex
                .map(index -> Math.max(0, AttachmentDataUtils.getAmmoCountWithAttachment(stack, index.getGunData())))
                .orElse(currentAmmo);
        if (magazineAmmo <= 0) {
            magazineAmmo = currentAmmo;
        }
        int normalizedMagazineAmmo = Math.max(1, magazineAmmo);
        iGun.setCurrentAmmoCount(stack, normalizedMagazineAmmo);

        Bolt boltType = gunIndex.map(index -> index.getGunData().getBolt()).orElse(null);
        if (boltType == Bolt.OPEN_BOLT) {
            iGun.setBulletInBarrel(stack, false);
        } else {
            iGun.setBulletInBarrel(stack, true);
        }

        int safeMultiple = Math.max(0, ammoMultiple);
        int reserveAmmo = Math.max(0, normalizedMagazineAmmo * safeMultiple);
        if (reserveAmmo <= 0 && safeMultiple > 0) {
            reserveAmmo = safeMultiple;
        }
        iGun.setMaxDummyAmmoAmount(stack, reserveAmmo);
        iGun.setDummyAmmoAmount(stack, reserveAmmo);
    }

    private static Optional<IGun> resolveGun(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(IGun.getIGunOrNull(stack));
    }

    private static Optional<CommonGunIndex> resolveGunIndex(ItemStack stack) {
        Optional<IGun> iGunOpt = resolveGun(stack);
        if (iGunOpt.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation gunId = iGunOpt.get().getGunId(stack);
        if (gunId == null) {
            return Optional.empty();
        }
        return TimelessAPI.getCommonGunIndex(gunId);
    }
}
