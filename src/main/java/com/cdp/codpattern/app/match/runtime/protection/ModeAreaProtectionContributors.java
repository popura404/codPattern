package com.cdp.codpattern.app.match.runtime.protection;

import com.cdp.codpattern.app.match.extension.ModeAreaProtectionContributor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Deterministic routing for mode-owned area protection rules. */
public final class ModeAreaProtectionContributors {
    private static final List<ModeAreaProtectionContributor> CONTRIBUTORS = new ArrayList<>();

    private ModeAreaProtectionContributors() {
    }

    public static synchronized void register(ModeAreaProtectionContributor contributor) {
        Objects.requireNonNull(contributor, "contributor");
        if (CONTRIBUTORS.stream().anyMatch(existing -> existing.id().equals(contributor.id()))) {
            throw new IllegalStateException("Duplicate area protection contributor: " + contributor.id());
        }
        CONTRIBUTORS.add(contributor);
        CONTRIBUTORS.sort(Comparator.comparingInt(ModeAreaProtectionContributor::order)
                .thenComparing(ModeAreaProtectionContributor::id));
    }

    public static synchronized boolean suppressLivingDrops(Entity entity) {
        return CONTRIBUTORS.stream().anyMatch(contributor -> contributor.suppressLivingDrops(entity));
    }

    public static synchronized boolean suppressExperienceDrop(Entity entity) {
        return CONTRIBUTORS.stream().anyMatch(contributor -> contributor.suppressExperienceDrop(entity));
    }

    public static synchronized boolean suppressItemToss(Entity player) {
        return CONTRIBUTORS.stream().anyMatch(contributor -> contributor.suppressItemToss(player));
    }

    public static synchronized boolean suppressEntitySpawn(Level level, Entity entity) {
        return CONTRIBUTORS.stream().anyMatch(contributor -> contributor.suppressEntitySpawn(level, entity));
    }
}
