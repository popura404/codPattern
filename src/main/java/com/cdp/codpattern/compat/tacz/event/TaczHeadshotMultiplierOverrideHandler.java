package com.cdp.codpattern.compat.tacz.event;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keep TaCZ's original headshot multiplier when other mods overwrite it.
 */
@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TaczHeadshotMultiplierOverrideHandler {
    private static final float FPSMATCH_HEADSHOT_MULTIPLIER = 4.0f;
    private static final float EPSILON = 0.0001f;
    private static final Map<EntityHurtByGunEvent.Pre, Float> ORIGINAL_HEADSHOT_MULTIPLIERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private TaczHeadshotMultiplierOverrideHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void captureOriginalHeadshotMultiplier(EntityHurtByGunEvent.Pre event) {
        if (!shouldTrack(event)) {
            return;
        }
        ORIGINAL_HEADSHOT_MULTIPLIERS.put(event, event.getHeadshotMultiplier());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void restoreOriginalHeadshotMultiplier(EntityHurtByGunEvent.Pre event) {
        Float original = ORIGINAL_HEADSHOT_MULTIPLIERS.remove(event);
        if (original == null) {
            return;
        }
        float current = event.getHeadshotMultiplier();
        if (Math.abs(current - FPSMATCH_HEADSHOT_MULTIPLIER) < EPSILON) {
            event.setHeadshotMultiplier(original);
        }
    }

    private static boolean shouldTrack(EntityHurtByGunEvent.Pre event) {
        return event.isHeadShot() && event.getHurtEntity() instanceof ServerPlayer;
    }
}
