package com.cdp.codpattern.config.storage;

import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server-scoped lifecycle, detection cache and migration maintenance guard. */
public final class ServerMapStorage {
    private static final Map<MinecraftServer, ServerMapStorage> INSTANCES = new IdentityHashMap<>();
    private final MinecraftServer server;
    private final MapStorageMigration migration;
    private final MapDefaultsStore defaults;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "codpattern-map-storage"); thread.setDaemon(true); return thread;
    });
    private final Set<String> locked = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean commandBusy = new AtomicBoolean();
    private String managementMode;
    private Thread managementOwner;
    private boolean managementBlockedAll;
    private final Set<String> managementRecovery = new HashSet<>();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile boolean closing;
    private volatile long checkedAt;
    private volatile MapStorageMigration.Detection detection = new MapStorageMigration.Detection(false, true, "Detection pending");

    private ServerMapStorage(MinecraftServer server) {
        this.server = server;
        migration = new MapStorageMigration(new MapStoragePaths(server.getWorldPath(LevelResource.ROOT), FMLLoader.getGamePath()),
                server.getWorldData().getLevelName());
        defaults = new MapDefaultsStore(migration.paths().defaults());
    }

    public static synchronized ServerMapStorage get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(Objects.requireNonNull(server), ServerMapStorage::new);
    }
    public static ServerMapStorage current() { return get(ServerLifecycleHooks.getCurrentServer()); }
    public MapStorageMigration migration() { return migration; }
    public MapDefaultsStore defaults() { return defaults; }
    public MapStoragePaths paths() { return migration.paths(); }

    public synchronized boolean managementReserved(String mode) {
        return managementMode != null && managementMode.equals(mode);
    }

    public synchronized void blockManagement(String mode) { managementRecovery.add(mode); }

    public synchronized boolean managementUnavailable(String mode) {
        return managementBlockedAll || managementRecovery.contains(mode) || managementReserved(mode);
    }

    public synchronized void blockAllManagement(String reason) {
        managementBlockedAll = true;
        LogUtils.getLogger().error("Map management recovery blocks all maps: {}", reason);
    }

    public synchronized ManagementReservation beginManagement(String mode) {
        if (closing || commandBusy.get() || managementMode != null || blocked(mode)) throw new IllegalStateException("Map management is busy or unavailable");
        managementMode = mode;
        managementOwner = Thread.currentThread();
        return new ManagementReservation(mode);
    }

    public final class ManagementReservation implements AutoCloseable {
        private final String mode;
        private boolean closed;
        private boolean unresolved;
        private ManagementReservation(String mode) { this.mode = mode; }
        public void unresolved() { unresolved = true; }
        @Override public void close() {
            synchronized (ServerMapStorage.this) {
                if (closed) return;
                closed = true;
                if (unresolved) managementRecovery.add(mode);
                managementMode = null;
                managementOwner = null;
            }
        }
    }

    public synchronized boolean blocked(String mode) {
        return managementUnavailable(mode) || locked.contains(mode)
                || ((mode.equals("frontline") || mode.equals("teamdeathmatch")) && migration.commonRulesPending());
    }

    public void requireAvailable(String mode) {
        boolean owner;
        synchronized (this) { owner = managementReserved(mode) && managementOwner == Thread.currentThread(); }
        if (blocked(mode) && !owner) throw new IllegalStateException("Map storage is awaiting migration/management: " + mode);
    }

    public void requireCreate(String mode, String name) {
        requireAvailable(mode);
        if (com.cdp.codpattern.app.match.management.MapDeletionCoordinator.get(server)
                .blocks(com.cdp.codpattern.app.match.model.RoomId.of(mode, name)))
            throw new IllegalStateException("Map deletion is pending");
        if (managementReserved(mode)) throw new IllegalStateException("Map management is in progress");
        MapStorageRegistration registration = migration.registration(mode);
        MapStoragePaths.mapDirectory(name);
        try { StorageFiles.checkPath(paths().map(registration.directory(), name)); }
        catch (java.io.IOException e) { throw new IllegalStateException("Unsafe map directory", e); }
        if (java.nio.file.Files.exists(paths().map(registration.directory(), name))
                || migration.hasLegacyMap(mode, name) || migration.blocksMap(mode, name)) {
            throw new IllegalStateException("Map already exists or requires migration: " + name);
        }
    }

    /** Preserve a failed, newly-created directory outside the active map scan. */
    public void abandonCreation(String mode, String name) {
        try {
            var source = paths().map(migration.registration(mode).directory(), name);
            if (!java.nio.file.Files.exists(source)) return;
            StorageFiles.checkPath(source);
            var target = paths().metadata().resolve("failed-creations").resolve(UUID.randomUUID().toString());
            StorageFiles.checkPath(target);
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.move(source, target);
        } catch (Exception e) { LogUtils.getLogger().error("Cannot archive failed map creation {}/{}", mode, name, e); }
    }

    public static boolean canUse(String mode) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null || !get(server).blocked(mode);
    }

    public void refresh() {
        if (closing || !refreshing.compareAndSet(false, true)) return;
        worker.execute(() -> {
            try { detection = migration.detect(); checkedAt = System.currentTimeMillis(); }
            finally { refreshing.set(false); }
        });
    }

    /** Invoked only for client list requests, never server pushes. */
    public void onLobbyRequest() {
        if (System.currentTimeMillis() - checkedAt > 30_000) refresh();
        MapStorageMigration.Detection snapshot = detection;
        if (migration.shouldNotifyAutomatically(snapshot)) broadcast("message.codpattern.storage.legacy");
    }

    public void broadcast(String key, Object... args) {
        Component message = Component.translatable(key, args).withStyle(style -> style.withClickEvent(
                new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND,
                        "/cdp map migrate check")));
        server.getPlayerList().getPlayers().forEach(player -> player.sendSystemMessage(message));
        LogUtils.getLogger().warn(consoleMessage(key, args));
    }

    private static final Map<String, String> CONSOLE_MESSAGES = loadConsoleMessages();

    private static Map<String, String> loadConsoleMessages() {
        try (var stream = ServerMapStorage.class.getResourceAsStream("/assets/codpattern/lang/zh_cn.json")) {
            if (stream == null) return Map.of();
            var object = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                    stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> messages = new HashMap<>();
            object.entrySet().forEach(entry -> messages.put(entry.getKey(), entry.getValue().getAsString()));
            return Map.copyOf(messages);
        } catch (Exception e) { return Map.of(); }
    }

    private static String consoleMessage(String key, Object... args) {
        return String.format(Locale.ROOT, CONSOLE_MESSAGES.getOrDefault(key,
                "[COD Pattern] Legacy map storage requires attention. Back up the save and fpsmatch, then run /cdp map migrate check. (" + key + ")"), args);
    }

    public void command(net.minecraft.commands.CommandSourceStack source, boolean execute) {
        if (closing || !commandBusy.compareAndSet(false, true)) {
            source.sendFailure(Component.translatable("message.codpattern.storage.busy")); return;
        }
        source.sendSuccess(() -> Component.translatable("message.codpattern.storage.checking"), false);
        worker.execute(() -> {
            try {
                MapStorageMigration.Plan plan = migration.inspect();
                server.execute(() -> {
                    source.sendSuccess(() -> Component.literal("World: " + paths().world() + "\nLegacy: " + paths().legacy()
                            + "\nMigration units: " + plan.units().size()), false);
                    for (var unit : plan.units().stream().limit(30).toList()) {
                        source.sendSuccess(() -> Component.literal(unit.id() + " (" + unit.files().size() + " files)"), false);
                        for (var file : unit.files().stream().limit(5).toList()) source.sendSuccess(() -> Component.literal(file.source() + " -> " + file.target()), false);
                    }
                    plan.problems().stream().limit(30).forEach(problem -> source.sendFailure(Component.literal(problem)));
                    if (plan.units().size() > 30 || plan.problems().size() > 30) {
                        source.sendSuccess(() -> Component.literal("Preview truncated: " + plan.units().size() + " units, "
                                + plan.problems().size() + " problems. No conflicting units will be migrated."), false);
                    }
                    if (!execute || (plan.units().isEmpty() && !migration.hasMigrationRecord())) {
                        commandBusy.set(false); refresh(); return;
                    }
                    Set<String> modes = plan.modes();
                    boolean occupied = FPSMCore.initialized() && FPSMCore.getInstance().getAllMaps().values().stream()
                            .flatMap(Collection::stream).anyMatch(map -> modes.contains(map.getGameType())
                                    && (map.isStart || !map.getMapTeams().getJoinedPlayersWithSpec().isEmpty()));
                    if (closing || occupied) {
                        source.sendFailure(Component.translatable("message.codpattern.storage.busy"));
                        commandBusy.set(false); return;
                    }
                    locked.addAll(modes);
                    Map<String, String> versions = new LinkedHashMap<>();
                    ModList.get().getMods().forEach(mod -> versions.put(mod.getModId(), mod.getVersion().toString()));
                    worker.execute(() -> {
                        MapStorageMigration.Result result;
                        try { result = migration.execute(plan, versions, () -> closing); }
                        catch (Exception e) { result = new MapStorageMigration.Result(0, 1, false, e.toString()); }
                        MapStorageMigration.Result finished = result;
                        // Keep all affected modes locked until restart, including partial commits.
                        detection = migration.detect(); checkedAt = System.currentTimeMillis();
                        commandBusy.set(false);
                        server.execute(() -> {
                            if (!finished.detail().isBlank()) {
                                source.sendFailure(Component.literal(finished.detail()));
                                LogUtils.getLogger().error("Map migration: {}", finished.detail());
                            }
                            if (finished.failed() == 0 && finished.markerWritten()) broadcast("message.codpattern.storage.complete");
                            else broadcast("message.codpattern.storage.partial", finished.completed(), finished.failed());
                            source.sendSuccess(() -> Component.literal("Report: " + migration.journalPath() + "\nMarker: " + migration.markerPath()), false);
                        });
                    });
                });
            } catch (Exception e) {
                commandBusy.set(false);
                server.execute(() -> source.sendFailure(Component.literal("Migration preflight failed: " + e.getMessage())));
            }
        });
    }

    public static synchronized void close(MinecraftServer server) {
        ServerMapStorage storage = INSTANCES.get(server);
        if (storage == null) return;
        storage.closing = true;
        storage.worker.shutdown();
        try {
            if (!storage.worker.awaitTermination(30, TimeUnit.SECONDS)) storage.worker.shutdownNow();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); storage.worker.shutdownNow(); }
        INSTANCES.remove(server);
    }
}
