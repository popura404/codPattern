package com.cdp.codpattern.compat.arcana;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Applies pure configuration changes without dispatching Arcana gameplay skills. */
public final class ArcanaGunFormCompat {
    private ArcanaGunFormCompat() { }

    /** TaCZ's gameplay getters hide disabled slots; configuration must retain their stored items. */
    public static ItemStack readStoredAttachment(ItemStack gun, AttachmentType type) {
        String key = GunItemDataAccessor.GUN_ATTACHMENT_BASE + type.name();
        return gun.hasTag() && gun.getTag().contains(key, Tag.TAG_COMPOUND)
                ? ItemStack.of(gun.getTag().getCompound(key)) : ItemStack.EMPTY;
    }

    /** Exact TaCZ 1.1.8 representation, used only after preset validation or blacklist removal. */
    public static void writeStoredAttachment(ItemStack gun, AttachmentType type, ItemStack attachment) {
        gun.getOrCreateTag().put(GunItemDataAccessor.GUN_ATTACHMENT_BASE + type.name(),
                attachment.save(new CompoundTag()));
    }

    public static final class FormException extends IllegalStateException {
        private final boolean unsupported;

        private FormException(ArcanaGunFormRules.Result result) {
            super(result.originalGunId() + ": " + result.reason());
            unsupported = result.status() == ArcanaGunFormRules.Status.UNSUPPORTED;
        }

        public String messageKey() {
            return unsupported ? "operation_arcana_form_unsupported" : "operation_arcana_unavailable";
        }
    }

    /** Reject unsupported transitions before the real executor can change ammunition or attachments. */
    public static void requireSupportedChange(ItemStack gun, ItemStack candidate, AttachmentType type, boolean install) {
        ItemStack projected = gun.copy();
        IGun api = IGun.getIGunOrNull(projected);
        if (install) api.installAttachment(projected, candidate.copy());
        else api.unloadAttachment(projected, type);
        requireResolved(ArcanaGunFormRules.inspect(projected));
    }

    public static void requireResolved(ArcanaGunFormRules.Result result) {
        if (!result.isResolved()) throw new FormException(result);
    }

    /** Clients keep the server-persisted form; only a server with ready rules can derive a new one. */
    public static ArcanaGunFormRules.Result normalize(ItemStack gun) {
        if (!ArcanaRefitCompat.isLoaded()) return ArcanaGunFormRules.inspect(gun);
        Object previousUnderbarrel = ArcanaRefitCompat.captureUnderbarrel(gun);
        ArcanaGunFormRules.Result result = ArcanaGunFormRules.normalize(gun);
        if (result.status() == ArcanaGunFormRules.Status.CHANGED) {
            ArcanaRefitCompat.resetUnderbarrelAfterPreset(gun, previousUnderbarrel);
        }
        return result;
    }
}
