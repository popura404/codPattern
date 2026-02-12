package com.cdp.codpattern;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CodPattern.MODID)
public class CodPattern {
    public static final String MODID = "codpattern";

    public CodPattern() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
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

    @SuppressWarnings("deprecation") // 保留旧命令以向后兼容，新命令请使用 /fpsm tdm
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        com.cdp.codpattern.command.CommandRegistration.register(event.getDispatcher());
        // 旧命令 (已弃用): /codtdm create <mapName>
        // 新命令: /fpsm tdm create <mapName> (通过 TdmFpsmCommandHandler 注册)
        com.cdp.codpattern.command.CodTdmCommands.register(event.getDispatcher());
    }
}
