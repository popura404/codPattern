package com.cdp.codpattern.app.match.runtime.object;

import com.cdp.codpattern.app.match.extension.ModeObjectInteractionBypassContributor;
import com.cdp.codpattern.app.match.ModeModules;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only interaction-bypass route over the frozen mode catalog. */
public final class ModeObjectInteractionBypassContributors {
    private ModeObjectInteractionBypassContributors() {
    }

    public static boolean handlesOwnUse(BlockState state) {
        return state != null && ModeModules.catalog().objectInteractionBypassContributors().stream()
                .anyMatch(contributor -> contributor.handlesOwnUse(state));
    }
}
