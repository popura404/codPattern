package com.phasetranscrystal.fpsmatch.common.item;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class FPSMCreativeModeTabRegister {
    public static final ResourceKey<CreativeModeTab> CODPATTERN_TOOLS_AND_ITEMS_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(FPSMatch.MODID, "tools_and_items")
    );

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB,
            FPSMatch.MODID
    );

    public static final RegistryObject<CreativeModeTab> CODPATTERN_TOOLS_AND_ITEMS = CREATIVE_MODE_TABS.register(
            "tools_and_items",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
                    .title(Component.translatable("itemGroup.codpattern.tools_and_items"))
                    .icon(() -> new ItemStack(FPSMItemRegister.MAP_CREATOR_TOOL.get()))
                    .displayItems((parameters, output) -> {
                    })
                    .build()
    );

    private FPSMCreativeModeTabRegister() {
    }
}
