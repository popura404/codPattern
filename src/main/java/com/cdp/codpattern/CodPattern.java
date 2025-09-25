package com.cdp.codpattern;

import com.cdp.codpattern.command.CommandRegistration;
import com.cdp.codpattern.command.MainMenuScreenCommand;
import com.cdp.codpattern.config.configmanager.BackpackConfigManager;
import com.cdp.codpattern.network.handler.PacketHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CodPattern.MODID)
public class CodPattern
{
    public static final String MODID = "codpattern";

    public CodPattern() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new CommandRegistration());

        MinecraftForge.EVENT_BUS.register(new MainMenuScreenCommand());

        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void setup(final FMLCommonSetupEvent event) {
        // 加载配置文件
        BackpackConfigManager.load();
        // 注册网络包
        PacketHandler.register();
    }

    private void onServerStarting(ServerStartingEvent event) {
        // 服务端启动时加载配置
        BackpackConfigManager.load();
    }
}
