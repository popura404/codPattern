package com.cdp.codpattern.app.match.management;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.persistence.ModeMapMutationProvider;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceRegistry;
import com.cdp.codpattern.app.match.port.ModeRoomEvictionPort;
import com.cdp.codpattern.app.match.runtime.termination.RoomTerminationService;
import com.cdp.codpattern.config.storage.ServerMapStorage;
import com.cdp.codpattern.config.storage.StorageFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Server-owned, durable intent. The short archive transaction remains in MapMutationEngine.
 * Recovery and membership checks are never bypassed by the deletion admission gate.
 */
public final class MapDeletionCoordinator {
    private static final Map<MinecraftServer, MapDeletionCoordinator> INSTANCES = new IdentityHashMap<>();
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_RECORDS = 128;
    public static final class Stale extends IllegalArgumentException {
        public Stale(String message) { super(message); }
    }
    public enum Stage { PREPARING, ENDING, EVICTING, WAITING_RECOVERY, READY_TO_DELETE, DELETING,
        DELETED, FAILED, RECONFIRM_REQUIRED, CANCELLED }
    public static final class Operation {
        public int version = 1;
        public UUID id, generation, actor, session;
        public long request, created;
        public String mode, name, revision, definition, identity, reason = "";
        public Stage stage;
        public Map<UUID, String> failures = new LinkedHashMap<>();
        transient BaseMap instance;
        transient boolean requestedEnd;
        transient int retryAt, retryDelay = 20, memberCursor;
        public RoomId room() { return RoomId.of(mode, name); }
        public boolean terminal() { return stage == Stage.DELETED || stage == Stage.CANCELLED; }
    }
    public record View(UUID id, RoomId room, Stage stage, String reason, int members, int spectators,
                       int onlinePending, int offlinePending, int entitiesPending) { }
    private record Receipt(UUID id, String mode, String name, boolean committed, String archive) { }
    private final MinecraftServer server;
    private final ServerMapStorage storage;
    private final Path directory;
    private final Map<UUID, Operation> operations = new LinkedHashMap<>();
    private boolean corrupt;
    private UUID finalization;
    private int ticks, cursor;

    private MapDeletionCoordinator(MinecraftServer server) {
        this.server = server;
        storage = ServerMapStorage.get(server);
        directory = storage.paths().metadata().resolve("deletions");
        load();
    }
    public static MapDeletionCoordinator get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, MapDeletionCoordinator::new);
    }
    public static void close(MinecraftServer server) { INSTANCES.remove(server); }
    private void thread() { if (!server.isSameThread()) throw new IllegalStateException("Deletion requires server thread"); }
    private void authorize(CommandSourceStack source) {
        thread();
        if (source.getServer() != server || !source.hasPermission(2)) throw new SecurityException("Administrator required");
    }
    private Operation active(RoomId room) {
        return operations.values().stream().filter(op -> !op.terminal() && op.room().encode().equalsIgnoreCase(room.encode()))
                .findFirst().orElse(null);
    }
    java.util.List<RoomId> pendingRooms() {
        return operations.values().stream().filter(op -> !op.terminal()).map(Operation::room).toList();
    }
    java.util.Optional<MapManagementService.MapDetail> unavailableDetail(RoomId room) {
        Operation op = active(room);
        if (op == null) return java.util.Optional.empty();
        var row = new MapManagementService.MapSummary(room, "", "deletion_pending", false, false, "deletion_pending");
        return java.util.Optional.of(new MapManagementService.MapDetail(row, "", "screen.codpattern.map_admin.map_unavailable",
                net.minecraft.core.BlockPos.ZERO, net.minecraft.core.BlockPos.ZERO, 0, 0, 0, java.util.Optional.empty(), false, op.generation, ""));
    }

    public boolean blocks(RoomId room) { return corrupt || active(room) != null; }
    boolean blocksEdits(RoomId room) {
        Operation op = active(room);
        return corrupt || op != null && !op.id.equals(finalization);
    }
    boolean finalizing(RoomId room, UUID id) {
        Operation op = operations.get(id);
        return id != null && id.equals(finalization) && op != null && op.room().equals(room) && op.stage == Stage.DELETING;
    }
    public boolean canSubmit(RoomId room, BaseMap map) {
        if (blocks(room) || !(map instanceof ModeRoomEvictionPort)
                || !MapMutationService.supportsDelete(server, room) || storage.blocked(room.gameType())) return false;
        try { return Files.isRegularFile(source(room).resolve("map.json")); }
        catch (RuntimeException failure) { return false; }
    }
    private Path source(RoomId room) {
        return storage.paths().map(storage.migration().registration(room.gameType()).directory(), room.mapName());
    }
    private String identity(RoomId room) throws IOException {
        Path path = source(room);
        StorageFiles.checkPath(path);
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        return path + "|" + attributes.creationTime() + "|" + attributes.fileKey();
    }
    private BaseMap resolve(RoomId room) {
        return FPSMCore.initialized() ? FPSMCore.getInstance().getMapByTypeWithName(room.gameType(), room.mapName()).orElse(null) : null;
    }
    public View preview(CommandSourceStack source, RoomId room) {
        View current = find(source, room, null);
        if (current != null) return current;
        BaseMap map = resolve(room);
        if (map == null) return null;
        var progress = RoomTerminationService.get(server).progress(room);
        return new View(null, room, Stage.PREPARING, "", membershipCount(map),
                map.getMapTeams().getSpectatorTeam().getPlayerCount(), (int)progress.onlinePending(),
                (int)progress.offlinePending(), (int)progress.pendingEntities());
    }

    public View find(CommandSourceStack source, RoomId room, UUID id) {
        authorize(source);
        Operation op = id == null ? active(room) : operations.get(id);
        if (op == null || !op.room().equals(room)) return null;
        return view(op);
    }
    public View submit(CommandSourceStack actor, RoomId room, String revision, UUID generation, UUID session, long request) {
        authorize(actor);
        Objects.requireNonNull(room, "room"); Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(generation, "generation"); Objects.requireNonNull(session, "session");
        UUID player = actor.getEntity() == null ? null : actor.getEntity().getUUID();
        for (Operation previous : operations.values()) {
            if (Objects.equals(player, previous.actor) && Objects.equals(session, previous.session) && request == previous.request) {
                if (!previous.room().equals(room) || !previous.revision.equals(revision)
                        || !previous.generation.equals(generation)) throw new Stale("Changed deletion request");
                return view(previous);
            }
        }
        Operation existing = active(room);
        if (existing != null) return view(existing);
        if (corrupt) throw new IllegalStateException("Deletion records require repair");
        BaseMap map = resolve(room);
        if (map == null) throw new IllegalArgumentException("Unknown map");
        var termination = RoomTerminationService.get(server);
        if (!Objects.equals(revision, MapManagementService.revision(server, room))
                || !termination.generation(room).equals(generation)) throw new Stale("Stale deletion confirmation");
        var detail = MapManagementService.detail(server, room).orElseThrow();
        if (!detail.summary().canDelete()) throw new IllegalStateException("Map cannot be deleted: " + detail.summary().disabledReason());
        if (!"idle".equals(detail.summary().status()) && !(map instanceof ModeRoomEvictionPort))
            throw new IllegalStateException("Mode has no eviction adapter");
        if (map instanceof ModeRoomEvictionPort eviction) Set.copyOf(eviction.deletionMembers());
        prune();
        if (operations.size() >= MAX_RECORDS) throw new IllegalStateException("Too many deletion records");
        Operation op = new Operation();
        op.id = UUID.randomUUID(); op.generation = generation; op.actor = player; op.session = session; op.request = request;
        op.created = System.currentTimeMillis(); op.mode = room.gameType(); op.name = room.mapName();
        op.revision = revision; op.definition = MapManagementService.definitionRevision(server, room, map);
        op.instance = map; op.stage = Stage.PREPARING;
        try { op.identity = identity(room); persist(op); }
        catch (IOException failure) { throw new IllegalStateException("Cannot save deletion intent", failure); }
        operations.put(op.id, op); // gate follows durable intent, before any mode callback
        advance(op);
        return view(op);
    }
    public View control(CommandSourceStack source, RoomId room, UUID id, boolean cancel, String revision) {
        authorize(source);
        Operation op = operations.get(id);
        if (op == null || !op.room().equals(room)) throw new IllegalArgumentException("Unknown deletion");
        if (op.terminal()) return view(op);
        if (op.stage == Stage.DELETING) return view(op); // file journal owns an uncertain commit
        if (cancel) { change(op, Stage.CANCELLED, "cancelled"); return view(op); }
        BaseMap map = resolve(room);
        if (map == null || !Objects.equals(revision, MapManagementService.revision(server, room)))
            throw new Stale("Refresh confirmation");
        try {
            if (!sameIdentity(op, map)) { change(op, Stage.RECONFIRM_REQUIRED, "identity_changed"); return view(op); }
            op.instance = map; op.requestedEnd = false; op.retryDelay = 20;
            change(op, Stage.ENDING, "");
            advance(op);
        } catch (IOException failure) { fail(op, "persistence"); }
        return view(op);
    }
    private boolean sameIdentity(Operation op, BaseMap map) throws IOException {
        return map != null && (op.instance == null || op.instance == map)
                && op.identity.equals(identity(op.room()))
                && op.definition.equals(MapManagementService.definitionRevision(server, op.room(), map))
                && op.generation.equals(RoomTerminationService.get(server).generation(op.room()));
    }
    public void tick() {
        thread();
        if (++ticks % 20 != 0 || !FPSMCore.initialized() || corrupt) return;
        var work = operations.values().stream().filter(op -> !op.terminal()).toList();
        for (int count = 0; count < Math.min(4, work.size()); count++) {
            Operation op = work.get(Math.floorMod(cursor++, work.size()));
            if (op.stage != Stage.FAILED && op.stage != Stage.RECONFIRM_REQUIRED && op.stage != Stage.DELETING && ticks >= op.retryAt) {
                Stage before = op.stage; String reason = op.reason;
                advance(op);
                op.retryDelay = before == op.stage && reason.equals(op.reason) ? Math.min(1200, Math.max(20, op.retryDelay) * 2) : 20;
                op.retryAt = ticks + op.retryDelay;
            }
        }
    }
    private void advance(Operation op) {
        try {
            BaseMap map = resolve(op.room());
            if (!sameIdentity(op, map)) { change(op, Stage.RECONFIRM_REQUIRED, "identity_changed"); return; }
            if (storage.blocked(op.mode)) { fail(op, "storage_unavailable"); return; }
            var termination = RoomTerminationService.get(server);
            if (op.stage == Stage.PREPARING || op.stage == Stage.ENDING) {
                change(op, Stage.ENDING, "");
                var result = op.requestedEnd ? termination.status(op.room())
                        : termination.forceEnd(server.createCommandSourceStack(), op.room(), op.generation);
                op.requestedEnd = true;
                switch (result.outcome()) {
                    case STALE -> { change(op, Stage.RECONFIRM_REQUIRED, "stale_generation"); return; }
                    case SETTLEMENT_FAILED -> { fail(op, "settlement_failed"); return; }
                    case PENDING, IN_PROGRESS -> { op.reason = "ending"; persist(op); return; }
                    default -> change(op, Stage.EVICTING, "");
                }
            }
            // Completed recovery is necessary even when forceEnd returned NOTHING_TO_END.
            Map<UUID, String> previousFailures = Map.copyOf(op.failures);
            op.failures.clear();
            if (map instanceof ModeRoomEvictionPort eviction) {
                List<UUID> members = eviction.deletionMembers().stream().sorted().toList();
                int start = members.isEmpty() ? 0 : Math.floorMod(op.memberCursor, members.size());
                int batchSize = Math.min(32, members.size());
                op.memberCursor = start + batchSize;
                for (int index = 0; index < batchSize; index++) {
                    UUID player = members.get((start + index) % members.size());
                    if (!termination.readyForEviction(op.room(), op.generation, player)) {
                        op.failures.put(player, "player_recovery"); continue;
                    }
                    // Refuse to act on an online player now owned by another map.
                    var online = server.getPlayerList().getPlayer(player);
                    if (online != null && FPSMCore.getInstance().getMapByPlayerWithSpec(online)
                            .filter(owner -> owner != map).isPresent()) {
                        op.failures.put(player, "other_room"); continue;
                    }
                    try {
                        eviction.evictRecoveredMember(player);
                        if (eviction.deletionMembers().contains(player)) op.failures.put(player, "eviction_failed");
                        else if (online != null) com.cdp.codpattern.adapter.forge.network.ModNetworkChannel.sendToPlayer(
                                new com.cdp.codpattern.network.match.LeaveRoomResultPacket(true, op.room().encode(), "MAP_DELETED", ""), online);
                    } catch (Exception | LinkageError failure) {
                        op.failures.put(player, "eviction_failed");
                        if (!previousFailures.containsKey(player))
                            LogUtils.getLogger().warn("Deletion {} could not evict member {} from {}", op.id, player, op.room(), failure);
                    }
                }
                if (!eviction.deletionMembers().isEmpty()) {
                    change(op, Stage.WAITING_RECOVERY, op.failures.isEmpty() ? "evicting" : op.failures.values().iterator().next()); return;
                }
            }
            var progress = termination.progress(op.room());
            var provider = (ModeMapMutationProvider) ModeMapPersistenceRegistry.find(op.mode).orElseThrow();
            if (!map.getMapTeams().getJoinedPlayersWithSpec().isEmpty() || termination.blocked(op.room())
                    || termination.hasLease(op.room()) || termination.valid(op.room(), op.generation)
                    || progress.onlinePending() + progress.offlinePending() + progress.pendingEntities() > 0
                    || provider.hasPendingResources(server, op.room())) {
                change(op, Stage.WAITING_RECOVERY, "recovery_pending"); return;
            }
            change(op, Stage.READY_TO_DELETE, "");
            if (!sameIdentity(op, map)) { change(op, Stage.RECONFIRM_REQUIRED, "identity_changed"); return; }
            change(op, Stage.DELETING, "");
            finalization = op.id;
            MapMutationService.Result result;
            try { result = MapMutationEngine.deleteCoordinated(server, op.room(), MapManagementService.revision(server, op.room()), op.id); }
            finally { finalization = null; }
            if (result.outcome() == MapMutationService.Outcome.DELETED) change(op, Stage.DELETED, "");
            else if (result.outcome() == MapMutationService.Outcome.RECOVERY_REQUIRED) {
                change(op, Stage.DELETING, "restart_required"); // retain gate until journal reconciliation
            } else fail(op, result.outcome().name().toLowerCase(Locale.ROOT));
        } catch (Exception | LinkageError failure) {
            LogUtils.getLogger().error("Deletion {} stopped for {}", op.id, op.room(), failure);
            if (op.stage == Stage.DELETING) {
                try { change(op, Stage.DELETING, "restart_required"); }
                catch (RuntimeException ignored) { corrupt = true; }
            } else fail(op, "operation_failed");
        }
    }
    private void change(Operation op, Stage stage, String reason) {
        Stage old = op.stage; String previous = op.reason;
        op.stage = stage; op.reason = reason;
        try { persist(op); }
        catch (IOException failure) { op.stage = old; op.reason = previous; throw new IllegalStateException("Cannot save deletion stage", failure); }
        if (op.terminal()) {
            MapManagementService.invalidateRevision(op.instance);
            MapManagementService.invalidateRevision(resolve(op.room()));
            op.instance = null;
        }
        com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager.getInstance().markRoomListDirty();
        if (stage == Stage.DELETED || stage == Stage.FAILED || stage == Stage.RECONFIRM_REQUIRED
                || stage == Stage.DELETING && !reason.isEmpty()) {
            Component message = Component.translatable("screen.codpattern.map_admin.deletion_notification", op.name, op.id.toString(),
                    Component.translatable("screen.codpattern.map_admin.deletion." + stage.name()),
                    Component.translatable("screen.codpattern.map_admin.deletion_reason." + (reason.isEmpty() ? "none" : reason)));
            server.createCommandSourceStack().sendSuccess(() -> message, false);
            var player = op.actor == null ? null : server.getPlayerList().getPlayer(op.actor);
            if (player != null) player.sendSystemMessage(message);
        }
    }
    private void fail(Operation op, String reason) {
        try { change(op, Stage.FAILED, reason); }
        catch (RuntimeException failure) { op.stage = Stage.FAILED; op.reason = "persistence"; corrupt = true; }
    }
    private static int membershipCount(BaseMap map) {
        Set<UUID> members = new HashSet<>(map.getMapTeams().getJoinedPlayersWithSpec());
        if (map instanceof ModeRoomEvictionPort eviction) {
            try { members.addAll(eviction.deletionMembers()); }
            catch (RuntimeException | LinkageError unavailable) {
                // Keep failure/cancel controls readable; advance() reports adapter failures and retains the map.
            }
        }
        return members.size();
    }
    private View view(Operation op) {
        if (op.terminal()) return new View(op.id, op.room(), op.stage, op.reason, 0, 0, 0, 0, 0);
        BaseMap map = resolve(op.room());
        int members = map == null ? 0 : membershipCount(map);
        int spectators = map == null ? 0 : map.getMapTeams().getSpectatorTeam().getPlayerCount();
        var progress = RoomTerminationService.get(server).progress(op.room());
        return new View(op.id, op.room(), op.stage, op.reason, members, spectators,
                (int)Math.min(Integer.MAX_VALUE, progress.onlinePending()), (int)Math.min(Integer.MAX_VALUE, progress.offlinePending()),
                (int)Math.min(Integer.MAX_VALUE, progress.pendingEntities()));
    }
    private void persist(Operation op) throws IOException {
        Path file = directory.resolve(op.id + ".json");
        StorageFiles.write(file, JSON.toJson(op));
        try (var channel = java.nio.channels.FileChannel.open(file, java.nio.file.StandardOpenOption.WRITE)) { channel.force(true); }
    }
    private void load() {
        try {
            StorageFiles.checkPath(directory);
            if (!Files.isDirectory(directory)) return;
            List<Path> files;
            try (var stream = Files.list(directory)) { files = stream.filter(p -> p.toString().endsWith(".json")).limit(MAX_RECORDS + 1).toList(); }
            if (files.size() > MAX_RECORDS) throw new IOException("Too many deletion records");
            for (Path file : files) {
                StorageFiles.checkPath(file);
                if (Files.size(file) > 1024 * 1024) throw new IOException("Deletion record too large");
                var encoded = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                for (String field : List.of("version", "id", "generation", "session", "mode", "name", "revision", "definition", "identity", "stage", "created", "failures"))
                    if (!encoded.has(field) || encoded.get(field).isJsonNull()) throw new IOException("Incomplete deletion record");
                Operation op = JSON.fromJson(encoded, Operation.class);
                if (op.version != 1 || op.stage == null || op.reason == null || op.failures.size() > 1024
                        || !file.equals(directory.resolve(op.id + ".json"))) throw new IOException("Invalid deletion record");
                op.room();
                if (!op.terminal()) {
                    if (op.stage != Stage.DELETING) op.stage = Stage.RECONFIRM_REQUIRED;
                    op.reason = op.stage == Stage.DELETING ? "restart_required" : "server_restarted";
                }
                operations.put(op.id, op);
            }
        } catch (Exception failure) { corrupt = true; LogUtils.getLogger().error("Cannot load deletion intents; admission disabled", failure); }
    }
    /** Called after archive journal recovery and map loading, never from a status query. */
    public void reconcile() {
        for (Operation op : operations.values()) {
            if (op.terminal()) continue;
            try {
                Path path = directory.resolve("receipts").resolve(op.id + ".json");
                StorageFiles.checkPath(path);
                if (!Files.isRegularFile(path)) {
                    if (op.stage == Stage.DELETING && !storage.blocked(op.mode) && sameIdentity(op, resolve(op.room())))
                        change(op, Stage.RECONFIRM_REQUIRED, "server_restarted");
                    continue;
                }
                if (Files.size(path) > 16384) throw new IOException("Receipt too large");
                var encoded = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                for (String field : List.of("id", "mode", "name", "committed", "archive"))
                    if (!encoded.has(field) || encoded.get(field).isJsonNull()) throw new IOException("Incomplete archive receipt");
                if (!encoded.get("committed").getAsJsonPrimitive().isBoolean()) throw new IOException("Invalid archive decision");
                Receipt receipt = JSON.fromJson(encoded, Receipt.class);
                if (!receipt.id.equals(op.id) || !receipt.mode.equals(op.mode) || !receipt.name.equals(op.name)) throw new IOException("Receipt mismatch");
                if (receipt.committed) {
                    Path archive = Path.of(receipt.archive);
                    if (!archive.normalize().startsWith(storage.paths().metadata().resolve("trash"))) throw new IOException("Invalid archive path");
                    StorageFiles.checkPath(archive);
                    if (resolve(op.room()) != null || Files.exists(source(op.room()))
                            || !archive.getFileName().toString().equals(com.cdp.codpattern.config.storage.MapStoragePaths.mapDirectory(op.name))
                            || !Files.isRegularFile(archive.resolve("map.json"))) throw new IOException("Archive identity conflict");
                    change(op, Stage.DELETED, "");
                } else if (!storage.blocked(op.mode)) change(op, Stage.RECONFIRM_REQUIRED, "server_restarted");
            } catch (Exception failure) {
                // An uncertain file commit cannot be cancelled or retried as a new archive.
                try { change(op, Stage.DELETING, "receipt_invalid"); }
                catch (RuntimeException persistenceFailure) { corrupt = true; }
                LogUtils.getLogger().error("Cannot reconcile deletion {}", op.id, failure);
            }
        }
    }
    static void writeReceipt(ServerMapStorage storage, MapManagementJournal.Operation op, boolean committed) throws IOException {
        UUID id = UUID.fromString(op.deletionId);
        Path file = storage.paths().metadata().resolve("deletions/receipts").resolve(id + ".json");
        StorageFiles.write(file, JSON.toJson(new Receipt(id, op.mode, op.oldName, committed, op.archiveReceipt.target())));
        try (var channel = java.nio.channels.FileChannel.open(file, java.nio.file.StandardOpenOption.WRITE)) { channel.force(true); }
    }
    private void prune() {
        Iterator<Operation> iterator = operations.values().iterator();
        while (iterator.hasNext()) {
            Operation op = iterator.next();
            if (!op.terminal() || System.currentTimeMillis() - op.created < 7L * 24 * 3600 * 1000) continue;
            try {
                Files.deleteIfExists(directory.resolve("receipts").resolve(op.id + ".json"));
                Files.deleteIfExists(directory.resolve(op.id + ".json")); iterator.remove();
            } catch (IOException failure) { LogUtils.getLogger().warn("Cannot expire deletion {}", op.id, failure); }
        }
    }
}
