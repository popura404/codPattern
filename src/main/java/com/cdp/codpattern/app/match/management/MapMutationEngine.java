package com.cdp.codpattern.app.match.management;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.GameModeRuntimeRegistry;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.persistence.MapDefinitionCodec;
import com.cdp.codpattern.app.match.persistence.ModeMapMutationProvider;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceRegistry;
import com.cdp.codpattern.app.match.runtime.termination.RoomTerminationService;
import com.cdp.codpattern.config.storage.ServerMapStorage;
import com.cdp.codpattern.config.storage.StorageFiles;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import static com.cdp.codpattern.app.match.management.MapMutationService.Outcome;
import static com.cdp.codpattern.app.match.management.MapMutationService.Result;

/** One bounded server-thread operation, with a durable decision before retirement. */
final class MapMutationEngine {
    private static final int MAX_ENTRIES = 512;
    private static final long MAX_BYTES = 32L * 1024 * 1024;
    private MapMutationEngine() { }

    static Result rename(MinecraftServer server, RoomId room, String revision, String name) {
        String normalized;
        try { normalized = normalizeName(name); }
        catch (IllegalArgumentException invalid) { return result(Outcome.INVALID_NAME, room, room, invalid.getMessage()); }
        return mutate(server, room, revision, normalized);
    }

    static Result delete(MinecraftServer server, RoomId room, String revision) {
        return mutate(server, room, revision, null);
    }

    static String normalizeName(String name) {
        var validation = MapNameValidation.validate(name);
        if (!validation.valid()) throw new IllegalArgumentException("Invalid map name: " + validation.status());
        return validation.normalized();
    }

    private static Result mutate(MinecraftServer server, RoomId requested, String revision, String newName) {
        if (!server.isSameThread()) throw new IllegalStateException("Map mutations require the server thread");
        if (requested == null || !FPSMCore.initialized()) return result(Outcome.NOT_FOUND, requested, requested, "Unknown map");
        RoomId room = RoomId.of(GameModeRegistry.canonicalize(requested.gameType()), requested.mapName());
        RoomId target = newName == null ? null : RoomId.of(room.gameType(), newName);
        FPSMCore core = FPSMCore.getInstance();
        BaseMap original = core.getMapByTypeWithName(room.gameType(), room.mapName()).orElse(null);
        if (original == null || GameModeRuntimeRegistry.find(room.gameType()).isEmpty())
            return result(Outcome.NOT_FOUND, room, room, "Unknown registered map");
        var candidate = ModeMapPersistenceRegistry.find(room.gameType()).orElse(null);
        if (!(candidate instanceof ModeMapMutationProvider provider))
            return result(Outcome.UNSUPPORTED, room, room, "Mode has no mutation adapter");
        ServerMapStorage storage = ServerMapStorage.get(server);
        if (storage.blocked(room.gameType())) return result(Outcome.STORAGE_UNAVAILABLE, room, room, "Storage is unavailable");
        MapManagementJournal journal = new MapManagementJournal(server, storage);
        MapManagementJournal.Operation operation = null;
        BaseMap replacement = null;
        boolean changedRegistration = false;
        try {
            var registration = storage.migration().registration(room.gameType());
            Path source = storage.paths().map(registration.directory(), room.mapName());
            if (!Files.isRegularFile(source.resolve("map.json")))
                return result(Outcome.SOURCE_MISSING, room, room, "Saved definition is missing");
            if (!Objects.equals(revision, MapManagementService.revision(server, room)))
                return result(Outcome.STALE, room, room, "Refresh the selected map");
            var detail = MapManagementService.detail(server, room).orElseThrow();
            if (!detail.summary().status().equals("idle") || !detail.summary().disabledReason().isEmpty())
                return result(Outcome.IN_USE, room, room, "Map has active players, occupancy, or recovery");
            if (target != null && target.equals(room)) return result(Outcome.UNCHANGED, room, room, "Name unchanged");
            if (target != null && (destinationOccupied(server, storage, target) || provider.hasPendingResources(server, target)))
                return result(Outcome.NAME_CONFLICT, room, room, "Destination name, data, or retained recovery already exists");
            List<Path> entries = entries(source);
            try (var reservation = storage.beginManagement(room.gameType())) {
                try {
                    if (target != null) {
                        var definition = provider.captureDefinition(original).deepCopy();
                        var renamed = MapDefinitionCodec.withName(definition, target.mapName());
                        registration.validateMap().accept(renamed.deepCopy());
                        operation = journal.prepareRename(room.gameType(), room.mapName(), target.mapName(),
                                entries.stream().filter(Files::isRegularFile).map(p -> source.relativize(p).toString()).toList(),
                                original.getMapTeams().createdScoreboardTeamNames());
                        Path destination = Path.of(operation.destination);
                        Files.createDirectory(destination);
                        for (Path entry : entries) {
                            Path output = destination.resolve(source.relativize(entry));
                            if (Files.isDirectory(entry)) Files.createDirectory(output);
                            else Files.copy(entry, output);
                        }
                        StorageFiles.write(destination.resolve("map.json"), renamed.toString());
                        var prepared = operation;
                        try (var tracking = MapMutationResources.track(name -> journal.recordProvisionalTeam(prepared, name))) {
                            replacement = provider.createRenamed(original.getServerLevel(), definition, target.mapName());
                        }
                        if (replacement == original || !target.mapName().equals(replacement.getMapName())
                                || !target.gameType().equals(replacement.getGameType()))
                            throw new IllegalStateException("Replacement identity mismatch");
                        requireDefinition(room, target, "replacement", renamed, provider.captureDefinition(replacement));
                        provider.save(replacement, core.getFPSMDataManager());
                        var persisted = JsonParser.parseString(Files.readString(destination.resolve("map.json"))).getAsJsonObject();
                        requireDefinition(room, target, "saved", renamed, persisted);
                        registration.validateMap().accept(persisted.deepCopy());
                        verifyRules(source, destination, entries);
                        if (!Objects.equals(revision, MapManagementService.revision(server, room)))
                            throw new IllegalStateException("Source changed during replacement preparation");
                        Files.createDirectories(Path.of(operation.backup).getParent());
                        Files.move(source, Path.of(operation.backup));
                        if (!core.unregisterMap(original)) throw new IllegalStateException("Original registration changed");
                        changedRegistration = true;
                        core.registerMap(target.gameType(), replacement);
                        if (core.getMapByTypeWithName(target.gameType(), target.mapName()).orElse(null) != replacement)
                            throw new IllegalStateException("Replacement registration failed");
                    } else {
                        var receipt = storage.migration().prepareArchive(room.gameType(), room.mapName());
                        operation = journal.prepareDelete(room.gameType(), room.mapName(), receipt,
                                original.getMapTeams().createdScoreboardTeamNames());
                        storage.migration().archive(receipt);
                        if (!core.unregisterMap(original)) throw new IllegalStateException("Original registration changed");
                        changedRegistration = true;
                    }
                    journal.commit(operation);
                    provider.retire(original);
                    journal.finishCommitted(operation);
                    return result(target == null ? Outcome.DELETED : Outcome.RENAMED, room, target, "");
                } catch (Exception | LinkageError failure) {
                    LogUtils.getLogger().error("Map mutation failed for {}", room, failure);
                    if (operation == null) return result(Outcome.FAILED, room, room, "Preparation failed");
                    try {
                        // A thrown commit write does not prove whether the decision reached disk.
                        var durable = journal.read(operation);
                        if (durable.state.equals("COMMITTED") || durable.state.equals("COMPLETE")) {
                            reservation.unresolved();
                            return result(Outcome.RECOVERY_REQUIRED, room, target, "Committed operation requires restart cleanup");
                        }
                        if (changedRegistration) {
                            if (replacement != null && core.getMapByTypeWithName(room.gameType(), replacement.getMapName()).orElse(null) == replacement)
                                core.unregisterMap(replacement);
                            core.registerMap(room.gameType(), original);
                            if (core.getMapByTypeWithName(room.gameType(), room.mapName()).orElse(null) != original)
                                throw new IllegalStateException("Cannot restore original registration");
                        }
                        journal.rollbackPrepared(durable);
                        return result(Outcome.FAILED, room, room, "Operation rolled back");
                    } catch (Exception | LinkageError recoveryFailure) {
                        failure.addSuppressed(recoveryFailure);
                        reservation.unresolved();
                        LogUtils.getLogger().error("Map mutation requires recovery for {}", room, failure);
                        return result(Outcome.RECOVERY_REQUIRED, room, room, "Restart recovery required");
                    }
                }
            }
        } catch (IllegalStateException busy) {
            LogUtils.getLogger().warn("Map mutation preflight failed for {}", room, busy);
            return result(Outcome.STORAGE_UNAVAILABLE, room, room, "Map storage or management is unavailable");
        } catch (Exception | LinkageError failure) {
            LogUtils.getLogger().warn("Map mutation preflight failed for {}", room, failure);
            return result(Outcome.FAILED, room, room, "Preflight failed");
        }
    }

    private static void requireDefinition(RoomId room, RoomId target, String stage,
                                          com.google.gson.JsonObject expected, com.google.gson.JsonObject actual) {
        var comparison = MapDefinitionComparison.compare(expected, actual);
        if (comparison.matches()) return;
        LogUtils.getLogger().error("Map definition mismatch: mode={}, oldName={}, newName={}, stage={}, differences={}, truncated={}",
                room.gameType(), room.mapName(), target.mapName(), stage, comparison.differences(), comparison.truncated());
        throw new IllegalStateException(stage.equals("saved")
                ? "Saved map definition differs from expected" : "Replacement map definition differs from expected");
    }

    private static boolean destinationOccupied(MinecraftServer server, ServerMapStorage storage, RoomId target) throws IOException {
        if (FPSMCore.getInstance().getMapNamesWithType(target.gameType()).stream()
                .anyMatch(name -> name.equalsIgnoreCase(target.mapName()))) return true;
        if (RoomTerminationService.get(server).hasRecordedIdentity(target)) return true;
        if (storage.migration().blocksMap(target.gameType(), target.mapName())) return true;
        Path mode = storage.paths().mode(storage.migration().registration(target.gameType()).directory());
        StorageFiles.checkPath(mode);
        if (!Files.isDirectory(mode)) return false;
        try (var children = Files.list(mode)) {
            var iterator = children.iterator();
            while (iterator.hasNext()) {
                String directory = iterator.next().getFileName().toString();
                if (!directory.startsWith("m-")) continue;
                try {
                    String name = new String(HexFormat.of().parseHex(directory.substring(2)), StandardCharsets.UTF_8);
                    if (name.equalsIgnoreCase(target.mapName())) return true;
                } catch (IllegalArgumentException ignored) { /* Invalid directories are not map identities. */ }
            }
        }
        return false;
    }

    private static List<Path> entries(Path root) throws IOException {
        StorageFiles.checkPath(root);
        List<Path> entries = new ArrayList<>();
        long bytes = 0;
        try (var walk = Files.walk(root)) {
            var iterator = walk.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (path.equals(root)) continue;
                StorageFiles.checkPath(path);
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Unsupported map entry");
                entries.add(path);
                if (Files.isRegularFile(path)) bytes = Math.addExact(bytes, Files.size(path));
                if (entries.size() > MAX_ENTRIES || bytes > MAX_BYTES) throw new IOException("Map exceeds mutation size limit");
            }
        }
        entries.sort(java.util.Comparator.naturalOrder());
        return entries;
    }

    private static void verifyRules(Path source, Path destination, List<Path> original) throws IOException {
        var copied = entries(destination).stream().map(destination::relativize).toList();
        if (!copied.equals(original.stream().map(source::relativize).toList()))
            throw new IOException("Constructor changed the map file manifest");
        for (Path path : original) {
            if (Files.isRegularFile(path) && !source.relativize(path).toString().equals("map.json")
                    && !StorageFiles.digest(path).equals(StorageFiles.digest(destination.resolve(source.relativize(path)))))
                throw new IOException("Constructor changed a copied rule file");
        }
    }

    private static Result result(Outcome outcome, RoomId oldRoom, RoomId newRoom, String detail) {
        return new Result(outcome, oldRoom, newRoom, detail);
    }
}
