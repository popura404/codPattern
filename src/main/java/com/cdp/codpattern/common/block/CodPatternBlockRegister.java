package com.cdp.codpattern.common.block;

import com.cdp.codpattern.CodPattern;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class CodPatternBlockRegister {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CodPattern.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CodPattern.MODID);

    public static final RegistryObject<ZombiesPowerSwitchBlock> ZOMBIES_POWER_SWITCH = BLOCKS.register(
            "zombies_power_switch",
            () -> new ZombiesPowerSwitchBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL))
    );

    public static final RegistryObject<Item> ZOMBIES_POWER_SWITCH_ITEM = ITEMS.register(
            "zombies_power_switch",
            () -> new BlockItem(ZOMBIES_POWER_SWITCH.get(), new Item.Properties())
    );

    private CodPatternBlockRegister() {
    }

    public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.hasPermissions()
                && (CreativeModeTabs.FUNCTIONAL_BLOCKS.equals(event.getTabKey())
                || CreativeModeTabs.REDSTONE_BLOCKS.equals(event.getTabKey()))) {
            event.accept(ZOMBIES_POWER_SWITCH_ITEM);
        }
    }
}
