package com.cdp.codpattern.app.match.management;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.GameModeRuntimeProvider;
import com.cdp.codpattern.app.match.GameModeRuntimeRegistry;
import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceRegistry;
import com.cdp.codpattern.app.match.persistence.ModeMapMutationProvider;
import com.cdp.codpattern.app.match.runtime.termination.ForceEndCoordinator;
import com.cdp.codpattern.app.match.runtime.termination.RoomTerminationService;
import com.cdp.codpattern.config.storage.MapStorageRegistration;
import com.cdp.codpattern.config.storage.ServerMapStorage;
import com.cdp.codpattern.config.storage.StorageFiles;
import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Optional;
import java.util.UUID;

/** Server-authoritative, read-only map management facade and force-end adapter. */
public final class MapManagementService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer, UUID> SERVER_SESSIONS = new WeakHashMap<>();
    private static final Map<BaseMap, UUID> MAP_IDENTITIES = new WeakHashMap<>();
    private static final int MAX_REVISION_FILES = 256;
    private static final long MAX_REVISION_BYTES = 32L * 1024L * 1024L;

    private MapManagementService() { }

    public record ModeOption(String id, String displayNameKey) { }

    public record MapSummary(RoomId roomId, String modeNameKey, String status,
                             boolean canRename, boolean canDelete, String disabledReason) { }

    public record MapDetail(MapSummary summary, String dimensionId, String lifecycleStateKey,
                            BlockPos pos1, BlockPos pos2,
                            int sizeX, int sizeY, int sizeZ, Optional<SpawnPointData> endPoint,
                            boolean endPointSupported, UUID generation, String revision) { }

    public record ListResult(List<MapSummary> maps, List<String> errors) {
        public ListResult { maps = List.copyOf(maps); errors = List.copyOf(errors); }
    }

    private static synchronized UUID sessionId(MinecraftServer server) {
        return SERVER_SESSIONS.computeIfAbsent(server, ignored -> UUID.randomUUID());
    }

    private static synchronized UUID mapIdentity(BaseMap map) {
        return MAP_IDENTITIES.computeIfAbsent(map, ignored -> UUID.randomUUID());
    }

    private static void onServerThread(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) throw new IllegalStateException("Map management requires server thread");
    }

    public static List<ModeOption> modes(MinecraftServer server) {
        onServerThread(server);
        if (!FPSMCore.initialized()) return List.of();
        FPSMCore core = FPSMCore.getInstance();
        List<ModeOption> options = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (GameModeRuntimeProvider provider : GameModeRuntimeRegistry.providers()) {
            try {
                String id = GameModeRegistry.canonicalize(provider.gameType());
                if (seen.add(id) && core.checkGameType(id)) {
                    options.add(new ModeOption(id, GameModeRegistry.getOrDefault(id).displayNameKey()));
                }
            } catch (RuntimeException | LinkageError failure) {
                LOGGER.warn("Cannot inspect a registered mode", failure);
            }
        }
        options.sort(Comparator.comparing(ModeOption::id));
        return List.copyOf(options);
    }

    public static List<MapSummary> list(MinecraftServer server) {
        return listWithErrors(server).maps();
    }

    public static ListResult listWithErrors(MinecraftServer server) {
        onServerThread(server);
        if (!FPSMCore.initialized()) return new ListResult(List.of(), List.of());
        List<MapSummary> maps = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        FPSMCore core = FPSMCore.getInstance();
        for (GameModeRuntimeProvider provider : GameModeRuntimeRegistry.providers()) {
            String mode = "unknown";
            try {
                mode = GameModeRegistry.canonicalize(provider.gameType());
                if (!core.checkGameType(mode)) continue;
                Set<String> seen = new HashSet<>();
                String currentMode = mode;
                try (var handles = provider.listRoomHandles()) {
                    handles.forEach(handle -> {
                        if (handle == null || handle.roomId() == null) {
                            errors.add("Mode " + currentMode + " returned an invalid room handle");
                            return;
                        }
                        RoomId id = handle.roomId();
                        if (!currentMode.equals(GameModeRegistry.canonicalize(id.gameType()))) {
                            errors.add("Mode provider returned a room with the wrong mode: " + id);
                            return;
                        }
                        Optional<BaseMap> found = core.getMapByTypeWithName(currentMode, id.mapName());
                        if (found.isEmpty()) {
                            errors.add("Mode " + currentMode + " returned an unregistered room " + id.mapName());
                            return;
                        }
                        if (!seen.add(id.mapName())) {
                            errors.add("Mode " + currentMode + " returned duplicate room " + id.mapName());
                            return;
                        }
                        found.ifPresent(map -> {
                            try { maps.add(summary(server, RoomId.of(currentMode, id.mapName()), map, handle)); }
                            catch (RuntimeException failure) {
                                LOGGER.warn("Cannot inspect registered map {}", id, failure);
                                errors.add("Cannot inspect " + id);
                            }
                        });
                    });
                }
                for (BaseMap registered : core.getAllMaps().getOrDefault(mode, List.of())) {
                    if (!seen.contains(registered.getMapName())) {
                        errors.add("Registered map has no room handle: " + mode + "/" + registered.getMapName());
                    }
                }
            } catch (RuntimeException | LinkageError failure) {
                LOGGER.warn("Cannot list maps for mode {}", mode, failure);
                errors.add("Cannot list mode " + mode);
            }
        }
        maps.sort(Comparator.comparing((MapSummary value) -> value.roomId().gameType())
                .thenComparing(value -> value.roomId().mapName(), String.CASE_INSENSITIVE_ORDER));
        return new ListResult(maps, errors);
    }

    public static boolean isRegistered(MinecraftServer server, RoomId room) {
        onServerThread(server);
        if (room == null || !FPSMCore.initialized()) return false;
        String mode = GameModeRegistry.canonicalize(room.gameType());
        return GameModeRuntimeRegistry.find(mode).isPresent()
                && FPSMCore.getInstance().getMapByTypeWithName(mode, room.mapName()).isPresent();
    }

    public static Optional<MapDetail> detail(MinecraftServer server, RoomId room) {
        onServerThread(server);
        if (!FPSMCore.initialized() || room == null) return Optional.empty();
        String mode = GameModeRegistry.canonicalize(room.gameType());
        if (GameModeRuntimeRegistry.find(mode).isEmpty() || !FPSMCore.getInstance().checkGameType(mode)) {
            return Optional.empty();
        }
        RoomId id = RoomId.of(mode, room.mapName());
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByTypeWithName(mode, id.mapName());
        if (map.isEmpty()) return Optional.empty();
        Optional<ModeRoomHandle> handle = GameModeRuntimeRegistry.find(mode).flatMap(provider -> provider.roomHandle(map.get()));
        if (handle.isEmpty() || !id.equals(handle.get().roomId())) return Optional.empty();
        AreaData area = handle.get().summaryPort().mapArea();
        if (area == null) area = map.get().getMapArea();
        BlockPos one = area.pos1();
        BlockPos two = area.pos2();
        RoomTerminationService termination = RoomTerminationService.get(server);
        MapSummary row = summary(server, id, map.get(), handle.get());
        String managementRevision;
        try { managementRevision = revision(server, id, map.get()); }
        catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("Map {} remains inspectable but its mutation revision is unavailable", id, failure);
            managementRevision = "";
            row = new MapSummary(id, row.modeNameKey(), row.status(), false, false,
                    row.disabledReason().equals("management_pending") ? "management_pending" : "storage_unavailable");
        }
        return Optional.of(new MapDetail(
                row,
                handle.get().summaryPort().dimensionId(), handle.get().summaryPort().lifecycleStateKey(), one, two,
                extent(one.getX(), two.getX()), extent(one.getY(), two.getY()), extent(one.getZ(), two.getZ()),
                configuredEndPoint(handle.get()), handle.get().summaryPort().supportsConfiguredEndPoint(),
                termination.generation(id), managementRevision
        ));
    }

    private static int extent(int a, int b) {
        return Math.addExact(Math.abs(Math.subtractExact(a, b)), 1);
    }

    private static MapSummary summary(MinecraftServer server, RoomId id, BaseMap map, ModeRoomHandle handle) {
        RoomTerminationService termination = RoomTerminationService.get(server);
        var progress = termination.progress(id);
        boolean players = !map.getMapTeams().getJoinedPlayersWithSpec().isEmpty();
        boolean processing = termination.executing(id);
        boolean pending = termination.blocked(id) || progress.onlinePending() > 0
                || progress.pendingEntities() > 0;
        boolean active = termination.valid(id, termination.generation(id));
        boolean occupied = players || termination.hasLease(id) || active;
        String status = processing ? "ending" : pending ? "recovery_pending"
                : handle.summaryPort().isRunning() ? "running" : occupied ? "occupied" : "idle";
        String reason = "";
        if (ServerMapStorage.get(server).managementUnavailable(id.gameType())) reason = "management_pending";
        else if (!"idle".equals(status)) reason = status;
        else if (progress.offlinePending() > 0 || modeResourcesPending(server, id)) reason = "recovery_pending";
        else if (ServerMapStorage.get(server).blocked(id.gameType())) reason = "storage_unavailable";
        else if (ModeMapPersistenceRegistry.find(id.gameType()).isEmpty()) reason = "persistence_unavailable";
        else {
            try {
                MapStorageRegistration registration = ServerMapStorage.get(server).migration().registration(id.gameType());
                Path file = ServerMapStorage.get(server).paths().map(registration.directory(), id.mapName()).resolve("map.json");
                if (!Files.isRegularFile(file)) reason = "source_missing";
            } catch (RuntimeException failure) {
                reason = "storage_unavailable";
            }
        }
        boolean available = reason.isEmpty();
        boolean canRename = available && MapMutationService.supportsRename(server, id);
        boolean canDelete = available && MapMutationService.supportsDelete(server, id);
        if (available && !canRename && !canDelete) reason = "mutation_unsupported";
        return new MapSummary(id, GameModeRegistry.getOrDefault(id.gameType()).displayNameKey(), status,
                canRename, canDelete, reason);
    }

    private static boolean modeResourcesPending(MinecraftServer server, RoomId room) {
        try {
            return ModeMapPersistenceRegistry.find(room.gameType()).filter(ModeMapMutationProvider.class::isInstance)
                    .map(ModeMapMutationProvider.class::cast).map(provider -> provider.hasPendingResources(server, room)).orElse(false);
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("Cannot verify mode-owned cleanup for {}", room, failure);
            return true;
        }
    }

    private static Optional<SpawnPointData> configuredEndPoint(ModeRoomHandle handle) {
        return handle.summaryPort().configuredEndPoint();
    }

    /** A session-bound content revision; validates persisted definition and mode-owned rules. */
    public static String revision(MinecraftServer server, RoomId room) {
        onServerThread(server);
        if (!FPSMCore.initialized() || room == null) throw new IllegalArgumentException("Unknown room");
        BaseMap map = FPSMCore.getInstance().getMapByTypeWithName(room.gameType(), room.mapName())
                .orElseThrow(() -> new IllegalArgumentException("Unknown room " + room));
        return revision(server, room, map);
    }

    private static String revision(MinecraftServer server, RoomId room, BaseMap map) {
        try {
            MapStorageRegistration registration;
            try { registration = ServerMapStorage.get(server).migration().registration(room.gameType()); }
            catch (IllegalArgumentException unsupported) { return "unsupported"; }
            Path root = ServerMapStorage.get(server).paths().map(registration.directory(), room.mapName());
            StorageFiles.checkPath(root);
            if (!Files.isRegularFile(root.resolve("map.json"))) return "unpersisted";
            List<Path> files = new ArrayList<>();
            try (var tree = Files.walk(root)) {
                var iterator = tree.iterator();
                int entries = 0;
                while (iterator.hasNext()) {
                    Path candidate = iterator.next();
                    if (++entries > 512) throw new IllegalStateException("Map has too many entries to inspect");
                    if (!Files.isRegularFile(candidate)) continue;
                    files.add(candidate);
                    if (files.size() > MAX_REVISION_FILES) {
                        throw new IllegalStateException("Map has too many files to inspect");
                    }
                }
            }
            files.sort(Comparator.naturalOrder());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(sessionId(server).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update(mapIdentity(map).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ModeMapPersistenceRegistry.find(room.gameType()).filter(ModeMapMutationProvider.class::isInstance)
                    .map(ModeMapMutationProvider.class::cast).ifPresent(provider -> digest.update(
                            provider.captureDefinition(map).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            long totalBytes = 0L;
            for (Path file : files) {
                StorageFiles.checkPath(file);
                totalBytes = Math.addExact(totalBytes, Files.size(file));
                if (totalBytes > MAX_REVISION_BYTES) throw new IllegalStateException("Map files are too large");
                digest.update(root.relativize(file).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update(HexFormat.of().parseHex(StorageFiles.digest(file)));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot inspect map revision " + room, failure);
        }
    }

    public static ForceEndCoordinator.Report forceEnd(CommandSourceStack source, RoomId room, UUID expectedGeneration) {
        Objects.requireNonNull(source, "source");
        return RoomTerminationService.get(source.getServer()).forceEnd(source, room, expectedGeneration);
    }
}
