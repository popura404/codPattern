package com.cdp.codpattern.app.zombies.bootstrap;

import com.cdp.codpattern.app.match.extension.ModeObjectInteractionBypassContributor;
import com.cdp.codpattern.common.block.ZombiesBoxInteractionBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps Zombies box blocks on their existing Block.use entry path. */
public final class ZombiesObjectInteractionBypassContributor implements ModeObjectInteractionBypassContributor {
    @Override
    public String id() {
        return "zombies.box_block_use";
    }

    @Override
    public boolean handlesOwnUse(BlockState state) {
        return state != null && state.getBlock() instanceof ZombiesBoxInteractionBlock;
    }
}
