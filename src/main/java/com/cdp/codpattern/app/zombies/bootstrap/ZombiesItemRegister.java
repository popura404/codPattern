package com.cdp.codpattern.app.zombies.bootstrap;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.ZombiesDeployTool;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Zombies-owned FPSMatch item registration preserving the legacy codpattern namespace and ID. */
public final class ZombiesItemRegister {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FPSMatch.MODID);

    public static final RegistryObject<ZombiesDeployTool> ZOMBIES_DEPLOY_TOOL = ITEMS.register(
            "zombies_deploy_tool",
            () -> new ZombiesDeployTool(new Item.Properties().stacksTo(1))
    );

    private ZombiesItemRegister() {
    }

    public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.TOOLS_AND_UTILITIES.equals(event.getTabKey())) {
            event.accept(ZOMBIES_DEPLOY_TOOL);
        }
    }
}
