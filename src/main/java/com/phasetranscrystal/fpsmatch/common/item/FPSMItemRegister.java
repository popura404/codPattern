package com.phasetranscrystal.fpsmatch.common.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = FPSMatch.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FPSMItemRegister {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FPSMatch.MODID);

    public static final RegistryObject<MapCreatorTool> MAP_CREATOR_TOOL = ITEMS.register(
            "map_creator_tool",
            () -> new MapCreatorTool(new Item.Properties().stacksTo(1))
    );

    public static final RegistryObject<SpawnPointTool> SPAWN_POINT_TOOL = ITEMS.register(
            "spawn_point_tool",
            () -> new SpawnPointTool(new Item.Properties().stacksTo(1))
    );

    private FPSMItemRegister() {
    }

    @SubscribeEvent
    public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.TOOLS_AND_UTILITIES.equals(event.getTabKey())) {
            event.accept(MAP_CREATOR_TOOL);
            event.accept(SPAWN_POINT_TOOL);
        }
    }
}
