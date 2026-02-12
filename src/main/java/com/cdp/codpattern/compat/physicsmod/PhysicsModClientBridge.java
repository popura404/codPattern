package com.cdp.codpattern.compat.physicsmod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

public final class PhysicsModClientBridge {
    private PhysicsModClientBridge() {
    }

    public static boolean isPhysicsModLoaded() {
        return ModList.get().isLoaded("physicsmod");
    }

    public static void blockifySnapshot(Level level, Player snapshot) {
        if (RenderSystem.isOnRenderThread()) {
            invokeBlockify(level, snapshot);
        } else {
            RenderSystem.recordRenderCall(() -> invokeBlockify(level, snapshot));
        }
    }

    private static void invokeBlockify(Level level, Player snapshot) {
        try {
            Class<?> physicsMod = Class.forName("net.diebuddies.physics.PhysicsMod");
            Method blockify = physicsMod.getMethod("blockifyEntity", Level.class,
                    net.minecraft.world.entity.LivingEntity.class);
            blockify.invoke(null, level, snapshot);
        } catch (Throwable ignored) {
            // physicsmod 不存在或触发失败时静默降级，不影响主流程
        }
    }
}
