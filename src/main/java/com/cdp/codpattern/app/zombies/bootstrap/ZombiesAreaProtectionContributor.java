package com.cdp.codpattern.app.zombies.bootstrap;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.extension.ModeAreaProtectionContributor;
import com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

/** Zombies room-area drop and toss protection policy. */
public final class ZombiesAreaProtectionContributor implements ModeAreaProtectionContributor {
    @Override
    public String id() {
        return "zombies.area_protection";
    }

    @Override
    public boolean suppressLivingDrops(Entity entity) {
        return isInZombiesRoomArea(entity == null ? null : entity.level(), entity);
    }

    @Override
    public boolean suppressExperienceDrop(Entity entity) {
        return isInZombiesRoomArea(entity == null ? null : entity.level(), entity);
    }

    @Override
    public boolean suppressItemToss(Entity player) {
        return isInZombiesRoomArea(player == null ? null : player.level(), player);
    }

    @Override
    public boolean suppressEntitySpawn(Level level, Entity entity) {
        return (entity instanceof ItemEntity || entity instanceof ExperienceOrb)
                && isInZombiesRoomArea(level, entity);
    }

    private static boolean isInZombiesRoomArea(Level level, Entity entity) {
        if (level == null || entity == null || level.isClientSide()) {
            return false;
        }
        for (BaseMap map : FpsMatchMapRegistry.listMaps(BuiltInGameModes.ZOMBIES)) {
            if (map == null || map.getMapArea() == null || map.getServerLevel() == null) {
                continue;
            }
            if (!map.getServerLevel().dimension().equals(level.dimension())) {
                continue;
            }
            if (map.getMapArea().isEntityInArea(entity)) {
                return true;
            }
        }
        return false;
    }
}
