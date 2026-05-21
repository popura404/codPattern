package com.cdp.codpattern.app.zombies.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesRedPlayerBarrierStaticContractCompatTest {
    private static final Path BLOCK = Path.of("src/main/java/com/cdp/codpattern/common/block/ZombiesRedPlayerBarrierBlock.java");
    private static final Path REGISTRY = Path.of("src/main/java/com/cdp/codpattern/common/block/CodPatternBlockRegister.java");
    private static final Path BLOCKSTATE = Path.of("src/main/resources/assets/codpattern/blockstates/zombies_red_player_barrier.json");
    private static final Path BLOCK_MODEL = Path.of("src/main/resources/assets/codpattern/models/block/zombies_red_player_barrier.json");
    private static final Path ITEM_MODEL = Path.of("src/main/resources/assets/codpattern/models/item/zombies_red_player_barrier.json");
    private static final Path EN_US = Path.of("src/main/resources/assets/codpattern/lang/en_us.json");
    private static final Path ZH_CN = Path.of("src/main/resources/assets/codpattern/lang/zh_cn.json");

    private ZombiesRedPlayerBarrierStaticContractCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        String block = Files.readString(BLOCK);
        String registry = Files.readString(REGISTRY);
        String blockstate = Files.readString(BLOCKSTATE);
        String blockModel = Files.readString(BLOCK_MODEL);
        String itemModel = Files.readString(ITEM_MODEL);
        String enUs = Files.readString(EN_US);
        String zhCn = Files.readString(ZH_CN);

        requireContains(block, "public final class ZombiesRedPlayerBarrierBlock extends Block",
                "red player barrier must be a standalone block");
        requireContains(block, "return entity instanceof Player ? FULL_SHAPE : Shapes.empty();",
                "red player barrier must collide only with players by default");
        requireContains(block, "public InteractionResult use(",
                "red player barrier must define an explicit no-op use path");
        requireContains(block, "return InteractionResult.PASS;",
                "red player barrier must not own interaction logic");
        requireAbsent(block, "ZombiesBarrierBlockRuntimeService",
                "red player barrier must not require runtime active-cell registration");
        requireAbsent(block, "onBarrierBlockRemoved",
                "red player barrier must not mutate runtime barrier indexes on removal");

        requireContains(registry, "RegistryObject<ZombiesRedPlayerBarrierBlock> ZOMBIES_RED_PLAYER_BARRIER",
                "red player barrier block must be registered");
        requireContains(registry, "\"zombies_red_player_barrier\"",
                "red player barrier registry id must be stable for commands");
        requireContains(registry, "RegistryObject<Item> ZOMBIES_RED_PLAYER_BARRIER_ITEM",
                "red player barrier must have a BlockItem so /give can provide it");
        requireContains(registry, "new BlockItem(ZOMBIES_RED_PLAYER_BARRIER.get(), new Item.Properties())",
                "red player barrier item must place the registered block");
        requireAbsent(registry, "event.accept(ZOMBIES_RED_PLAYER_BARRIER_ITEM);",
                "red player barrier should remain command-obtainable without adding it to creative tabs");

        requireContains(blockstate, "\"model\": \"codpattern:block/zombies_red_player_barrier\"",
                "red player barrier blockstate must point at its block model");
        requireContains(blockModel, "\"render_type\": \"minecraft:translucent\"",
                "red player barrier model must render as translucent");
        requireContains(blockModel, "\"all\": \"minecraft:block/red_stained_glass\"",
                "red player barrier model must use a red texture");
        requireContains(itemModel, "\"parent\": \"codpattern:block/zombies_red_player_barrier\"",
                "red player barrier item model must use the block model");
        requireContains(enUs, "\"block.codpattern.zombies_red_player_barrier\"",
                "red player barrier must have an English language entry");
        requireContains(zhCn, "\"block.codpattern.zombies_red_player_barrier\"",
                "red player barrier must have a Simplified Chinese language entry");

        System.out.println("PASS zombies red player barrier static contract compat");
    }

    private static void requireContains(String text, String expected, String message) {
        if (!text.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }

    private static void requireAbsent(String text, String unexpected, String message) {
        if (text.contains(unexpected)) {
            throw new AssertionError(message + ": found `" + unexpected + "`");
        }
    }
}
