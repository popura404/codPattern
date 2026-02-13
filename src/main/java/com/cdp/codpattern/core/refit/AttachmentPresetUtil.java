package com.cdp.codpattern.core.refit;

import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public class AttachmentPresetUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static CompoundTag buildPresetFromGun(ItemStack gunStack) {
        return TaczGatewayProvider.gateway().buildAttachmentPreset(gunStack);
    }

    public static void applyPresetToGun(ItemStack gunStack, CompoundTag preset) {
        TaczGatewayProvider.gateway().applyAttachmentPreset(gunStack, preset);
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
