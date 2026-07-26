package com.cdp.codpattern.app.zombies.bootstrap;

import com.cdp.codpattern.app.match.extension.ModeHeldToolPreviewContributor;
import com.phasetranscrystal.fpsmatch.common.item.ZombiesDeployTool;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Zombies deploy-tool preview lifecycle contribution. */
public final class ZombiesHeldToolPreviewContributor implements ModeHeldToolPreviewContributor {
    @Override
    public String id() {
        return "zombies.deploy_tool";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public boolean matches(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ZombiesDeployTool;
    }

    @Override
    public void sync(ServerPlayer player, ItemStack stack) {
        ((ZombiesDeployTool) stack.getItem()).syncHeldPreview(player, stack);
    }

    @Override
    public void clear(ServerPlayer player) {
        ZombiesDeployTool.clearHeldPreview(player);
    }
}
