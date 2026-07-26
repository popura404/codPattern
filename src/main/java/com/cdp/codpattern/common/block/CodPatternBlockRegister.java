package com.cdp.codpattern.common.block;

import com.cdp.codpattern.CodPatternConstants;
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
            DeferredRegister.create(ForgeRegistries.BLOCKS, CodPatternConstants.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CodPatternConstants.MOD_ID);

    public static final RegistryObject<ZombiesPowerSwitchBlock> ZOMBIES_POWER_SWITCH = BLOCKS.register(
            "zombies_power_switch",
            () -> new ZombiesPowerSwitchBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL))
    );
    public static final RegistryObject<ZombiesPlayerBarrierBlock> ZOMBIES_PLAYER_BARRIER = BLOCKS.register(
            "zombies_player_barrier",
            () -> new ZombiesPlayerBarrierBlock(BlockBehaviour.Properties.copy(Blocks.GLASS)
                    .strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false))
    );
    public static final RegistryObject<ZombiesRedPlayerBarrierBlock> ZOMBIES_RED_PLAYER_BARRIER = BLOCKS.register(
            "zombies_red_player_barrier",
            () -> new ZombiesRedPlayerBarrierBlock(BlockBehaviour.Properties.copy(Blocks.RED_STAINED_GLASS)
                    .strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false))
    );
    public static final RegistryObject<ZombiesBoxInteractionBlock> ZOMBIES_WEAPON_WALL_BOX = BLOCKS.register(
            "zombies_weapon_wall_box",
            () -> new ZombiesBoxInteractionBlock(BlockBehaviour.Properties.copy(Blocks.RED_CONCRETE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOD))
    );
    public static final RegistryObject<ZombiesBoxInteractionBlock> ZOMBIES_AMMO_BOX = BLOCKS.register(
            "zombies_ammo_box",
            () -> new ZombiesBoxInteractionBlock(BlockBehaviour.Properties.copy(Blocks.GREEN_CONCRETE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOD))
    );
    public static final RegistryObject<ZombiesBoxInteractionBlock> ZOMBIES_ARMOR_STATION_BOX = BLOCKS.register(
            "zombies_armor_station_box",
            () -> new ZombiesBoxInteractionBlock(BlockBehaviour.Properties.copy(Blocks.BLUE_CONCRETE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOD))
    );
    public static final RegistryObject<ZombiesBoxInteractionBlock> ZOMBIES_SODA_MACHINE_BOX = BLOCKS.register(
            "zombies_soda_machine_box",
            () -> new ZombiesBoxInteractionBlock(BlockBehaviour.Properties.copy(Blocks.YELLOW_CONCRETE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOD))
    );
    public static final RegistryObject<ZombiesBoxInteractionBlock> ZOMBIES_ULTIMATE_MACHINE_BOX = BLOCKS.register(
            "zombies_ultimate_machine_box",
            () -> new ZombiesBoxInteractionBlock(BlockBehaviour.Properties.copy(Blocks.PURPLE_CONCRETE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOD))
    );

    public static final RegistryObject<Item> ZOMBIES_POWER_SWITCH_ITEM = ITEMS.register(
            "zombies_power_switch",
            () -> new BlockItem(ZOMBIES_POWER_SWITCH.get(), new Item.Properties())
    );
    public static final RegistryObject<Item> ZOMBIES_RED_PLAYER_BARRIER_ITEM = ITEMS.register(
            "zombies_red_player_barrier",
            () -> new BlockItem(ZOMBIES_RED_PLAYER_BARRIER.get(), new Item.Properties())
    );
    public static final RegistryObject<Item> ZOMBIES_WEAPON_WALL_BOX_ITEM = ITEMS.register(
            "zombies_weapon_wall_box",
            () -> new BlockItem(ZOMBIES_WEAPON_WALL_BOX.get(), new Item.Properties())
    );
    public static final RegistryObject<Item> ZOMBIES_AMMO_BOX_ITEM = ITEMS.register(
            "zombies_ammo_box",
            () -> new BlockItem(ZOMBIES_AMMO_BOX.get(), new Item.Properties())
    );
    public static final RegistryObject<Item> ZOMBIES_ARMOR_STATION_BOX_ITEM = ITEMS.register(
            "zombies_armor_station_box",
            () -> new BlockItem(ZOMBIES_ARMOR_STATION_BOX.get(), new Item.Properties())
    );
    public static final RegistryObject<Item> ZOMBIES_SODA_MACHINE_BOX_ITEM = ITEMS.register(
            "zombies_soda_machine_box",
            () -> new BlockItem(ZOMBIES_SODA_MACHINE_BOX.get(), new Item.Properties())
    );
    public static final RegistryObject<Item> ZOMBIES_ULTIMATE_MACHINE_BOX_ITEM = ITEMS.register(
            "zombies_ultimate_machine_box",
            () -> new BlockItem(ZOMBIES_ULTIMATE_MACHINE_BOX.get(), new Item.Properties())
    );

    private CodPatternBlockRegister() {
    }

    public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.hasPermissions()
                && (CreativeModeTabs.FUNCTIONAL_BLOCKS.equals(event.getTabKey())
                || CreativeModeTabs.REDSTONE_BLOCKS.equals(event.getTabKey()))) {
            event.accept(ZOMBIES_POWER_SWITCH_ITEM);
            event.accept(ZOMBIES_WEAPON_WALL_BOX_ITEM);
            event.accept(ZOMBIES_AMMO_BOX_ITEM);
            event.accept(ZOMBIES_ARMOR_STATION_BOX_ITEM);
            event.accept(ZOMBIES_SODA_MACHINE_BOX_ITEM);
            event.accept(ZOMBIES_ULTIMATE_MACHINE_BOX_ITEM);
        }
    }
}
