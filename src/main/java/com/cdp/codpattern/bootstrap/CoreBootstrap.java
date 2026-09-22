package com.cdp.codpattern.bootstrap;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.match.ModeModules;
import com.cdp.codpattern.app.tdm.TdmModeModule;
import com.cdp.codpattern.client.bootstrap.CoreClientBootstrap;
import com.cdp.codpattern.command.CommandRegistration;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.phasetranscrystal.fpsmatch.common.item.FPSMCreativeModeTabRegister;
import com.phasetranscrystal.fpsmatch.common.item.FPSMItemRegister;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/** Future-main bootstrap with no installed-mode implementation dependencies. */
public final class CoreBootstrap {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private CoreBootstrap() {
    }

    public static void install(IEventBus modEventBus) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        ModeModules.contribute(TdmModeModule.INSTANCE);
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> CoreClientBootstrap::install);

        modEventBus.addListener(CoreBootstrap::onCommonSetup);
        modEventBus.addListener(FPSMItemRegister::onBuildCreativeModeTabContents);
        FPSMCreativeModeTabRegister.CREATIVE_MODE_TABS.register(modEventBus);
        FPSMItemRegister.ITEMS.register(modEventBus);

        MinecraftForge.EVENT_BUS.addListener(CoreBootstrap::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(CoreBootstrap::onRegisterCommands);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModeModules.freeze();
            ModNetworkChannel.register();
        });
    }

    private static void onServerStarting(ServerStartingEvent event) {
        CodTdmConfig.load(event.getServer());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandRegistration.register(event.getDispatcher());
    }
}
