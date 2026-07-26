package com.cdp.codpattern.app.match.extension;

import net.minecraft.world.level.block.state.BlockState;

/** Identifies blocks whose own use method already routes the interaction. */
public interface ModeObjectInteractionBypassContributor {
    String id();

    default int order() {
        return 0;
    }

    boolean handlesOwnUse(BlockState state);
}
