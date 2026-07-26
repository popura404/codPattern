package com.cdp.codpattern.bootstrap;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.match.extension.ModeHeldToolPreviewContributor;
import com.cdp.codpattern.app.match.runtime.tool.ModeHeldToolPreviewContributors;
import com.cdp.codpattern.app.tdm.model.TdmGameModeDefinitions;
import com.cdp.codpattern.client.bootstrap.CoreClientBootstrap;
import com.cdp.codpattern.command.CommandRegistration;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmLoginRecoveryContributor;
import com.cdp.codpattern.app.match.runtime.player.ModePlayerLoginContributors;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.phasetranscrystal.fpsmatch.common.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.common.item.MapCreatorTool;
import com.phasetranscrystal.fpsmatch.common.item.SpawnPointTool;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
        TdmGameModeDefinitions.registerDefaults();
        ModePlayerLoginContributors.register(new CodTdmLoginRecoveryContributor());
        installCoreToolPreviewContributors();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> CoreClientBootstrap::install);

        modEventBus.addListener(CoreBootstrap::onCommonSetup);
        modEventBus.addListener(FPSMItemRegister::onBuildCreativeModeTabContents);
        FPSMItemRegister.ITEMS.register(modEventBus);

        MinecraftForge.EVENT_BUS.addListener(CoreBootstrap::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(CoreBootstrap::onRegisterCommands);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        ModNetworkChannel.register();
    }

    private static void onServerStarting(ServerStartingEvent event) {
        CodTdmConfig.load(event.getServer());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandRegistration.register(event.getDispatcher());
    }

    private static void installCoreToolPreviewContributors() {
        ModeHeldToolPreviewContributors.register(new ModeHeldToolPreviewContributor() {
            @Override
            public String id() {
                return "fpsm.map_creator";
            }

            @Override
            public int order() {
                return 10;
            }

            @Override
            public boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof MapCreatorTool;
            }

            @Override
            public void sync(ServerPlayer player, ItemStack stack) {
                ((MapCreatorTool) stack.getItem()).syncHeldPreview(player, stack);
            }

            @Override
            public void clear(ServerPlayer player) {
                MapCreatorTool.clearHeldPreview(player);
            }
        });
        ModeHeldToolPreviewContributors.register(new ModeHeldToolPreviewContributor() {
            @Override
            public String id() {
                return "fpsm.spawn_point";
            }

            @Override
            public int order() {
                return 20;
            }

            @Override
            public boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof SpawnPointTool;
            }

            @Override
            public void sync(ServerPlayer player, ItemStack stack) {
                ((SpawnPointTool) stack.getItem()).syncHeldPreview(player, stack);
            }

            @Override
            public void clear(ServerPlayer player) {
                SpawnPointTool.clearHeldPreview(player);
            }
        });
    }
}
