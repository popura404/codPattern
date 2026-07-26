package com.cdp.codpattern.app.match.extension;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Mode-owned protection rules for drops, tosses, and spawned drop entities inside mode areas. */
public interface ModeAreaProtectionContributor {
    String id();

    default int order() {
        return 0;
    }

    default boolean suppressLivingDrops(Entity entity) {
        return false;
    }

    default boolean suppressExperienceDrop(Entity entity) {
        return false;
    }

    default boolean suppressItemToss(Entity player) {
        return false;
    }

    default boolean suppressEntitySpawn(Level level, Entity entity) {
        return false;
    }
}
