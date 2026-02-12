package com.cdp.codpattern.compat.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class TaczCoreGateway implements TaczGateway {
    @Override
    public boolean isGun(ItemStack stack) {
        return stack != null && stack.getItem() instanceof IGun;
    }

    @Override
    public void configureGunAmmo(ItemStack stack, int ammoMultiple) {
        if (!(stack.getItem() instanceof IGun iGun)) {
            return;
        }

        Optional<CommonGunIndex> gunIndex = TimelessAPI.getCommonGunIndex(iGun.getGunId(stack));
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
}
