package com.cdp.codpattern.app.match.runtime.termination;

import com.cdp.codpattern.app.match.GameModeRuntimeRegistry;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.*;

/** Server-owned termination authority. Modes supply data; shared cleanup executes independently. */
public final class RoomTerminationService {
    private static final Map<MinecraftServer, RoomTerminationService> SERVICES = new IdentityHashMap<>();
    private final MinecraftServer server;
    private final RoomRecoveryStore store;
    private final RoomRecoveryStore.Data data;
    private final ForceEndCoordinator coordinator = new ForceEndCoordinator();
    private final Map<String, Map<UUID, Runnable>> cancellations = new LinkedHashMap<>();
    private final PlayerRecoveryExecutor recovery;
    private final Set<UUID> recovering = new HashSet<>();
    private final Map<UUID, Integer> playerRetryAfter = new HashMap<>();
    private final Map<UUID, Integer> playerRetryDelay = new HashMap<>();
    private String loadFailure;
    private int ticks;
    private final com.cdp.codpattern.app.match.runtime.lease.ModeMapLeaseRegistry leases =
            new com.cdp.codpattern.app.match.runtime.lease.ModeMapLeaseRegistry();
    private final Map<String, Integer> retryAfter = new HashMap<>();
    private final Map<String, Integer> retryDelay = new HashMap<>();
    private final Map<String, String> lastDiagnostic = new HashMap<>();

    private RoomTerminationService(MinecraftServer server) {
        this.server = server;
        store = new RoomRecoveryStore(server.getWorldPath(LevelResource.ROOT).resolve("data/codpattern/room-recovery.json"));
        RoomRecoveryStore.Data loaded;
        try { loaded = store.read(); }
        catch (Exception failure) {
            loaded = new RoomRecoveryStore.Data();
            loadFailure = failure.toString();
            LogUtils.getLogger().error("Cannot load room recovery; admission is disabled", failure);
        }
        data = loaded;
        recovery = new PlayerRecoveryExecutor(server, this::save);
        data.rooms.values().forEach(state -> {
            if (state.active && !state.complete) {
                state.terminated = true;
                state.reason = "SERVER_RECOVERY";
                state.actor = "server";
            }
        });
        data.rooms.forEach((key, state) -> {
            if (state.active && !state.complete) acquireLease(RoomId.decode(key), state.generation);
        });
        data.players.values().forEach(record -> {
            var state = data.rooms.get(record.room);
            if (record.armed && !record.recovered && state != null && state.terminated
                    && state.generation.equals(record.generation)) record.recoveryPending = true;
        });
    }
    public static synchronized RoomTerminationService get(MinecraftServer server) {
        return SERVICES.computeIfAbsent(Objects.requireNonNull(server), RoomTerminationService::new);
    }
    public static Optional<RoomTerminationService> current() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? Optional.empty() : Optional.of(get(server));
    }
    public static RoomId id(BaseMap map) { return RoomId.of(map.getGameType(), map.getMapName()); }
    private void thread() {
        if (!server.isSameThread()) throw new IllegalStateException("Room lifecycle requires the server thread");
    }
    private void save() throws Exception {
        if (loadFailure != null) throw new IllegalStateException(loadFailure);
        store.write(data);
    }
    private void checkpoint() {
        try { save(); }
        catch (Exception failure) { throw new IllegalStateException("Cannot checkpoint room recovery", failure); }
    }
    private ForceEndCoordinator.State state(RoomId room) {
        return data.rooms.computeIfAbsent(room.encode(), ignored -> new ForceEndCoordinator.State());
    }
    public UUID generation(RoomId room) { return state(room).generation; }
    private static com.cdp.codpattern.app.match.runtime.lease.ModeMapLeaseRegistry.LeaseKey leaseKey(RoomId room) {
        return new com.cdp.codpattern.app.match.runtime.lease.ModeMapLeaseRegistry.LeaseKey(
                room.gameType().toLowerCase(Locale.ROOT), room.mapName().toLowerCase(Locale.ROOT));
    }
    public boolean acquireLease(RoomId room, UUID generation) {
        thread();
        if (!state(room).generation.equals(generation)) return false;
        return leases.acquire(leaseKey(room), generation.toString()).acquired();
    }
    public boolean hasLease(RoomId room) { return leases.current(leaseKey(room)).isPresent(); }
    /** No addon may release occupancy while cleanup is incomplete, even with the correct token. */
    public boolean releaseLease(RoomId room, UUID generation) {
        thread();
        if (!state(room).generation.equals(generation) || !state(room).complete) return false;
        return leases.current(leaseKey(room)).filter(lease -> lease.owner().equals(generation.toString()))
                .map(leases::release).orElse(false);
    }
    public ForceEndCoordinator.Report status(RoomId room) {
        var state = state(room);
        return ForceEndCoordinator.report(state, state.executing ? ForceEndCoordinator.Outcome.IN_PROGRESS
                : state.complete ? (state.settled ? ForceEndCoordinator.Outcome.COMPLETED : ForceEndCoordinator.Outcome.SETTLEMENT_FAILED)
                : state.terminated ? ForceEndCoordinator.Outcome.PENDING : ForceEndCoordinator.Outcome.NOTHING_TO_END);
    }
    public record RecoveryProgress(long onlinePending, long offlinePending, long pendingEntities) { }
    public RecoveryProgress progress(RoomId room) {
        long online = 0, offline = 0;
        for (var record : data.players.values()) if (record.room.equals(room.encode()) && record.recoveryPending && !record.recovered) {
            if (server.getPlayerList().getPlayer(record.player) == null) offline++; else online++;
        }
        return new RecoveryProgress(online, offline, data.entities.values().stream().filter(e -> e.room().equals(room.encode())).count());
    }
    public boolean blocked(RoomId room) {
        var state = data.rooms.get(room.encode());
        return loadFailure != null || state != null && state.terminated && !state.complete;
    }
    public boolean terminated(RoomId room) {
        var state = data.rooms.get(room.encode());
        return loadFailure != null || state != null && state.terminated;
    }
    public boolean valid(RoomId room, UUID generation) {
        var state = data.rooms.get(room.encode());
        return loadFailure == null && state != null && state.active && !state.terminated && state.generation.equals(generation);
    }
    public boolean ownsRecovery(UUID player) {
        var record = data.players.get(player);
        return record != null && record.armed && record.recoveryPending;
    }
    public boolean playerPending(UUID player) {
        var record = data.players.get(player);
        return record != null && record.recoveryPending && !record.recovered;
    }
    public boolean canJoin(RoomId room, UUID player) { return !blocked(room) && !playerPending(player); }
    public boolean executing(RoomId room) { return state(room).executing; }

    /** Capture before changing modes, inventories, positions or respawn locations. */
    public void capture(RoomId room, ServerPlayer player) {
        thread();
        if (!canJoin(room, player.getUUID())) throw new IllegalStateException("Player or room recovery pending");
        var old = data.players.get(player.getUUID());
        if (old != null && old.room.equals(room.encode()) && !old.recovered) return;
        if (old != null && old.armed && !old.recovered) throw new IllegalStateException("Conflicting player ownership");
        var state = state(room);
        if (state.complete) {
            state = new ForceEndCoordinator.State();
            data.rooms.put(room.encode(), state);
        }
        state.active = true; // Waiting-room admission also changes player state and needs recovery.
        if (!acquireLease(room, state.generation)) throw new IllegalStateException("Map occupied by another attempt");
        var record = PlayerRecoveryRecord.capture(player, room.encode(), state.generation);
        record.armed = true;
        UUID currentGeneration = state.generation;
        record.endTarget = data.players.values().stream()
                .filter(r -> r.room.equals(room.encode()) && r.generation.equals(currentGeneration) && r.endTarget != null)
                .map(r -> r.endTarget).findFirst().orElse(null);
        data.players.put(player.getUUID(), record);
        checkpoint();
    }
    /** The existing BaseMap contract returns participants to Adventure after leaving a room. */
    public void useAdventureRecovery(ServerPlayer player) {
        var record = data.players.get(player.getUUID());
        if (record == null) throw new IllegalStateException("Capture recovery before changing player state");
        record.restoreGameMode = net.minecraft.world.level.GameType.ADVENTURE.getName();
        checkpoint();
    }
    /** Voting and countdown share the generation with the match they start. */
    public UUID begin(RoomId room, Collection<UUID> players, SpawnPointData endPoint) {
        thread();
        if (blocked(room)) throw new IllegalStateException("Room cleanup pending");
        for (UUID player : players) if (playerPending(player)) throw new IllegalStateException("Player recovery pending");
        var state = state(room);
        if (!state.active || state.complete) {
            state = new ForceEndCoordinator.State();
            state.active = true;
            data.rooms.put(room.encode(), state);
        }
        if (!acquireLease(room, state.generation)) throw new IllegalStateException("Map occupied by another attempt");
        for (UUID id : players) {
            var record = data.players.get(id);
            if (record == null || record.recovered) {
                var player = server.getPlayerList().getPlayer(id);
                if (player == null) throw new IllegalStateException("No recovery evidence for offline player " + id);
                record = PlayerRecoveryRecord.capture(player, room.encode(), state.generation);
                data.players.put(id, record);
            }
            if (!record.room.equals(room.encode())) throw new IllegalStateException("Conflicting player ownership");
            record.generation = state.generation;
            record.armed = true;
            record.endTarget = PlayerRecoveryRecord.Target.of(endPoint);
        }
        checkpoint();
        return state.generation;
    }
    /** Acknowledge normal result-presentation cleanup only after checking and persisting its postcondition. */
    public void acknowledgeInventoryCleared(ServerPlayer player) {
        var record = requireRecord(player);
        if (!record.clearInventory) return;
        if (!player.getInventory().isEmpty()) throw new IllegalStateException("Round inventory was not cleared");
        try {
            PlayerRecoveryPersistence.save(player);
            record.completed.add("inventory");
            save();
        } catch (Exception failure) {
            record.completed.remove("inventory");
            throw new IllegalStateException("Cannot acknowledge inventory cleanup", failure);
        }
    }
    public void registerRoundInventory(ServerPlayer player) {
        var record = requireRecord(player);
        if (!record.clearInventory) { record.clearInventory = true; checkpoint(); }
    }
    public void registerAttribute(ServerPlayer player, String attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier modifier) {
        var record = requireRecord(player);
        var undo = new PlayerRecoveryRecord.AttributeUndo(attribute, modifier.getName(), modifier.getAmount(), modifier.getOperation().toValue());
        if (!Objects.equals(record.attributes.put(modifier.getId().toString(), undo), undo)) checkpoint();
    }
    public void registerPersistentTag(ServerPlayer player, String key) {
        var record = requireRecord(player);
        if (record.persistentTags.add(key)) checkpoint();
    }
    public void registerCustom(ServerPlayer player, String key, String payload) {
        var record = requireRecord(player);
        if (!Objects.equals(record.customActions.put(key, payload), payload)) checkpoint();
    }
    private PlayerRecoveryRecord requireRecord(ServerPlayer player) {
        thread();
        var record = data.players.get(player.getUUID());
        if (record == null || !record.armed || !valid(RoomId.decode(record.room), record.generation))
            throw new IllegalStateException("Missing or terminated match recovery registration");
        return record;
    }
    public Runnable guard(RoomId room, Runnable action) {
        UUID token = generation(room);
        return () -> { if (valid(room, token)) action.run(); };
    }
    public void registerTask(RoomId room, UUID generation, UUID taskId, Runnable cancel) {
        thread();
        if (!valid(room, generation)) throw new IllegalStateException("Stale match task");
        cancellations.computeIfAbsent(room.encode(), ignored -> new LinkedHashMap<>()).put(taskId, cancel);
    }
    private void cancelTasks(RoomId room) {
        var tasks = cancellations.get(room.encode());
        if (tasks == null) return;
        for (var entry : List.copyOf(tasks.entrySet())) {
            try { entry.getValue().run(); tasks.remove(entry.getKey()); }
            catch (Exception | LinkageError failure) { LogUtils.getLogger().warn("Task cancellation failed in {}", room, failure); }
        }
    }
    public void registerEntity(RoomId room, Entity entity) {
        thread();
        if (entity instanceof net.minecraft.world.entity.player.Player)
            throw new IllegalArgumentException("Players require recovery records, not entity reclamation");
        var state = state(room);
        if (state.terminated) { entity.discard(); throw new IllegalStateException("Spawn for terminated match"); }
        state.active = true; // Explicitly registered resources are work even in a waiting room.
        data.entities.put(entity.getUUID(), new RoomRecoveryStore.EntityRecord(entity.getUUID(), room.encode(),
                state.generation, entity.level().dimension().location().toString()));
        entity.getPersistentData().putUUID("codpattern_match_generation", state.generation);
        try { checkpoint(); }
        catch (RuntimeException failure) {
            // Registration must succeed before spawning; never leave an untracked live entity.
            entity.discard();
            throw failure;
        }
    }
    public void acknowledgeEntity(UUID id) {
        thread();
        if (data.entities.remove(id) != null) checkpoint();
    }
    public boolean entityMustBeReclaimed(Entity entity) {
        var record = data.entities.get(entity.getUUID());
        if (record != null) return terminated(RoomId.decode(record.room()));
        String key = entity.getPersistentData().getString("codpattern_room_key");
        if (!entity.getPersistentData().hasUUID("codpattern_match_generation")) return false;
        var state = data.rooms.get(key);
        return state != null && (state.terminated || !state.generation.equals(entity.getPersistentData().getUUID("codpattern_match_generation")));
    }
    public void reclaimLoadedEntity(Entity entity) {
        if (!entityMustBeReclaimed(entity)) return;
        try {
            discardOwnedEntity(entity);
            if (entity.isRemoved()) {
                ModeEntityOwnershipRegistry.instance().unregister(entity);
                acknowledgeEntity(entity.getUUID());
            }
        } catch (Exception | LinkageError failure) {
            // EntityJoinLevelEvent is already cancelled; retain evidence without aborting world loading.
            LogUtils.getLogger().warn("Loaded entity {} reclamation remains pending", entity.getUUID(), failure);
        }
    }
    private void discardOwnedEntity(Entity entity) {
        try { entity.discard(); }
        catch (Exception | LinkageError failure) {
            if (!entity.isRemoved()) throw new IllegalStateException("Entity removal failed", failure);
            LogUtils.getLogger().warn("Entity {} was removed but a removal notification failed", entity.getUUID(), failure);
        }
    }
    private void reclaimEntities(RoomId room, UUID generation) throws Exception {
        List<String> pending = new ArrayList<>();
        for (var record : List.copyOf(data.entities.values())) {
            if (!record.room().equals(room.encode()) || !record.generation().equals(generation)) continue;
            try {
                var level = PlayerRecoveryExecutor.level(server, record.dimension());
                Entity entity = level == null ? null : level.getEntity(record.entity());
                if (entity == null) { pending.add(record.entity().toString()); continue; }
                discardOwnedEntity(entity);
                if (!entity.isRemoved()) throw new IllegalStateException("Removal not confirmed");
                ModeEntityOwnershipRegistry.instance().unregister(entity);
                data.entities.remove(record.entity());
            } catch (Exception | LinkageError failure) { pending.add(record.entity() + ": " + failure); }
        }
        if (!pending.isEmpty()) throw new IllegalStateException("Pending entities: " + String.join(", ", pending));
    }
    public ForceEndCoordinator.Report forceEnd(CommandSourceStack source, RoomId room, UUID expectedGeneration) {
        thread();
        if (source.getServer() != server || !source.hasPermission(2)) throw new SecurityException("Administrator permission required");
        BaseMap map = resolve(room);
        if (map == null) throw new IllegalArgumentException("Unknown registered room " + room);
        var state = state(room);
        if (!state.generation.equals(expectedGeneration)) return reply(source, room, ForceEndCoordinator.report(state, ForceEndCoordinator.Outcome.STALE));
        if (!state.active && !state.terminated) return reply(source, room, ForceEndCoordinator.report(state, ForceEndCoordinator.Outcome.NOTHING_TO_END));
        if (!state.terminated) {
            state.actor = source.getTextName();
            state.reason = "ADMIN_FORCE_END";
            state.terminated = true;
            notifyRoom(room, "message.codpattern.force_end.started", state.actor);
        }
        var report = execute(room, map);
        return reply(source, room, report);
    }
    private ForceEndCoordinator.Report reply(CommandSourceStack source, RoomId room, ForceEndCoordinator.Report report) {
        String key = message(report);
        if (report.outcome() == ForceEndCoordinator.Outcome.PENDING) {
            key = report.failures().containsKey("persistence") ? "message.codpattern.force_end.persistence"
                    : progress(room).onlinePending() > 0 ? "message.codpattern.force_end.players_pending"
                    : "message.codpattern.force_end.resources_pending";
        }
        final String messageKey = key;
        source.sendSuccess(() -> Component.translatable(messageKey, room.mapName()), false);
        if (progress(room).offlinePending() > 0 && !report.failures().containsKey("persistence"))
            source.sendSuccess(() -> Component.translatable("message.codpattern.force_end.offline", progress(room).offlinePending()), false);
        return report;
    }
    /** Durable at-most-once settlement shared by the normal results screen and administrator termination. */
    public void settleNormally(RoomId room, Runnable settlement) {
        thread();
        var state = state(room);
        if (state.executing) { settlement.run(); return; } // coordinator already recorded the attempt
        if (state.settlementAttempted) return;
        state.settlementAttempted = true;
        try {
            checkpoint();
            settlement.run();
            state.settled = true;
        } catch (RuntimeException | LinkageError failure) {
            state.failures.put("settlement", failure.toString());
            throw failure;
        } finally { checkpoint(); }
    }
    public String terminationReason(RoomId room) { return state(room).terminated ? state(room).reason : "NORMAL_END"; }
    public void finishNormally(BaseMap map) {
        thread();
        RoomId room = id(map);
        var state = state(room);
        if (!state.active || state.executing || state.terminated || state.complete) return;
        state.reason = "NORMAL_END";
        execute(room, map);
    }
    private BaseMap resolve(RoomId room) {
        if (!FPSMCore.initialized() || GameModeRuntimeRegistry.find(room.gameType()).isEmpty()) return null;
        return FPSMCore.getInstance().getMapByTypeWithName(room.gameType(), room.mapName()).orElse(null);
    }
    private ForceEndCoordinator.Report execute(RoomId room, BaseMap map) {
        var state = state(room);
        if (state.complete || state.executing) return ForceEndCoordinator.report(state, state.executing
                ? ForceEndCoordinator.Outcome.IN_PROGRESS : state.settled
                ? ForceEndCoordinator.Outcome.COMPLETED : ForceEndCoordinator.Outcome.SETTLEMENT_FAILED);
        state.terminated = true;
        data.players.values().forEach(record -> {
            if (record.room.equals(room.encode()) && record.generation.equals(state.generation)
                    && record.armed && !record.recovered) record.recoveryPending = true;
        });
        cancelTasks(room);
        var context = new ForceEndContext(room, state.generation,
                state.operationId == null ? UUID.randomUUID() : state.operationId, state.actor, state.reason);
        // Resolve failures are also isolated: basic recovery must still run if an addon cannot construct its handle.
        ModeForceEndHandler handler;
        try { handler = Objects.requireNonNull(GameModeRuntimeRegistry.find(room.gameType()).flatMap(p -> p.roomHandle(map))
                .orElseThrow().lifecyclePort().forceEndHandler()); }
        catch (Exception | LinkageError failure) { handler = unavailable(failure); }
        var report = coordinator.execute(state, context, handler, () -> {
            reclaimEntities(room, state.generation);
            if (!cancellations.getOrDefault(room.encode(), Map.of()).isEmpty()) throw new IllegalStateException("Task cancellation pending");
        }, () -> recoverRoom(room, state.generation), this::save);
        if (state.complete) {
            releaseLease(room, state.generation);
            notifyRoom(room, message(report), room.mapName());
            if (progress(room).offlinePending() > 0) notifyRoom(room, "message.codpattern.force_end.offline", Long.toString(progress(room).offlinePending()));
        }
        int delay = state.complete ? 100 : Math.min(1200, retryDelay.getOrDefault(room.encode(), 50) * 2);
        retryDelay.put(room.encode(), delay);
        retryAfter.put(room.encode(), ticks + delay);
        String diagnostic = report.outcome() + ":" + report.failures();
        if (!diagnostic.equals(lastDiagnostic.put(room.encode(), diagnostic)))
            LogUtils.getLogger().info("Termination room={} generation={} operation={} actor={} reason={} outcome={} failures={}",
                    room, state.generation, state.operationId, state.actor, state.reason, report.outcome(), report.failures());
        try { if (map != null) map.syncToClient(); } catch (Exception failure) { LogUtils.getLogger().warn("Room sync failed", failure); }
        return report;
    }
    private ModeForceEndHandler unavailable(Throwable failure) {
        return new ModeForceEndHandler() {
            public com.cdp.codpattern.app.match.model.result.ModeOperationResult<Void> stop(ForceEndContext c) { throw new IllegalStateException(failure); }
            public com.cdp.codpattern.app.match.model.result.ModeOperationResult<Void> cleanup(ForceEndContext c) { throw new IllegalStateException(failure); }
        };
    }
    private void recoverRoom(RoomId room, UUID generation) throws Exception {
        List<String> failures = new ArrayList<>();
        for (var record : List.copyOf(data.players.values())) {
            if (!record.room.equals(room.encode()) || !record.generation.equals(generation) || !record.armed || record.recovered) continue;
            var player = server.getPlayerList().getPlayer(record.player);
            if (player == null) continue; // durable checkpoint below retains offline recovery work
            recoverPlayer(record, player);
            if (!record.recovered) failures.add(record.player + ": " + record.failures);
        }
        save();
        if (!failures.isEmpty()) throw new IllegalStateException(String.join("; ", failures));
    }
    private void recoverPlayer(PlayerRecoveryRecord record, ServerPlayer player) {
        if (!recovering.add(record.player)) return;
        try {
            if (!player.isAlive()) {
                // Use vanilla's respawn path so the connection and player replacement stay consistent.
                player.connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                        net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                player = server.getPlayerList().getPlayer(record.player);
            }
            if (player != null) {
                record.failures.remove("player");
                recovery.recover(record, player);
            }
        } catch (Exception | LinkageError failure) { record.failures.put("player", failure.toString()); }
        finally {
            recovering.remove(record.player);
            int delay = record.recovered ? 100 : Math.min(1200, playerRetryDelay.getOrDefault(record.player, 50) * 2);
            playerRetryDelay.put(record.player, delay);
            playerRetryAfter.put(record.player, ticks + delay);
        }
    }
    public boolean recoverOnLogin(ServerPlayer player) {
        thread();
        if (!playerPending(player.getUUID())) return false;
        var record = data.players.get(player.getUUID());
        recoverPlayer(record, player);
        try { save(); } catch (Exception failure) { record.recovered = false; LogUtils.getLogger().error("Recovery save failed", failure); }
        return true;
    }
    public void leave(ServerPlayer player) {
        var record = data.players.get(player.getUUID());
        if (record == null) return;
        if (record.armed && !record.recovered) {
            record.recoveryPending = true;
            try { checkpoint(); }
            catch (RuntimeException failure) { LogUtils.getLogger().error("Cannot persist leave recovery; attempting online recovery", failure); }
            recoverPlayer(record, player);
            if (!record.recovered) throw new IllegalStateException("Player recovery incomplete: " + record.failures);
        }
        data.players.remove(player.getUUID());
        checkpoint();
    }
    private String message(ForceEndCoordinator.Report report) {
        return switch (report.outcome()) {
            case COMPLETED -> "message.codpattern.force_end.completed";
            case SETTLEMENT_FAILED -> "message.codpattern.force_end.settlement_failed";
            case NOTHING_TO_END -> "message.codpattern.force_end.idle";
            case STALE -> "message.codpattern.force_end.stale";
            case IN_PROGRESS -> "message.codpattern.force_end.in_progress";
            default -> "message.codpattern.force_end.pending";
        };
    }
    private void notifyRoom(RoomId room, String key, String argument) {
        for (var record : data.players.values()) {
            if (!record.room.equals(room.encode()) || !record.generation.equals(state(room).generation)) continue;
            var player = server.getPlayerList().getPlayer(record.player);
            if (player != null) try { player.sendSystemMessage(Component.translatable(key, argument)); }
            catch (RuntimeException | LinkageError failure) { LogUtils.getLogger().warn("Termination notification failed for {}", record.player, failure); }
        }
    }
    public void tick() {
        thread();
        if (loadFailure != null || ++ticks % 100 != 0 || !FPSMCore.initialized()) return;
        for (var entry : List.copyOf(data.rooms.entrySet())) {
            var state = entry.getValue();
            if (!state.terminated || state.complete || state.executing || ticks < retryAfter.getOrDefault(entry.getKey(), 0)) continue;
            try {
                RoomId room = RoomId.decode(entry.getKey());
                BaseMap map = resolve(room);
                execute(room, map); // An unavailable handler still permits independent shared recovery and backoff.
            } catch (Exception failure) { LogUtils.getLogger().warn("Recovery retry failed for {}", entry.getKey(), failure); }
        }
        for (var player : List.copyOf(server.getPlayerList().getPlayers()))
            if (playerPending(player.getUUID()) && ticks >= playerRetryAfter.getOrDefault(player.getUUID(), 0)) recoverOnLogin(player);
    }
    public static synchronized void close(MinecraftServer server) {
        var service = SERVICES.remove(server);
        if (service != null) {
            try { service.save(); } catch (Exception failure) { LogUtils.getLogger().error("Cannot save room recovery at shutdown", failure); }
        }
    }
}
