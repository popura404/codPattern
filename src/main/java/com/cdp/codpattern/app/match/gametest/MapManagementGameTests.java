package com.cdp.codpattern.app.match.gametest;

import com.cdp.codpattern.app.match.management.MapManagementService;
import com.cdp.codpattern.app.match.management.MapMutationService;
import com.cdp.codpattern.app.match.management.MapManagementJournal;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceRegistry;
import com.cdp.codpattern.config.storage.ServerMapStorage;
import com.cdp.codpattern.app.match.runtime.termination.RoomTerminationService;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmMap;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Checks that the administrator catalog follows live registration, not map directories. */
@GameTestHolder("codpattern")
@PrefixGameTestTemplate(false)
public final class MapManagementGameTests {
    private MapManagementGameTests() { }

    @GameTest(template = "empty", batch = "map_management", timeoutTicks = 100)
    public static void registeredMapsSupplyDetailsAndEmptyModesRemainVisible(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        String name = "manage-" + UUID.randomUUID().toString().substring(0, 8);
        RoomId room = RoomId.of("frontline", name);
        BlockPos first = new BlockPos(7, 4, 13);
        BlockPos second = new BlockPos(2, 8, 15);
        var map = new CodTdmMap(helper.getLevel(), name, new AreaData(first, second));
        var core = FPSMCore.getInstance();
        try {
            helper.assertTrue(MapManagementService.modes(server).stream().anyMatch(mode -> mode.id().equals(room.gameType())),
                    "a valid registered mode remains in the filter without maps");
            helper.assertTrue(MapManagementService.detail(server, room).isEmpty(),
                    "an unregistered map instance must not appear in management");
            core.registerMap(room.gameType(), map);
            var listed = MapManagementService.listWithErrors(server);
            helper.assertTrue(listed.errors().isEmpty(), "registered mode discovery should be complete: " + listed.errors());
            helper.assertTrue(listed.maps().stream().anyMatch(entry -> entry.roomId().equals(room)),
                    "registered map is listed by mode and exact name");
            var detail = MapManagementService.detail(server, room).orElseThrow();
            helper.assertTrue(detail.pos1().equals(first) && detail.pos2().equals(second),
                    "details retain both authored boundary coordinates");
            helper.assertTrue(detail.sizeX() == 6 && detail.sizeY() == 5 && detail.sizeZ() == 3,
                    "region dimensions include both boundary blocks");
            helper.assertTrue(detail.endPointSupported() && detail.endPoint().isEmpty(),
                    "supported but unset end point is distinct from unsupported");
            var configured = new SpawnPointData(Level.NETHER, new BlockPos(-12, 74, 36), 45.5F, 12.25F);
            map.setMatchEndTeleportPoint(configured);
            var endpoint = MapManagementService.detail(server, room).orElseThrow().endPoint().orElseThrow();
            helper.assertTrue(endpoint.getDimension().equals(Level.NETHER)
                            && endpoint.getPosition().equals(configured.getPosition())
                            && endpoint.getYaw() == configured.getYaw()
                            && endpoint.getPitch() == configured.getPitch(),
                    "configured cross-dimension end point is reported without substituting a fallback");
            helper.assertTrue(!detail.summary().canRename() && !detail.summary().canDelete(),
                    "a map without a saved definition cannot be renamed or deleted");
            helper.succeed();
        } finally {
            core.unregisterMap(map);
            map.getMapTeams().retireCreatedScoreboardTeams();
        }
    }

    @GameTest(template = "empty", batch = "map_management", timeoutTicks = 200)
    public static void renameDeleteAndConstructorRollback(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var storage = ServerMapStorage.get(server);
        var core = FPSMCore.getInstance();
        String name = "mutation-" + UUID.randomUUID().toString().substring(0, 8);
        RoomId room = RoomId.of("frontline", name);
        RoomId target = RoomId.of("frontline", name + "-新地图");
        var map = new CodTdmMap(helper.getLevel(), name, new AreaData(BlockPos.ZERO, new BlockPos(8, 4, 6)));
        var provider = ModeMapPersistenceRegistry.find(room.gameType()).orElseThrow();
        Path root = storage.paths().map("builtin/frontline", name);
        Path destination = storage.paths().map("builtin/frontline", target.mapName());
        core.registerMap(room.gameType(), map);
        provider.save(map, core.getFPSMDataManager());
        Files.createDirectories(root.resolve("rules"));
        Files.writeString(root.resolve("rules/custom.json"), "{\"sentinel\":37}");
        String revision = MapManagementService.revision(server, room);
        try {
            var point = new SpawnPointData(Level.NETHER, new BlockPos(-4, 72, 14), 12.3F, -9.7F);
            map.setMatchEndTeleportPoint(point);
            helper.assertTrue(MapMutationService.delete(server, room, revision).outcome() == MapMutationService.Outcome.STALE,
                    "unsaved definition edits invalidate old management revisions");
            provider.save(map, core.getFPSMDataManager());
            revision = MapManagementService.revision(server, room);
            Path unsafeLink = root.resolve("rules/revision-link");
            Files.createSymbolicLink(unsafeLink, root.resolve("map.json"));
            var readable = MapManagementService.detail(server, room).orElseThrow();
            helper.assertTrue(!readable.summary().canRename() && !readable.summary().canDelete()
                            && readable.endPoint().isPresent(), "unsafe storage disables mutations without hiding room details");
            helper.assertTrue(MapManagementService.forceEnd(server.createCommandSourceStack(), room, readable.generation()).outcome()
                            == com.cdp.codpattern.app.match.runtime.termination.ForceEndCoordinator.Outcome.NOTHING_TO_END,
                    "storage inspection failure does not block shared force-end");
            Files.delete(unsafeLink);
            helper.assertTrue(MapMutationService.rename(server, room, revision, " ").outcome() == MapMutationService.Outcome.INVALID_NAME,
                    "blank map names are rejected");
            helper.assertTrue(MapMutationService.rename(server, room, revision, name.toUpperCase(java.util.Locale.ROOT)).outcome()
                            == MapMutationService.Outcome.NAME_CONFLICT,
                    "case-only destination aliases are rejected");
            String blockedName = name + "-blocked";
            Path blockedPath = storage.paths().map("builtin/frontline", blockedName);
            Files.createDirectories(blockedPath);
            helper.assertTrue(MapMutationService.rename(server, room, revision, blockedName).outcome() == MapMutationService.Outcome.NAME_CONFLICT,
                    "unloaded destination data is never overwritten");
            Files.delete(blockedPath);
            String occupiedTeam = "frontline_" + target.mapName() + "_spectator";
            var scoreboard = helper.getLevel().getScoreboard();
            var existing = scoreboard.addPlayerTeam(occupiedTeam);
            var failed = MapMutationService.rename(server, room, revision, target.mapName());
            helper.assertTrue(failed.outcome() == MapMutationService.Outcome.FAILED, "constructor conflict rolls back: " + failed);
            helper.assertTrue(core.getMapByTypeWithName(room.gameType(), name).orElseThrow() == map
                            && Files.isRegularFile(root.resolve("map.json")) && !Files.exists(destination),
                    "failed constructor preserves source identity and removes provisional files");
            helper.assertTrue(scoreboard.getPlayerTeam(occupiedTeam) == existing, "pre-existing scoreboard resource survives rollback");
            scoreboard.removePlayerTeam(existing);
            assertSavedFieldLossRollsBack(helper, room, target, map, revision, root, destination);
            var generation = RoomTerminationService.get(server).generation(room);
            try (var reservation = storage.beginManagement(room.gameType())) {
                boolean rejected = false;
                try { MapManagementService.forceEnd(server.createCommandSourceStack(), room, generation); }
                catch (IllegalStateException expected) { rejected = true; }
                helper.assertTrue(rejected, "force-end cannot resolve a reserved map");
            }
            var renamed = MapMutationService.rename(server, room, revision, "  " + target.mapName() + "  ");
            helper.assertTrue(renamed.outcome() == MapMutationService.Outcome.RENAMED, "rename completes: " + renamed);
            helper.assertTrue(core.getMapByTypeWithName(room.gameType(), name).isEmpty() && !Files.exists(root),
                    "old active identity disappears");
            var replacement = core.getMapByTypeWithName(target.gameType(), target.mapName()).orElseThrow();
            var renamedPoint = MapManagementService.detail(server, target).orElseThrow().endPoint().orElseThrow();
            helper.assertTrue(renamedPoint.equals(point) && renamedPoint.getPitch() == point.getPitch(),
                    "configured end point and fractional orientation survive provider definition round-trip");
            helper.assertTrue(Files.readString(destination.resolve("rules/custom.json")).equals("{\"sentinel\":37}"),
                    "mode-owned nested rules survive rename byte-for-byte");
            helper.assertTrue(MapMutationService.delete(server, target, revision).outcome() == MapMutationService.Outcome.STALE,
                    "old identity revision cannot mutate a replacement");
            var deleted = MapMutationService.delete(server, target, MapManagementService.revision(server, target));
            helper.assertTrue(deleted.outcome() == MapMutationService.Outcome.DELETED, "delete completes: " + deleted);
            helper.assertTrue(!Files.exists(destination) && core.getMapByTypeWithName(target.gameType(), target.mapName()).isEmpty(),
                    "delete archives files and unregisters the same identity");
            helper.assertTrue(replacement.getMapTeams().createdScoreboardTeamNames().isEmpty(),
                    "retirement releases exclusively owned teams");
            helper.succeed();
        } finally {
            core.getMapByTypeWithName(target.gameType(), target.mapName()).ifPresent(value -> {
                core.unregisterMap(value);
                value.getMapTeams().retireCreatedScoreboardTeams();
            });
            core.unregisterMap(map);
            map.getMapTeams().retireCreatedScoreboardTeams();
        }
    }

    /** Replace only the save codec adapter during this synchronous test; always restore it. */
    @SuppressWarnings("unchecked")
    private static void assertSavedFieldLossRollsBack(GameTestHelper helper, RoomId room, RoomId target,
                                                     BaseMap original, String revision, Path source, Path destination)
            throws Exception {
        var manager = FPSMCore.getInstance().getFPSMDataManager();
        var field = manager.getClass().getDeclaredField("registry");
        field.setAccessible(true);
        var registry = (java.util.Map<Class<?>, com.mojang.datafixers.util.Pair<String,
                com.phasetranscrystal.fpsmatch.core.data.save.ISavePort<?>>>) field.get(manager);
        var dataClass = com.cdp.codpattern.compat.fpsmatch.data.CodTdmMapData.MapData.class;
        var originalEntry = registry.get(dataClass);
        String originalJson = Files.readString(source.resolve("map.json"));
        var originalTeams = new java.util.HashSet<>(helper.getLevel().getScoreboard().getTeamNames());
        var broken = new com.phasetranscrystal.fpsmatch.core.data.save.ISavePort<
                com.cdp.codpattern.compat.fpsmatch.data.CodTdmMapData.MapData>() {
            @Override public com.mojang.serialization.Codec<com.cdp.codpattern.compat.fpsmatch.data.CodTdmMapData.MapData> codec() {
                return com.cdp.codpattern.compat.fpsmatch.data.CodTdmMapData.MapData.CODEC;
            }
            @Override public com.google.gson.JsonElement encodeToJson(
                    com.cdp.codpattern.compat.fpsmatch.data.CodTdmMapData.MapData data) {
                var json = com.phasetranscrystal.fpsmatch.core.data.save.ISavePort.super.encodeToJson(data).getAsJsonObject();
                json.remove("matchEndTeleportPoint");
                return json;
            }
        };
        try {
            registry.put(dataClass, com.mojang.datafixers.util.Pair.of(originalEntry.getFirst(), broken));
            var result = MapMutationService.rename(helper.getLevel().getServer(), room, revision, target.mapName());
            helper.assertTrue(result.outcome() == MapMutationService.Outcome.FAILED
                            && result.detail().equals("Operation rolled back"), "saved field loss is rolled back: " + result);
            helper.assertTrue(FPSMCore.getInstance().getMapByTypeWithName(room.gameType(), room.mapName()).orElseThrow() == original
                            && FPSMCore.getInstance().getMapByTypeWithName(target.gameType(), target.mapName()).isEmpty(),
                    "save mismatch preserves original registration");
            helper.assertTrue(Files.readString(source.resolve("map.json")).equals(originalJson) && !Files.exists(destination),
                    "save mismatch preserves original bytes and removes provisional destination");
            helper.assertTrue(Files.readString(source.resolve("rules/custom.json")).equals("{\"sentinel\":37}")
                            && originalTeams.equals(new java.util.HashSet<>(helper.getLevel().getScoreboard().getTeamNames())),
                    "save mismatch preserves rules and cleans provisional scoreboard teams");
        } finally {
            registry.put(dataClass, originalEntry);
        }
    }

    @GameTest(template = "empty", batch = "map_management", timeoutTicks = 200)
    public static void administratorTransportRejectsUnauthorizedAndDuplicateChanges(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var core = FPSMCore.getInstance();
        String name = "transport-" + UUID.randomUUID().toString().substring(0, 8);
        var map = new CodTdmMap(helper.getLevel(), name, new AreaData(BlockPos.ZERO, new BlockPos(3, 3, 3)));
        var room = RoomId.of("frontline", name);
        var target = RoomId.of("frontline", name + "-renamed");
        core.registerMap("frontline", map);
        ModeMapPersistenceRegistry.find("frontline").orElseThrow().save(map, core.getFPSMDataManager());
        var denied = new net.minecraft.server.level.ServerPlayer(server, helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "map-reader")) {
            @Override public boolean hasPermissions(int permission) { return false; }
        };
        var administrator = new net.minecraft.server.level.ServerPlayer(server, helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "map-admin")) {
            @Override public boolean hasPermissions(int permission) { return permission <= 2; }
            @Override public net.minecraft.commands.CommandSourceStack createCommandSourceStack() {
                return super.createCommandSourceStack().withPermission(2);
            }
        };
        UUID session = UUID.randomUUID();
        try {
            var list = com.cdp.codpattern.network.map.MapAdminRequestPacket.list(session, 1, 0, 0).process(denied);
            helper.assertTrue(list.code() == com.cdp.codpattern.network.map.MapAdminResponsePacket.Code.DENIED
                            && list.maps().isEmpty() && list.modes().isEmpty(), "unauthorized sender receives no map data");
            String revision = MapManagementService.revision(server, room);
            var request = com.cdp.codpattern.network.map.MapAdminRequestPacket.rename(session, 2, room, revision, target.mapName());
            helper.assertTrue(request.process(denied).code() == com.cdp.codpattern.network.map.MapAdminResponsePacket.Code.DENIED,
                    "unauthorized mutation is rejected");
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                request.encode(buffer);
                helper.assertTrue(com.cdp.codpattern.network.map.MapAdminRequestPacket.decode(buffer).equals(request),
                        "request identity, revision and Unicode survive wire encoding");
            } finally { buffer.release(); }
            var first = request.process(administrator);
            helper.assertTrue(first.result().equals("RENAMED"), "authorized request renames the map: " + first.result());
            helper.assertTrue(request.process(administrator).equals(first), "duplicate request returns original result");
            var changedBody = com.cdp.codpattern.network.map.MapAdminRequestPacket.rename(session, 2, target,
                    MapManagementService.revision(server, target), name + "-other").process(administrator);
            helper.assertTrue(changedBody.code() == com.cdp.codpattern.network.map.MapAdminResponsePacket.Code.STALE,
                    "request ID reuse cannot select a different operation");
            var delete = com.cdp.codpattern.network.map.MapAdminRequestPacket.delete(session, 3, target,
                    MapManagementService.revision(server, target), RoomTerminationService.get(server).generation(target));
            var deleted = delete.process(administrator);
            helper.assertTrue(deleted.result().equals("DELETED") && delete.process(administrator).equals(deleted),
                    "duplicate delete returns original success without a second mutation: " + deleted);
            helper.succeed();
        } finally {
            core.getMapByTypeWithName("frontline", target.mapName()).ifPresent(value -> {
                core.unregisterMap(value);
                value.getMapTeams().retireCreatedScoreboardTeams();
            });
            core.unregisterMap(map);
            map.getMapTeams().retireCreatedScoreboardTeams();
        }
    }

    @GameTest(template = "empty", batch = "map_management", timeoutTicks = 100)
    public static void committedRenameFinishesForwardAndAbsentOwnerIsPreserved(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var storage = ServerMapStorage.get(server);
        String name = "commit-" + UUID.randomUUID().toString().substring(0, 8);
        Path source = storage.paths().map("builtin/frontline", name);
        Path target = storage.paths().map("builtin/frontline", name + "-new");
        Files.createDirectories(source);
        Files.writeString(source.resolve("map.json"), "{}");
        var journal = new MapManagementJournal(server, storage);
        var operation = journal.prepareRename("frontline", name, name + "-new", List.of("map.json"), Set.of());
        Files.createDirectories(target);
        Files.copy(source.resolve("map.json"), target.resolve("map.json"));
        Files.createDirectories(Path.of(operation.backup).getParent());
        Files.move(source, Path.of(operation.backup));
        journal.commit(operation);
        new MapManagementJournal(server, storage).recoverAll();
        helper.assertTrue(!Files.exists(source) && Files.exists(target.resolve("map.json"))
                        && Files.exists(Path.of(operation.retiredBackup).resolve("map.json")),
                "committed rename keeps replacement and retires backup without resurrecting source");
        Files.delete(target.resolve("map.json"));
        Files.delete(target);
        Files.delete(Path.of(operation.retiredBackup).resolve("map.json"));
        Files.delete(Path.of(operation.retiredBackup));

        operation.mode = "absent_fixture";
        operation.owner = "absent_fixture_addon";
        operation.directory = "absent_fixture";
        operation.state = "PREPARED";
        source = storage.paths().map(operation.directory, name);
        target = storage.paths().map(operation.directory, name + "-new");
        operation.source = source.toString();
        operation.destination = target.toString();
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.writeString(source.resolve("map.json"), "{}");
        Files.writeString(target.resolve("map.json"), "{}");
        journal.write(operation);
        new MapManagementJournal(server, storage).recoverAll();
        helper.assertTrue(Files.exists(source.resolve("map.json")) && Files.exists(target.resolve("map.json"))
                        && !storage.blocked("frontline"),
                "absent addon leaves its evidence intact without disabling installed built-in modes");
        Path record = storage.paths().metadata().resolve("management").resolve(operation.id + ".json");
        var malformed = com.google.gson.JsonParser.parseString(Files.readString(record)).getAsJsonObject();
        malformed.remove("state");
        Files.writeString(record, malformed.toString());
        boolean rejected = false;
        try { journal.read(operation); }
        catch (java.io.IOException expected) { rejected = true; }
        helper.assertTrue(rejected, "missing durable decision is not guessed from a default state");
        Files.delete(record);
        Files.delete(source.resolve("map.json")); Files.delete(source);
        Files.delete(target.resolve("map.json")); Files.delete(target);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "map_management", timeoutTicks = 100)
    public static void preparedRenameIsReconciledBeforeReload(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var storage = ServerMapStorage.get(server);
        String name = "recover-" + UUID.randomUUID().toString().substring(0, 8);
        Path source = storage.paths().map("builtin/frontline", name);
        Path target = storage.paths().map("builtin/frontline", name + "-new");
        Files.createDirectories(source);
        Files.writeString(source.resolve("map.json"), "{\"sentinel\":true}");
        var journal = new MapManagementJournal(server, storage);
        var prepared = journal.prepareRename("frontline", name, name + "-new", List.of("map.json"), Set.of());
        Files.createDirectories(target);
        Files.copy(source.resolve("map.json"), target.resolve("map.json"));
        Files.createDirectories(Path.of(prepared.backup).getParent());
        Files.move(source, Path.of(prepared.backup));
        new MapManagementJournal(server, storage).recoverAll();
        helper.assertTrue(Files.readString(source.resolve("map.json")).equals("{\"sentinel\":true}")
                        && !Files.exists(target) && !Files.exists(Path.of(prepared.backup)),
                "restart reconciliation chooses original identity for prepared operations");
        helper.assertTrue(!storage.blocked("frontline"), "completed recovery releases the mode");
        Files.delete(source.resolve("map.json"));
        Files.delete(source);
        helper.succeed();
    }
}
