package com.cdp.codpattern.app.match.gametest;

import com.cdp.codpattern.app.match.management.EndTeleportService;
import com.cdp.codpattern.app.match.management.MapManagementService;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.termination.RoomTerminationService;
import com.cdp.codpattern.compat.fpsmatch.data.CodMapPersistence;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmMap;
import com.cdp.codpattern.config.storage.ServerMapStorage;
import com.cdp.codpattern.network.map.*;
import com.phasetranscrystal.fpsmatch.common.item.FPSMItemRegister;
import com.phasetranscrystal.fpsmatch.common.packet.MapCreatorToolActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.service.MapCreationService;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.UUID;

@GameTestHolder("codpattern")
@PrefixGameTestTemplate(false)
public final class EndTeleportGameTests {
    private EndTeleportGameTests() { }

    @GameTest(template = "empty", batch = "end_teleport", timeoutTicks = 300)
    public static void defaultsAreCopiedOnlyAtCreationAndEditsAreGuarded(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var core = FPSMCore.getInstance();
        var storage = ServerMapStorage.get(server);
        var defaultsFile = storage.paths().defaults();
        byte[] previous = Files.exists(defaultsFile) ? Files.readAllBytes(defaultsFile) : null;
        var maps = new ArrayList<BaseMap>();
        String prefix = "endtp-" + UUID.randomUUID().toString().substring(0, 8);
        var admin = player(helper, true);
        var denied = player(helper, false);
        var first = new SpawnPointData(helper.getLevel().dimension(), new BlockPos(12, 70, -4), 45, 0);
        var second = new SpawnPointData(helper.getLevel().dimension(), new BlockPos(20, 72, -8), 90, 0);
        try {
            Files.deleteIfExists(defaultsFile);
            var old = new CodTdmMap(helper.getLevel(), prefix + "-old", new AreaData(BlockPos.ZERO, new BlockPos(4, 4, 4)));
            maps.add(old);
            old.setMatchEndTeleportPoint(second);
            core.registerMap("frontline", old);
            CodMapPersistence.saveMap(old);
            var unset = new CodTdmMap(helper.getLevel(), prefix + "-unset", new AreaData(BlockPos.ZERO, new BlockPos(4, 4, 4)));
            maps.add(unset);
            EndTeleportService.registerAndSaveNew(server, unset);
            helper.assertTrue(unset.readPort().matchEndTeleportPoint().isEmpty(), "missing default leaves new map unset");
            var snapshot = EndTeleportService.read(admin, null);
            helper.assertTrue(snapshot.point().isEmpty(), "default starts unset");
            var save = MapAdminRequestPacket.teleport(MapAdminRequestPacket.Operation.SAVE_DEFAULTS,
                    UUID.randomUUID(), 1, null, snapshot.revision(), MapAdminData.EndPoint.from(first));
            helper.assertTrue(save.process(denied).code() == MapAdminResponsePacket.Code.DENIED, "non-admin cannot change defaults");
            var result = save.process(admin);
            helper.assertTrue(result.code() == MapAdminResponsePacket.Code.OK, "save defaults: " + result.result());
            helper.assertTrue(save.process(admin).equals(result), "duplicate save returns original result");
            roundTrip(helper, save, result);
            var changed = MapAdminRequestPacket.teleport(MapAdminRequestPacket.Operation.SAVE_DEFAULTS,
                    save.session(), save.requestId(), null, snapshot.revision(), MapAdminData.EndPoint.from(second));
            helper.assertTrue(changed.process(admin).code() == MapAdminResponsePacket.Code.STALE, "same request identity cannot change body");
            helper.assertTrue(EndTeleportService.save(admin, null, snapshot.revision(), second).code().equals("stale"), "old default revision rejected");
            helper.assertTrue(old.readPort().matchEndTeleportPoint().orElseThrow().equals(second)
                    && unset.readPort().matchEndTeleportPoint().isEmpty(), "global save does not alter configured or unset maps");

            var tool = MapCreationService.instance().createMap(admin, "frontline", prefix + "-tool", BlockPos.ZERO, new BlockPos(4, 4, 4));
            helper.assertTrue(tool.success(), "tool creation succeeds: " + tool.code());
            maps.add(tool.map());
            var room = RoomId.of("frontline", tool.mapName());
            helper.assertTrue(EndTeleportService.read(admin, room).point().orElseThrow().equals(first), "tool copies default");
            var defaultRevision = EndTeleportService.read(admin, null).revision();
            helper.assertTrue(EndTeleportService.save(admin, null, defaultRevision, second).code().equals("saved"), "change default");
            helper.assertTrue(EndTeleportService.read(admin, room).point().orElseThrow().equals(first), "existing map retains old default");
            String laterName = prefix + "-later-tool";
            admin.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FPSMItemRegister.MAP_CREATOR_TOOL.get()));
            new MapCreatorToolActionC2SPacket(MapCreatorToolActionC2SPacket.Action.CREATE, "frontline", laterName,
                    BlockPos.ZERO, new BlockPos(4, 4, 4)).process(admin);
            BaseMap laterMap = core.getMapByTypeWithName("frontline", laterName).orElseThrow();
            maps.add(laterMap);
            helper.assertTrue(EndTeleportService.read(admin, RoomId.of("frontline", laterName)).point().orElseThrow().equals(second),
                    "GUI creation request copies changed default");
            var mapSnapshot = EndTeleportService.read(admin, room);
            helper.assertTrue(mapSnapshot.editable(), "idle map supports editing");
            var current = MapAdminRequestPacket.teleport(MapAdminRequestPacket.Operation.CURRENT_POSITION,
                    UUID.randomUUID(), 2, room, "", null);
            var currentResponse = current.process(admin);
            helper.assertTrue(currentResponse.currentPosition().position().equals(admin.blockPosition()), "position comes from server player");
            helper.assertTrue(EndTeleportService.read(admin, room).point().orElseThrow().equals(first), "position read does not save");
            roundTrip(helper, current, currentResponse);
            var spectators = tool.map().getMapTeams().getSpectatorTeam();
            spectators.join(denied);
            try {
                helper.assertTrue(!EndTeleportService.read(admin, room).editable(), "occupied map cannot be edited");
                helper.assertTrue(!EndTeleportService.save(admin, room, mapSnapshot.revision(), second).code().equals("saved"), "server rejects occupied save");
            } finally { spectators.leave(denied); }
            var savedMap = EndTeleportService.save(admin, room, mapSnapshot.revision(), second);
            helper.assertTrue(savedMap.code().equals("saved"), "idle map saves: " + savedMap.code());
            assertMapSaveFailureRestoresPoint(helper, admin, room, first, second);
            var invalidRequest = MapAdminRequestPacket.teleport(MapAdminRequestPacket.Operation.SAVE_END_POINT,
                    UUID.randomUUID(), 10, room, MapManagementService.revision(server, room),
                    new MapAdminData.EndPoint(helper.getLevel().dimension().location().toString(), new BlockPos(67108864, 4096, 0), 0, 0));
            var invalidBuffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                invalidRequest.encode(invalidBuffer);
                var decoded = MapAdminRequestPacket.decode(invalidBuffer);
                helper.assertTrue(decoded.equals(invalidRequest), "large coordinates do not wrap in transit");
                helper.assertTrue(decoded.process(admin).code() == MapAdminResponsePacket.Code.ERROR, "invalid coordinates rejected after decoding");
            } finally { invalidBuffer.release(); }
            helper.assertTrue(MapManagementService.editBlockedReason(server, room, "idle", true).equals("recovery_pending"),
                    "offline recovery blocks an otherwise idle map");
            helper.assertTrue(EndTeleportService.save(admin, room, mapSnapshot.revision(), first).code().equals("stale"), "map revision conflict rejected");
            var persisted = com.google.gson.JsonParser.parseString(Files.readString(storage.paths().map("builtin/frontline", room.mapName()).resolve("map.json")));
            helper.assertTrue(persisted.toString().contains("matchEndTeleportPoint"), "end point persisted in map definition");
            try {
                EndTeleportService.save(admin, room, MapManagementService.revision(server, room),
                        new SpawnPointData(helper.getLevel().dimension(), new BlockPos(30000001, 70, 0), 0, 0));
                throw new AssertionError("invalid world coordinates accepted");
            } catch (IllegalArgumentException expected) { }
            byte[] validDefaults = Files.readAllBytes(defaultsFile);
            Files.writeString(defaultsFile, "{broken");
            helper.assertTrue(EndTeleportService.read(admin, room).point().orElseThrow().equals(second), "bad defaults do not hide existing map point");
            var failed = MapCreationService.instance().createMap(admin, "frontline", prefix + "-bad", BlockPos.ZERO, new BlockPos(4, 4, 4));
            helper.assertTrue(!failed.success() && failed.code().equals("defaults_unavailable")
                    && core.getMapByTypeWithName("frontline", prefix + "-bad").isEmpty(), "bad defaults abort new creation without registration");
            helper.assertTrue(Files.readString(defaultsFile).equals("{broken"), "bad defaults not replaced");
            Files.write(defaultsFile, validDefaults);
            helper.succeed();
        } finally {
            for (BaseMap map : maps) {
                core.unregisterMap(map);
                map.getMapTeams().retireCreatedScoreboardTeams();
                var root = storage.paths().map("builtin/frontline", map.getMapName());
                if (Files.exists(root)) try (var paths = Files.walk(root)) {
                    for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
            if (previous == null) Files.deleteIfExists(defaultsFile);
            else Files.write(defaultsFile, previous);
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertMapSaveFailureRestoresPoint(GameTestHelper helper, ServerPlayer admin, RoomId room,
                                                         SpawnPointData requested, SpawnPointData saved) throws Exception {
        var manager = FPSMCore.getInstance().getFPSMDataManager();
        var field = manager.getClass().getDeclaredField("registry");
        field.setAccessible(true);
        var registry = (java.util.Map<Class<?>, com.mojang.datafixers.util.Pair<String,
                com.phasetranscrystal.fpsmatch.core.data.save.ISavePort<?>>>) field.get(manager);
        var dataClass = com.cdp.codpattern.compat.fpsmatch.data.CodTdmMapData.MapData.class;
        var original = registry.get(dataClass);
        var file = ServerMapStorage.get(admin.server).paths().map("builtin/frontline", room.mapName()).resolve("map.json");
        String before = Files.readString(file);
        var broken = new com.phasetranscrystal.fpsmatch.core.data.save.ISavePort<
                com.cdp.codpattern.compat.fpsmatch.data.CodTdmMapData.MapData>() {
            @Override public com.mojang.serialization.Codec<com.cdp.codpattern.compat.fpsmatch.data.CodTdmMapData.MapData> codec() {
                return com.cdp.codpattern.compat.fpsmatch.data.CodTdmMapData.MapData.CODEC;
            }
            @Override public com.google.gson.JsonElement encodeToJson(com.cdp.codpattern.compat.fpsmatch.data.CodTdmMapData.MapData data) {
                throw new IllegalStateException("Injected map save failure");
            }
        };
        try {
            registry.put(dataClass, com.mojang.datafixers.util.Pair.of(original.getFirst(), broken));
            try {
                EndTeleportService.save(admin, room, MapManagementService.revision(admin.server, room), requested);
                throw new AssertionError("save failure was not reported");
            } catch (IllegalStateException expected) { }
            helper.assertTrue(EndTeleportService.read(admin, room).point().orElseThrow().equals(saved)
                    && Files.readString(file).equals(before), "failed map save retains memory and disk point");
        } finally { registry.put(dataClass, original); }
    }

    private static ServerPlayer player(GameTestHelper helper, boolean admin) {
        return MapToolGameTests.player(helper, admin, new ArrayList<>());
    }

    private static void roundTrip(GameTestHelper helper, MapAdminRequestPacket request, MapAdminResponsePacket response) {
        var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            request.encode(buffer);
            helper.assertTrue(MapAdminRequestPacket.decode(buffer).equals(request) && !buffer.isReadable(), "request round trip");
            buffer.clear();
            response.encode(buffer);
            helper.assertTrue(MapAdminResponsePacket.decode(buffer).equals(response) && !buffer.isReadable(), "response round trip");
        } finally { buffer.release(); }
    }
}
