package com.cdp.codpattern.client.input;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ThrowableKeyMappings {
    private static final String CATEGORY = "key.categories.codpattern";

    public static final KeyMapping THROWABLE_SLOT_ONE = new KeyMapping(
            "key.codpattern.throwable.slot_1",
            KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN,
            CATEGORY);
    public static final KeyMapping THROWABLE_SLOT_TWO = new KeyMapping(
            "key.codpattern.throwable.slot_2",
            KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN,
            CATEGORY);

    private ThrowableKeyMappings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(THROWABLE_SLOT_ONE);
        event.register(THROWABLE_SLOT_TWO);
    }

    public static KeyMapping get(int slotIndex) {
        return slotIndex == ThrowableInventoryService.SLOT_ONE ? THROWABLE_SLOT_ONE : THROWABLE_SLOT_TWO;
    }

    public static Component getBoundLabel(int slotIndex) {
        KeyMapping mapping = get(slotIndex);
        return mapping.isUnbound()
                ? Component.translatable("common.codpattern.unbound")
                : mapping.getTranslatedKeyMessage();
    }
}
