package com.cdp.codpattern.app.match.runtime.protection;

import com.cdp.codpattern.app.match.extension.ModeAreaProtectionContributor;
import com.cdp.codpattern.app.match.ModeModules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Read-only area-protection route over the frozen mode catalog. */
public final class ModeAreaProtectionContributors {
    private ModeAreaProtectionContributors() {
    }

    public static boolean suppressLivingDrops(Entity entity) {
        return ModeModules.catalog().areaProtectionContributors().stream()
                .anyMatch(contributor -> contributor.suppressLivingDrops(entity));
    }

    public static boolean suppressExperienceDrop(Entity entity) {
        return ModeModules.catalog().areaProtectionContributors().stream()
                .anyMatch(contributor -> contributor.suppressExperienceDrop(entity));
    }

    public static boolean suppressItemToss(Entity player) {
        return ModeModules.catalog().areaProtectionContributors().stream()
                .anyMatch(contributor -> contributor.suppressItemToss(player));
    }

    public static boolean suppressEntitySpawn(Level level, Entity entity) {
        return ModeModules.catalog().areaProtectionContributors().stream()
                .anyMatch(contributor -> contributor.suppressEntitySpawn(level, entity));
    }
}
