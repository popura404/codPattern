package com.cdp.codpattern.client.refit;

import com.cdp.codpattern.core.refit.RefitAction;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Keeps pending slider edits visible over an ACK without applying them to a not-yet-installed attachment. */
public final class RefitLaserPreview {
    private RefitLaserPreview() { }

    public static boolean sameTarget(RefitAction previous, RefitAction next) {
        return previous.kind() == RefitAction.Kind.LASER && next.kind() == RefitAction.Kind.LASER
                && previous.gunSlot() == next.gunSlot() && previous.applyGunColor() == next.applyGunColor()
                && previous.laserColors().keySet().equals(next.laserColors().keySet());
    }

    public static void applyPending(ItemStack gun, List<RefitAction> pending) {
        IGun api = IGun.getIGunOrNull(gun);
        if (api == null) return;
        for (RefitAction action : pending) {
            // A later color belongs to the gun state after this operation, which its ACK will supply.
            if (action.kind() != RefitAction.Kind.LASER) break;
            for (var color : action.laserColors().entrySet()) {
                AttachmentType type = AttachmentType.valueOf(color.getKey());
                ItemStack attachment = api.getAttachment(gun, type);
                IAttachment attachmentApi = IAttachment.getIAttachmentOrNull(attachment);
                if (attachmentApi != null) {
                    attachmentApi.setLaserColor(attachment, color.getValue());
                    api.installAttachment(gun, attachment);
                }
            }
            if (action.applyGunColor()) api.setLaserColor(gun, action.gunColor());
        }
    }
}
