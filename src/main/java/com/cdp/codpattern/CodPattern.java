package com.cdp.codpattern;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.phasetranscrystal.fpsmatch.common.item.FPSMItemRegister;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CodPattern.MODID)
public class CodPattern {
    public static final String MODID = "codpattern";

    public CodPattern() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::setup);
        FPSMItemRegister.ITEMS.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        // 注册网络包
        ModNetworkChannel.register();
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        com.cdp.codpattern.config.tdm.CodTdmConfig.load(event.getServer());
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        com.cdp.codpattern.command.CommandRegistration.register(event.getDispatcher());
    }
}
