package com.cdp.codpattern.app.zombies.bootstrap;

import com.cdp.codpattern.app.match.runtime.debug.ModeDebugSnapshotContributors;
import com.cdp.codpattern.app.match.runtime.entity.ModeEntityReconciliationContributors;
import com.cdp.codpattern.app.match.runtime.object.ModeObjectInteractionBypassContributors;
import com.cdp.codpattern.app.match.runtime.player.ModePlayerLoginContributors;
import com.cdp.codpattern.app.match.runtime.protection.ModeAreaProtectionContributors;
import com.cdp.codpattern.app.match.runtime.tool.ModeHeldToolPreviewContributors;
import com.cdp.codpattern.app.zombies.model.ZombiesGameModeDefinitions;
import com.cdp.codpattern.app.zombies.service.ZombiesEntityReconciliationContributor;
import com.cdp.codpattern.app.zombies.service.ZombiesLoginRecoveryContributor;
import com.cdp.codpattern.common.block.CodPatternBlockRegister;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;

import java.util.concurrent.atomic.AtomicBoolean;

/** Addon-owned bootstrap callable directly by the current shim or a future addon entry point. */
public final class ZombiesBootstrap {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private ZombiesBootstrap() {
    }

    public static void install(IEventBus modEventBus) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        ZombiesGameModeDefinitions.registerDefaults();
        ModePlayerLoginContributors.register(new ZombiesLoginRecoveryContributor());
        ModeEntityReconciliationContributors.register(new ZombiesEntityReconciliationContributor());
        ModeObjectInteractionBypassContributors.register(new ZombiesObjectInteractionBypassContributor());
        ModeAreaProtectionContributors.register(new ZombiesAreaProtectionContributor());
        ModeDebugSnapshotContributors.register(new ZombiesDebugSnapshotContributor());
        ModeHeldToolPreviewContributors.register(new ZombiesHeldToolPreviewContributor());
        ZombiesNetworkPacketContributor.install();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ZombiesClientBootstrap::install);

        modEventBus.addListener(CodPatternBlockRegister::onBuildCreativeModeTabContents);
        modEventBus.addListener(ZombiesItemRegister::onBuildCreativeModeTabContents);
        CodPatternBlockRegister.BLOCKS.register(modEventBus);
        CodPatternBlockRegister.ITEMS.register(modEventBus);
        ZombiesItemRegister.ITEMS.register(modEventBus);
    }
}
