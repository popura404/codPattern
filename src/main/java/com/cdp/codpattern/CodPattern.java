package com.cdp.codpattern;

import com.cdp.codpattern.app.zombies.bootstrap.ZombiesBootstrap;
import com.cdp.codpattern.bootstrap.CoreBootstrap;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CodPatternConstants.MOD_ID)
public class CodPattern {
    public static final String MODID = CodPatternConstants.MOD_ID;

    public CodPattern() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        CoreBootstrap.install(modEventBus);
        ZombiesBootstrap.install(modEventBus);
    }
}
