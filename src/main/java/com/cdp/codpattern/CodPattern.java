package com.cdp.codpattern;

import com.cdp.codpattern.network.handler.PacketHandler;
import net.minecraftforge.common.MinecraftForge;
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
    }

    private void setup(final FMLCommonSetupEvent event) {
        // 注册网络包
        PacketHandler.register();
    }
}
