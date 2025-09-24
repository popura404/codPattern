package com.cdp.codpattern;

import com.cdp.codpattern.command.CommandRegistration;
import com.cdp.codpattern.command.MainMenuScreenCommand;
import com.cdp.codpattern.config.configmanager.BackpackConfigManager;
import com.cdp.codpattern.network.handler.PacketHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CodPattern.MODID)
public class CodPattern
{
    public static final String MODID = "codpattern";
    private static final Logger LOGGER = LogUtils.getLogger();

    public CodPattern()
    {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        //FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new CommandRegistration());
        MinecraftForge.EVENT_BUS.register(new MainMenuScreenCommand());
    }

    private void setup(final FMLCommonSetupEvent event) {
        // 加载配置文件
        BackpackConfigManager.load();
        // 注册网络包
        PacketHandler.register();
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
