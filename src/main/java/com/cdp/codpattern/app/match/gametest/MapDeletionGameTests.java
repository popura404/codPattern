package com.cdp.codpattern.app.match.gametest;

import com.cdp.codpattern.app.match.management.*;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceRegistry;
import com.cdp.codpattern.app.match.runtime.termination.*;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmMap;
import com.cdp.codpattern.config.storage.ServerMapStorage;
import com.mojang.authlib.GameProfile;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.*;

@GameTestHolder("codpattern")
@PrefixGameTestTemplate(false)
public final class MapDeletionGameTests {
    private static String name() { return "delete-" + UUID.randomUUID().toString().substring(0, 8); }
    private static AreaData area() { return new AreaData(BlockPos.ZERO, new BlockPos(8, 8, 8)); }
    private static void register(CodTdmMap map) {
        FPSMCore.getInstance().registerMap(map.getGameType(), map);
        ModeMapPersistenceRegistry.find(map.getGameType()).orElseThrow().save(map, FPSMCore.getInstance().getFPSMDataManager());
    }
    private static MapDeletionCoordinator.View submit(CodTdmMap map) {
        var server = map.getServerLevel().getServer(); var room = RoomTerminationService.id(map);
        return MapDeletionCoordinator.get(server).submit(server.createCommandSourceStack(), room,
                MapManagementService.revision(server, room), RoomTerminationService.get(server).generation(room), UUID.randomUUID(), 1);
    }
    private static void tick(MapDeletionCoordinator service) { for (int n = 0; n < 20; n++) service.tick(); }

    @GameTest(template = "empty", batch = "map_deletion", timeoutTicks = 200)
    public static void occupiedDeletionEvictsAfterRecoveryAndBlocksNewAdmission(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer(); var service = MapDeletionCoordinator.get(server);
        boolean[] fail = {true};
        var map = new CodTdmMap(helper.getLevel(), name(), area()) {
            @Override public void evictRecoveredMember(UUID player) {
                if (fail[0]) { fail[0] = false; throw new IllegalStateException("injected leave failure"); }
                super.evictRecoveredMember(player);
            }
        };
        register(map); var room = RoomTerminationService.id(map); var recovery = RoomTerminationService.get(server);
        var player = player(helper); online(player, true);
        try {
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
            map.joinSpec(player);
            helper.assertTrue(map.getMapTeams().getJoinedPlayersWithSpec().contains(player.getUUID()), "spectator joined waiting room");
            var generation = recovery.generation(room);
            var ended = recovery.forceEnd(server.createCommandSourceStack(), room, generation);
            helper.assertTrue(ended.outcome() == ForceEndCoordinator.Outcome.COMPLETED, "force end completes: " + ended);
            helper.assertTrue(map.getMapTeams().getJoinedPlayersWithSpec().contains(player.getUUID()), "standalone force end retains membership");
            UUID session = UUID.randomUUID(); String revision = MapManagementService.revision(server, room);
            var first = service.submit(server.createCommandSourceStack(), room, revision, generation, session, 1);
            helper.assertTrue(first.stage() == MapDeletionCoordinator.Stage.WAITING_RECOVERY, "failed eviction retains operation: " + first);
            helper.assertTrue(service.blocks(room) && !recovery.canJoin(room, UUID.randomUUID()), "server rejects new admission");
            helper.assertTrue(service.submit(server.createCommandSourceStack(), room, revision, generation, session, 1).id().equals(first.id()), "same request reuses operation");
            helper.assertTrue(service.submit(server.createCommandSourceStack(), room, revision, generation, UUID.randomUUID(), 2).id().equals(first.id()), "second admin shares operation");
            try { recovery.begin(room, List.of(), null); throw new AssertionError("start accepted during deletion"); }
            catch (IllegalStateException expected) { }
            helper.assertTrue(MapMutationService.rename(server, room, MapManagementService.revision(server, room), name()).outcome()
                    == MapMutationService.Outcome.IN_USE, "rename cannot bypass gate");
            try { ServerMapStorage.get(server).requireCreate(room.gameType(), room.mapName()); throw new AssertionError("name reused during deletion"); }
            catch (IllegalStateException expected) { }
            tick(service);
            var done = service.find(server.createCommandSourceStack(), room, first.id());
            helper.assertTrue(done.stage() == MapDeletionCoordinator.Stage.DELETED, "eviction retry archives map: " + done);
            helper.assertTrue(map.deletionMembers().isEmpty() && !FPSMCore.getInstance().isRegistered(map), "roster cleared before unregister");
            helper.assertTrue(player.getInventory().getItem(0).is(Items.DIAMOND), "eviction does not repeat ordinary leave inventory clearing");
            helper.assertTrue(service.submit(server.createCommandSourceStack(), room, revision, generation, session, 1).stage()
                    == MapDeletionCoordinator.Stage.DELETED, "duplicate completion survives missing map");
            var request = com.cdp.codpattern.network.map.MapAdminRequestPacket.deletionControl(
                    com.cdp.codpattern.network.map.MapAdminRequestPacket.Operation.DELETE_STATUS, session, 3, room, first.id(), "");
            var response = com.cdp.codpattern.network.map.MapAdminResponsePacket.status(request,
                    com.cdp.codpattern.network.map.MapAdminResponsePacket.Code.OK, "DELETED").withDeletion(done);
            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try { response.encode(buf); helper.assertTrue(response.equals(com.cdp.codpattern.network.map.MapAdminResponsePacket.decode(buf)), "deletion status wire round trip"); }
            finally { buf.release(); }
            helper.succeed();
        } finally { online(player, false); FPSMCore.getInstance().unregisterMap(map); }
    }

    @GameTest(template = "empty", batch = "map_deletion", timeoutTicks = 200)
    public static void offlineRecoverySurvivesRosterResetBeforeDeletion(GameTestHelper helper) throws Exception {
        var map = new CodTdmMap(helper.getLevel(), name(), area()); register(map);
        var server = helper.getLevel().getServer(); var room = RoomTerminationService.id(map);
        var service = MapDeletionCoordinator.get(server); var recovery = RoomTerminationService.get(server);
        var player = player(helper); // absent from PlayerList: disconnected before termination
        try {
            map.joinSpec(player);
            var pending = submit(map);
            helper.assertTrue(pending.stage() == MapDeletionCoordinator.Stage.WAITING_RECOVERY && pending.offlinePending() == 1,
                    "offline evidence prevents delete even if reset removes roster: " + pending);
            helper.assertTrue(FPSMCore.getInstance().isRegistered(map), "map retained for offline recovery");
            online(player, true);
            helper.assertTrue(recovery.recoverOnLogin(player) && !recovery.playerPending(player.getUUID()), "login restores pending player");
            tick(service);
            helper.assertTrue(service.find(server.createCommandSourceStack(), room, pending.id()).stage() == MapDeletionCoordinator.Stage.DELETED,
                    "delete continues after login recovery");
            helper.succeed();
        } finally { online(player, false); FPSMCore.getInstance().unregisterMap(map); }
    }

    @GameTest(template = "empty", batch = "map_deletion", timeoutTicks = 200)
    public static void restartRequiresConfirmationAndCancellationOnlyReleasesDeletionGate(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer(); boolean[] fail = {true};
        var map = new CodTdmMap(helper.getLevel(), name(), area()) {
            @Override public void evictRecoveredMember(UUID player) {
                if (fail[0]) throw new IllegalStateException("injected removal failure");
                super.evictRecoveredMember(player);
            }
        };
        register(map); var room = RoomTerminationService.id(map); var player = player(helper); online(player, true);
        try {
            map.joinSpec(player); var pending = submit(map);
            helper.assertTrue(pending.stage() == MapDeletionCoordinator.Stage.WAITING_RECOVERY, "waits before restart");
            MapDeletionCoordinator.close(server);
            var restarted = MapDeletionCoordinator.get(server);
            tick(restarted);
            helper.assertTrue(restarted.find(server.createCommandSourceStack(), room, pending.id()).stage() == MapDeletionCoordinator.Stage.RECONFIRM_REQUIRED,
                    "reload never silently continues deletion");
            helper.assertTrue(restarted.blocks(room), "restart preserves admission gate");
            var cancelled = restarted.control(server.createCommandSourceStack(), room, pending.id(), true, "");
            helper.assertTrue(cancelled.stage() == MapDeletionCoordinator.Stage.CANCELLED && !restarted.blocks(room), "cancel releases own gate");
            helper.assertTrue(RoomTerminationService.get(server).terminated(room), "cancel does not resurrect ended match");
            fail[0] = false;
            var done = submit(map);
            helper.assertTrue(done.stage() == MapDeletionCoordinator.Stage.DELETED, "new explicit confirmation can finish deletion");
            helper.succeed();
        } finally { online(player, false); FPSMCore.getInstance().unregisterMap(map); }
    }

    @GameTest(template = "empty", batch = "map_deletion", timeoutTicks = 200)
    public static void settlementFailureRetainsMapWithoutRepeatingSettlement(GameTestHelper helper) {
        int[] attempts = {0};
        var map = new CodTdmMap(helper.getLevel(), name(), area()) {
            @Override public ModeForceEndHandler forceEndHandler() {
                var normal = super.forceEndHandler();
                return new ModeForceEndHandler() {
                    public com.cdp.codpattern.app.match.model.result.ModeOperationResult<Void> stop(ForceEndContext c) { return normal.stop(c); }
                    public com.cdp.codpattern.app.match.model.result.ModeOperationResult<Void> cleanup(ForceEndContext c) { return normal.cleanup(c); }
                    public com.cdp.codpattern.app.match.model.result.ModeOperationResult<Void> settle(ForceEndContext c) {
                        attempts[0]++; throw new IllegalStateException("injected settlement failure");
                    }
                };
            }
        };
        register(map); var server = helper.getLevel().getServer(); var room = RoomTerminationService.id(map);
        var service = MapDeletionCoordinator.get(server);
        try {
            map.startGame(); var failed = submit(map);
            helper.assertTrue(failed.stage() == MapDeletionCoordinator.Stage.FAILED && failed.reason().equals("settlement_failed"), "settlement failure blocks delete");
            var retry = service.control(server.createCommandSourceStack(), room, failed.id(), false, MapManagementService.revision(server, room));
            helper.assertTrue(retry.stage() == MapDeletionCoordinator.Stage.FAILED && attempts[0] == 1, "retry never repeats settlement");
            helper.assertTrue(FPSMCore.getInstance().isRegistered(map), "failed deletion preserves map");
            service.control(server.createCommandSourceStack(), room, failed.id(), true, "");
            helper.succeed();
        } finally { FPSMCore.getInstance().unregisterMap(map); }
    }

    @GameTest(template = "empty", batch = "map_deletion", timeoutTicks = 200)
    public static void archiveCommitSurvivesMissingCoordinatorAcknowledgement(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer(); var storage = ServerMapStorage.get(server);
        UUID member = UUID.randomUUID();
        var map = new CodTdmMap(helper.getLevel(), name(), area()) {
            @Override public Set<UUID> deletionMembers() { return Set.of(member); }
            @Override public void evictRecoveredMember(UUID ignored) { throw new IllegalStateException("fixture pending member"); }
        };
        register(map); var room = RoomTerminationService.id(map);
        try {
            var pending = submit(map);
            helper.assertTrue(pending.stage() == MapDeletionCoordinator.Stage.WAITING_RECOVERY, "durable operation established");
            // Reproduce a crash after the file commit but before the coordinator receives its result.
            var journal = new MapManagementJournal(server, storage);
            var receipt = storage.migration().prepareArchive(room.gameType(), room.mapName());
            var transaction = journal.prepareDelete(room.gameType(), room.mapName(), receipt,
                    map.getMapTeams().createdScoreboardTeamNames(), pending.id());
            storage.migration().archive(receipt);
            FPSMCore.getInstance().unregisterMap(map);
            journal.commit(transaction);
            MapDeletionCoordinator.close(server);
            journal.recoverAll();
            var restarted = MapDeletionCoordinator.get(server); restarted.reconcile();
            var done = restarted.find(server.createCommandSourceStack(), room, pending.id());
            helper.assertTrue(done.stage() == MapDeletionCoordinator.Stage.DELETED && !restarted.blocks(room),
                    "committed journal evidence completes old operation: " + done);
            helper.assertTrue(java.nio.file.Files.isRegularFile(java.nio.file.Path.of(receipt.target()).resolve("map.json")), "archived definition retained");
            helper.succeed();
        } finally { FPSMCore.getInstance().unregisterMap(map); }
    }

    @GameTest(template = "empty", batch = "map_deletion", timeoutTicks = 200)
    public static void staleConfirmationHasNoSideEffectsAndOtherMapsRemainUsable(GameTestHelper helper) {
        var server = helper.getLevel().getServer(); var map = new CodTdmMap(helper.getLevel(), name(), area()); register(map);
        var other = new CodTdmMap(helper.getLevel(), name(), area()); register(other);
        var room = RoomTerminationService.id(map); var recovery = RoomTerminationService.get(server);
        var service = MapDeletionCoordinator.get(server);
        try {
            String revision = MapManagementService.revision(server, room); UUID generation = recovery.generation(room);
            map.startGame();
            try { service.submit(server.createCommandSourceStack(), room, revision, generation, UUID.randomUUID(), 1);
                throw new AssertionError("stale generation accepted"); }
            catch (IllegalArgumentException expected) { }
            helper.assertFalse(recovery.terminated(room) || service.blocks(room), "stale confirmation cannot stop current match or set gate");
            helper.assertTrue(MapManagementService.detail(server, room).orElseThrow().summary().canDelete(), "occupied map exposes coordinated deletion");
            var entity = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(helper.getLevel());
            com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry.instance().register(room, entity);
            var pending = submit(map);
            helper.assertTrue(pending.stage() == MapDeletionCoordinator.Stage.ENDING, "pending resource holds deletion");
            other.startGame(); var otherRoom = RoomTerminationService.id(other);
            helper.assertTrue(recovery.valid(otherRoom, recovery.generation(otherRoom)), "another map of the same mode can start");
            recovery.forceEnd(server.createCommandSourceStack(), otherRoom, recovery.generation(otherRoom));
            service.control(server.createCommandSourceStack(), room, pending.id(), true, "");
            helper.assertFalse(service.blocks(room), "cancel releases deletion gate");
            helper.assertTrue(recovery.blocked(room) && !recovery.canJoin(room, UUID.randomUUID()), "cancel does not bypass unfinished recovery");
            recovery.reclaimLoadedEntity(entity);
            recovery.forceEnd(server.createCommandSourceStack(), room, recovery.generation(room));
            helper.succeed();
        } finally { FPSMCore.getInstance().unregisterMap(map); FPSMCore.getInstance().unregisterMap(other); }
    }

    @GameTest(template = "empty", batch = "map_deletion", timeoutTicks = 200)
    public static void bothBuiltInModesDeleteMembersAndSpectatorsDuringCountdown(GameTestHelper helper) throws Exception {
        for (boolean tactical : List.of(false, true)) {
            CodTdmMap map = tactical
                    ? new com.cdp.codpattern.compat.fpsmatch.map.CodTacticalTdmMap(helper.getLevel(), name(), area())
                    : new CodTdmMap(helper.getLevel(), name(), area());
            map.addTeam("red", 8); register(map);
            var member = player(helper); var spectator = player(helper);
            online(member, true); online(spectator, true);
            try {
                map.join("red", member); map.joinSpec(spectator); map.startGame();
                helper.assertTrue(map.getMapTeams().getJoinedPlayersWithSpec().size() == 2, "fixture includes member and spectator");
                var done = submit(map);
                helper.assertTrue(done.stage() == MapDeletionCoordinator.Stage.DELETED, map.getGameType() + " countdown deletion: " + done);
                helper.assertTrue(map.deletionMembers().isEmpty(), "both roles evicted");
            } finally { online(member, false); online(spectator, false); FPSMCore.getInstance().unregisterMap(map); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "map_deletion", timeoutTicks = 200)
    public static void failingMembersDoNotStarveLaterEvictionBatches(GameTestHelper helper) {
        Set<UUID> members = new HashSet<>();
        for (int n = 1; n <= 40; n++) members.add(new UUID(0, n));
        var map = new CodTdmMap(helper.getLevel(), name(), area()) {
            @Override public Set<UUID> deletionMembers() { return Set.copyOf(members); }
            @Override public void evictRecoveredMember(UUID player) {
                if (player.getLeastSignificantBits() <= 32) throw new IllegalStateException("fixture blocked member");
                members.remove(player);
            }
        };
        register(map); var server = helper.getLevel().getServer(); var room = RoomTerminationService.id(map);
        var service = MapDeletionCoordinator.get(server);
        try {
            var pending = submit(map);
            helper.assertTrue(members.size() == 40, "first batch retains failed members");
            tick(service);
            helper.assertTrue(members.size() == 32, "later healthy members progress despite earlier failures");
            helper.assertTrue(FPSMCore.getInstance().isRegistered(map), "failed members keep map registered");
            service.control(server.createCommandSourceStack(), room, pending.id(), true, "");
            helper.succeed();
        } finally { FPSMCore.getInstance().unregisterMap(map); }
    }

    @GameTest(template = "empty", batch = "map_deletion", timeoutTicks = 200)
    public static void cancelledConfirmationCannotStartAnotherDeletion(GameTestHelper helper) {
        UUID member = UUID.randomUUID();
        var map = new CodTdmMap(helper.getLevel(), name(), area()) {
            @Override public Set<UUID> deletionMembers() { return Set.of(member); }
            @Override public void evictRecoveredMember(UUID ignored) { throw new IllegalStateException("fixture pending member"); }
        };
        register(map); var server = helper.getLevel().getServer(); var room = RoomTerminationService.id(map);
        var service = MapDeletionCoordinator.get(server); var recovery = RoomTerminationService.get(server);
        try {
            String oldRevision = MapManagementService.revision(server, room); UUID generation = recovery.generation(room);
            var pending = service.submit(server.createCommandSourceStack(), room, oldRevision, generation, UUID.randomUUID(), 1);
            service.control(server.createCommandSourceStack(), room, pending.id(), true, "");
            try {
                // Fresh request identity represents a replay after the old request record/cache has expired.
                service.submit(server.createCommandSourceStack(), room, oldRevision, generation, UUID.randomUUID(), 2);
                throw new AssertionError("cancelled confirmation reused after request identity changed");
            } catch (MapDeletionCoordinator.Stale expected) { }
            helper.assertTrue(FPSMCore.getInstance().isRegistered(map) && !service.blocks(room), "stale request has no new effects");
            helper.succeed();
        } finally { FPSMCore.getInstance().unregisterMap(map); }
    }

    /** Fixture registers only the online lookup, without a network client or normal player tick. */
    @SuppressWarnings("unchecked")
    public static void online(ServerPlayer player, boolean online) throws Exception {
        for (var field : net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType()) && field.getGenericType().getTypeName().contains("UUID")
                    && field.getGenericType().getTypeName().contains("ServerPlayer")) {
                field.setAccessible(true);
                Map<UUID, ServerPlayer> players = (Map<UUID, ServerPlayer>)field.get(player.server.getPlayerList());
                if (online) players.put(player.getUUID(), player); else players.remove(player.getUUID());
                return;
            }
        }
        throw new IllegalStateException("PlayerList fixture lookup not found");
    }
    public static ServerPlayer player(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "del-" + UUID.randomUUID().toString().substring(0, 8)));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) { }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { }
            @Override public void teleport(double x, double y, double z, float yaw, float pitch) { player.moveTo(x,y,z,yaw,pitch); }
            @Override public void teleport(double x, double y, double z, float yaw, float pitch, Set<RelativeMovement> flags) { player.moveTo(x,y,z,yaw,pitch); }
        };
        BlockPos point = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(point.below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(point, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(point.above(), Blocks.AIR.defaultBlockState());
        player.moveTo(point.getX() + .5, point.getY(), point.getZ() + .5, 0, 0);
        return player;
    }
}
