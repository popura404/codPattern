package com.cdp.codpattern.app.match.extension;

import com.cdp.codpattern.app.match.model.GameModeDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Public, construction-time contribution unit for one installed mode module.
 * Implementations should be immutable singletons; Forge may construct mods in parallel.
 */
public interface ModeModule {
    ResourceLocation id();

    default int order() {
        return 0;
    }

    List<GameModeDefinition> definitions();

    default List<ModePlayerLoginContributor> playerLoginContributors() {
        return List.of();
    }

    default List<ModeEntityReconciliationContributor> entityReconciliationContributors() {
        return List.of();
    }

    default List<ModeObjectInteractionBypassContributor> objectInteractionBypassContributors() {
        return List.of();
    }

    default List<ModeAreaProtectionContributor> areaProtectionContributors() {
        return List.of();
    }

    default List<ModeDebugSnapshotContributor> debugSnapshotContributors() {
        return List.of();
    }

    default List<ModeHeldToolPreviewContributor> heldToolPreviewContributors() {
        return List.of();
    }
}
