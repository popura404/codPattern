package com.cdp.codpattern.app.match.gametest;

import com.cdp.codpattern.app.match.management.MapManagementService;
import com.cdp.codpattern.config.storage.ServerMapStorage;
import com.mojang.authlib.GameProfile;
import com.phasetranscrystal.fpsmatch.common.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.common.item.MapCreatorTool;
import com.phasetranscrystal.fpsmatch.common.item.MapCreatorToolModes;
import com.phasetranscrystal.fpsmatch.common.packet.MapCreatorToolActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenMapCreatorToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("codpattern")
@PrefixGameTestTemplate(false)
public final class MapToolGameTests {
    private MapToolGameTests() { }

    @GameTest(template = "empty", batch = "map_tools", timeoutTicks = 100)
    public static void creatorFiltersModesAndRecoversOldDrafts(GameTestHelper helper) {
        var all = List.of("zombies", "frontline", "teamdeathmatch", "addon-mode");
        helper.assertTrue(MapCreatorToolModes.availableTypes(all).equals(List.of("teamdeathmatch", "frontline")),
                "only built-in creation modes are offered in stable order");
        helper.assertTrue(MapCreatorToolModes.availableTypes(List.of("cdptdm")).equals(List.of("frontline")),
                "legacy mode aliases remain supported");
        var stack = new ItemStack(FPSMItemRegister.MAP_CREATOR_TOOL.get());
        MapCreatorTool.setSelectedType(stack, "zombies");
        MapCreatorTool.setDraftMapName(stack, "old-draft");
        MapCreatorTool.setBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_1, new BlockPos(1, 2, 3));
        var original = stack.getTag().copy();
        var data = OpenMapCreatorToolScreenS2CPacket.fromStack(stack, all);
        helper.assertTrue(data.selectedType().equals("teamdeathmatch") && data.availableTypes().size() == 2,
                "old addon selection falls back to the first supported mode");
        helper.assertTrue(data.draftMapName().equals("old-draft") && data.pos1().equals(new BlockPos(1, 2, 3))
                && original.equals(stack.getTag()), "opening preserves draft name, coordinates and stored NBT");
        helper.assertTrue(OpenMapCreatorToolScreenS2CPacket.fromStack(stack, List.of("frontline")).selectedType().equals("frontline"),
                "a single available built-in mode is selected");
        var empty = OpenMapCreatorToolScreenS2CPacket.fromStack(stack, List.of("zombies"));
        helper.assertTrue(empty.availableTypes().isEmpty() && empty.selectedType().isEmpty(),
                "no available built-in modes means no selection");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "map_tools", timeoutTicks = 200)
    public static void creatorServerRejectsOutOfScopeRequests(GameTestHelper helper) {
        var player = player(helper, true, new ArrayList<>());
        var stack = new ItemStack(FPSMItemRegister.MAP_CREATOR_TOOL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        MapCreatorTool.setDraftMapName(stack, "preserved");
        var original = stack.getTag().copy();
        String name = "tool-" + UUID.randomUUID().toString().substring(0, 8);
        var core = FPSMCore.getInstance();
        for (var action : MapCreatorToolActionC2SPacket.Action.values()) {
            new MapCreatorToolActionC2SPacket(action, "zombies", name, BlockPos.ZERO, new BlockPos(3, 3, 3)).process(player);
            helper.assertTrue(original.equals(stack.getTag()), "rejected addon request must not change the draft");
        }
        new MapCreatorToolActionC2SPacket(MapCreatorToolActionC2SPacket.Action.CREATE, "", name,
                BlockPos.ZERO, new BlockPos(3, 3, 3)).process(player);
        helper.assertTrue(original.equals(stack.getTag()) && core.getMapByName(name).isEmpty(),
                "empty creation mode cannot write a draft or create a map");
        new MapCreatorToolActionC2SPacket(MapCreatorToolActionC2SPacket.Action.SAVE_DRAFT, "", "empty-mode-draft",
                BlockPos.ZERO, null).process(player);
        helper.assertTrue(MapCreatorTool.getSelectedType(stack).isEmpty()
                && MapCreatorTool.getDraftMapName(stack).equals("empty-mode-draft"), "an unselected draft can be saved");
        new MapCreatorToolActionC2SPacket(MapCreatorToolActionC2SPacket.Action.SAVE_DRAFT, "cdptdm", "alias-draft",
                BlockPos.ZERO, new BlockPos(3, 3, 3)).process(player);
        helper.assertTrue(MapCreatorTool.getSelectedType(stack).equals("frontline"), "saved aliases are canonicalized");
        var denied = player(helper, false, new ArrayList<>());
        denied.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var beforeDenied = stack.getTag().copy();
        new MapCreatorToolActionC2SPacket(MapCreatorToolActionC2SPacket.Action.CREATE, "frontline", name,
                BlockPos.ZERO, new BlockPos(3, 3, 3)).process(denied);
        helper.assertTrue(beforeDenied.equals(stack.getTag()) && core.getMapByName(name).isEmpty(),
                "non-administrators cannot create maps");
        var folder = ServerMapStorage.get(player.server).paths().map("builtin/frontline", name);
        helper.assertTrue(!Files.exists(folder), "rejected requests create no built-in map directory");
        try {
            new MapCreatorToolActionC2SPacket(MapCreatorToolActionC2SPacket.Action.CREATE, "cdptdm", name,
                    BlockPos.ZERO, new BlockPos(3, 3, 3)).process(player);
            helper.assertTrue(core.getMapByTypeWithName("frontline", name).isPresent() && Files.isDirectory(folder),
                    "an authorized legacy alias can still create and persist a built-in map");
            helper.assertTrue(MapManagementService.list(player.server).stream().anyMatch(row -> row.roomId().mapName().equals(name)),
                    "the independent management catalog still discovers created maps");
        } finally {
            core.getMapByTypeWithName("frontline", name).ifPresent(map -> {
                core.unregisterMap(map);
                map.getMapTeams().retireCreatedScoreboardTeams();
            });
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "map_tools", timeoutTicks = 100)
    public static void managementItemConsumesAirAndChestClicksWithPermission(GameTestHelper helper) {
        var sent = new ArrayList<Packet<?>>();
        var player = player(helper, true, sent);
        var stack = new ItemStack(FPSMItemRegister.MAP_MANAGEMENT_TOOL.get());
        helper.assertTrue(stack.getMaxStackSize() == 1, "management tools are not stackable");
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var air = player.gameMode.useItem(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND);
        helper.assertTrue(air.consumesAction() && customPayloadCount(sent) == 1, "air click sends exactly one screen-open packet");
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        var hit = new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);
        sent.clear();
        var block = player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(block.consumesAction() && customPayloadCount(sent) == 1
                && player.containerMenu == player.inventoryMenu, "chest click opens management once without opening the chest");
        var deniedPackets = new ArrayList<Packet<?>>();
        var denied = player(helper, false, deniedPackets);
        denied.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        denied.gameMode.useItem(denied, helper.getLevel(), denied.getMainHandItem(), InteractionHand.MAIN_HAND);
        var refused = denied.gameMode.useItemOn(denied, helper.getLevel(), denied.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(refused.consumesAction() && customPayloadCount(deniedPackets) == 0
                && denied.containerMenu == denied.inventoryMenu, "permission denial sends no open packet and consumes the block click");
        helper.succeed();
    }

    private static long customPayloadCount(List<Packet<?>> sent) {
        return sent.stream().filter(packet -> packet instanceof ClientboundCustomPayloadPacket).count();
    }

    private static ServerPlayer player(GameTestHelper helper, boolean administrator, List<Packet<?>> sent) {
        var server = helper.getLevel().getServer();
        var player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "map-tool-test")) {
            @Override public boolean hasPermissions(int permission) { return administrator && permission <= 2; }
        };
        var connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(Packet<?> packet) { sent.add(packet); }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { sent.add(packet); }
        };
        player.connection = new ServerGamePacketListenerImpl(server, connection, player) {
            @Override public void send(Packet<?> packet) { sent.add(packet); }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { sent.add(packet); }
        };
        return player;
    }
}
