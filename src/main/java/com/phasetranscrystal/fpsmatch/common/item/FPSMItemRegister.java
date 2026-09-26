package com.phasetranscrystal.fpsmatch.common.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FPSMItemRegister {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FPSMatch.MODID);

    public static final RegistryObject<MapManagementTool> MAP_MANAGEMENT_TOOL = ITEMS.register(
            "map_management_tool",
            () -> new MapManagementTool(new Item.Properties().stacksTo(1))
    );

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

    public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (FPSMCreativeModeTabRegister.CODPATTERN_TOOLS_AND_ITEMS_KEY.equals(event.getTabKey())) {
            event.accept(MAP_MANAGEMENT_TOOL);
            event.accept(MAP_CREATOR_TOOL);
            event.accept(SPAWN_POINT_TOOL);
        }
    }
}
