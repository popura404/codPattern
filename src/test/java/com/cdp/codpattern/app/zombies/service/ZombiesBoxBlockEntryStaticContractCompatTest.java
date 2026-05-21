package com.cdp.codpattern.app.zombies.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesBoxBlockEntryStaticContractCompatTest {
    private static final Path BLOCK = Path.of("src/main/java/com/cdp/codpattern/common/block/ZombiesBoxInteractionBlock.java");
    private static final Path REGISTRY = Path.of("src/main/java/com/cdp/codpattern/common/block/CodPatternBlockRegister.java");
    private static final Path EVENT_HANDLER = Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/event/ModeObjectInteractionEventHandler.java");
    private static final Path SERVICE = Path.of("src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesObjectInteractionService.java");
    private static final Path TACZ_INTERACT_WHITELIST = Path.of("src/main/resources/data/tacz/tags/blocks/interact_key/whitelist.json");

    private ZombiesBoxBlockEntryStaticContractCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        String block = Files.readString(BLOCK);
        String registry = Files.readString(REGISTRY);
        String handler = Files.readString(EVENT_HANDLER);
        String service = Files.readString(SERVICE);
        String taczInteractWhitelist = Files.readString(TACZ_INTERACT_WHITELIST);

        requireContains(block, "public final class ZombiesBoxInteractionBlock extends Block",
                "box block must be a reusable Block subclass");
        requireContains(block, "public InteractionResult use(",
                "box block must own the block use entry point");
        requireContains(block, "if (level.isClientSide) {\n            return hand == InteractionHand.MAIN_HAND ? InteractionResult.SUCCESS : InteractionResult.PASS;\n        }",
                "client-side main-hand box clicks must consume locally so held TaCZ items do not handle the same right-click");
        requireContains(block, "player instanceof ServerPlayer serverPlayer",
                "server-side box block entry must require a ServerPlayer");
        requireContains(block, "FpsMatchGatewayProvider.gateway()\n                .findPlayerInteractableObjectPort(serverPlayer)",
                "box block entry must resolve the current room interactable object port");
        requireContains(block, "new ModeObjectInteractionContext(\n                                port.roomId(),\n                                hand,\n                                pos,\n                                face(hit),\n                                null,\n                                heldItem(player, hand))",
                "box block entry must pass room id, hand, clicked position, face, no entity, and held item to the service");
        requireContains(block, ".map(result -> result == null ? InteractionResult.PASS : result)",
                "box block entry must return the service result and only normalize null to PASS");
        requireAbsent(block, "return InteractionResult.SUCCESS;",
                "box block entry must not return unconditional SUCCESS");
        requireAbsent(block, "InteractionResult.CONSUME",
                "box block entry must not return unconditional CONSUME");
        requireAbsent(block, "sendMessage(",
                "box block must not own gameplay messages");
        requireAbsent(block, "spend",
                "box block must not own cost or economy logic");
        requireAbsent(block, "refill",
                "box block must not own ammo refill logic");
        requireAbsent(block, "armor",
                "box block must not own armor purchase logic");

        requireContains(registry, "RegistryObject<ZombiesBoxInteractionBlock> ZOMBIES_WEAPON_WALL_BOX",
                "weapon wall box registry type must use ZombiesBoxInteractionBlock");
        requireContains(registry, "RegistryObject<ZombiesBoxInteractionBlock> ZOMBIES_AMMO_BOX",
                "ammo box registry type must use ZombiesBoxInteractionBlock");
        requireContains(registry, "RegistryObject<ZombiesBoxInteractionBlock> ZOMBIES_ARMOR_STATION_BOX",
                "armor station box registry type must use ZombiesBoxInteractionBlock");
        requireContains(registry, "RegistryObject<ZombiesBoxInteractionBlock> ZOMBIES_SODA_MACHINE_BOX",
                "soda machine box registry type must use ZombiesBoxInteractionBlock");
        requireContains(registry, "RegistryObject<ZombiesBoxInteractionBlock> ZOMBIES_ULTIMATE_MACHINE_BOX",
                "ultimate machine box registry type must use ZombiesBoxInteractionBlock");
        requireContains(registry, "new ZombiesBoxInteractionBlock(BlockBehaviour.Properties.copy(Blocks.RED_CONCRETE)",
                "weapon wall box registry id must keep its existing simple colored box block");
        requireContains(registry, "new ZombiesBoxInteractionBlock(BlockBehaviour.Properties.copy(Blocks.GREEN_CONCRETE)",
                "ammo box registry id must keep its existing simple colored box block");
        requireContains(registry, "new ZombiesBoxInteractionBlock(BlockBehaviour.Properties.copy(Blocks.BLUE_CONCRETE)",
                "armor station box registry id must keep its existing simple colored box block");
        requireContains(registry, "new ZombiesBoxInteractionBlock(BlockBehaviour.Properties.copy(Blocks.YELLOW_CONCRETE)",
                "soda machine box registry id must use a simple yellow box block");
        requireContains(registry, "new ZombiesBoxInteractionBlock(BlockBehaviour.Properties.copy(Blocks.PURPLE_CONCRETE)",
                "ultimate machine box registry id must use a simple purple box block");
        requireContains(taczInteractWhitelist, "\"codpattern:zombies_power_switch\"",
                "power switch must be whitelisted for TaCZ interact-key block interaction");
        requireContains(taczInteractWhitelist, "\"codpattern:zombies_player_barrier\"",
                "player barrier must be whitelisted for TaCZ interact-key block interaction");
        requireContains(taczInteractWhitelist, "\"codpattern:zombies_weapon_wall_box\"",
                "weapon wall box must be whitelisted for TaCZ interact-key block interaction");
        requireContains(taczInteractWhitelist, "\"codpattern:zombies_ammo_box\"",
                "ammo box must be whitelisted for TaCZ interact-key block interaction");
        requireContains(taczInteractWhitelist, "\"codpattern:zombies_armor_station_box\"",
                "armor station box must be whitelisted for TaCZ interact-key block interaction");
        requireContains(taczInteractWhitelist, "\"codpattern:zombies_soda_machine_box\"",
                "soda machine box must be whitelisted for TaCZ interact-key block interaction");
        requireContains(taczInteractWhitelist, "\"codpattern:zombies_ultimate_machine_box\"",
                "ultimate machine box must be whitelisted for TaCZ interact-key block interaction");

        String onRightClickBlock = methodBody(handler, "public static void onRightClickBlock");
        requireContains(onRightClickBlock, "if (isBlockHandledByOwnUse(event)) {\n            return;\n        }",
                "global RightClickBlock handler must skip box blocks handled by Block.use");
        requireContains(handler, "instanceof ZombiesBoxInteractionBlock",
                "global RightClickBlock skip must be scoped to the new box block class");
        requireContains(handler, "public static void onRightClickItem",
                "RightClickItem handler must remain separate for non-block fallback behavior");
        requireContains(handler, "public static void onEntityInteract",
                "EntityInteract handler must remain separate for non-block fallback behavior");

        requireContains(service, "InteractionResult gateResult = gateBoxStyleInteraction(player, target, context);\n        if (gateResult != null) {\n            return gateResult;\n        }\n\n        long gameTime",
                "recent interaction de-duplication must only run after box-style hand gating");
        requireAbsent(service, "gateTaggedTaczWeaponInteraction",
                "general object interactions must not reject held TaCZ guns by zombies weapon tag");
        requireAbsent(service, "currentWeaponTag(roomId, context.itemStack())",
                "barrier, armor station, soda, and power interactions must not require a held zombies weapon tag");
        requireContains(service, ".filter(candidate -> !isBoxStyleObject(candidate.type()))",
                "no-block-position fallback must keep excluding box-style objects");
        String boxGate = methodBody(service, "private InteractionResult gateBoxStyleInteraction");
        requireContains(boxGate, "target.type() == InteractionType.AMMO_BOX || target.type() == InteractionType.ULTIMATE_MACHINE",
                "ammo box and ultimate machine invalid held item must remain service-controlled failures");
        requireContains(boxGate, "return InteractionResult.PASS;",
                "weapon wall and armor off-hand paths must keep PASS behavior");
        String canHandle = methodBody(service, "private static boolean canHandleBoxStyleInteraction");
        requireContains(canHandle, "boolean mainHand = context.hand() == InteractionHand.MAIN_HAND;",
                "box-style hand filtering must keep main-hand authority in the service");
        requireContains(canHandle, "boolean mainHandTacz = mainHand && TaczGatewayProvider.gateway().isGun(context.itemStack());",
                "ammo box TaCZ held-item filtering must keep using the service gateway");
        requireContains(canHandle, "case WEAPON_WALL -> mainHand;",
                "weapon wall must accept any main-hand item state");
        requireContains(canHandle, "case AMMO_BOX -> mainHandTacz;",
                "ammo box must keep main-hand TaCZ requirement");
        requireContains(canHandle, "case ARMOR_STATION -> mainHand;",
                "armor station must accept any main-hand item state");
        requireContains(canHandle, "case SODA_MACHINE -> mainHand;",
                "soda machine must accept any main-hand item state");
        requireContains(canHandle, "case ULTIMATE_MACHINE -> mainHandTacz;",
                "ultimate machine must require a main-hand TaCZ weapon");
        String expectedInteractionBlock = methodBody(service, "private static Block expectedInteractionBlock");
        requireContains(expectedInteractionBlock, "case SODA_MACHINE -> CodPatternBlockRegister.ZOMBIES_SODA_MACHINE_BOX.get();",
                "soda machine target matching must require the registered soda machine box block");
        String isAnyBoxStyleBlock = methodBody(service, "private static boolean isAnyBoxStyleBlock");
        requireContains(isAnyBoxStyleBlock, "CodPatternBlockRegister.ZOMBIES_SODA_MACHINE_BOX.get()",
                "unmanaged soda machine box clicks must stay in the box-block PASS path");

        System.out.println("PASS zombies box block entry static contract compat");
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("missing method `" + signature + "`");
        }
        int open = source.indexOf('{', start);
        if (open < 0) {
            throw new AssertionError("missing method body `" + signature + "`");
        }
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open + 1, i);
                }
            }
        }
        throw new AssertionError("unterminated method `" + signature + "`");
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
