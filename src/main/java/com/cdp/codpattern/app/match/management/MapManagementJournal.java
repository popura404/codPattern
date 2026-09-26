package com.cdp.codpattern.app.match.management;

import com.cdp.codpattern.config.storage.MapStorageMigration;
import com.cdp.codpattern.config.storage.MapStoragePaths;
import com.cdp.codpattern.config.storage.ServerMapStorage;
import com.cdp.codpattern.config.storage.StorageFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Small durable undo record for a single map rename or archive. */
public final class MapManagementJournal {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int VERSION = 1;
    private static final int MAX_RECORDS = 128;

    public static final class Operation {
        public int version = VERSION;
        public String id;
        public String kind;
        public String state = "PREPARED";
        public String mode;
        public String directory;
        public String owner;
        public String oldName;
        public String newName;
        public String source;
        public String destination;
        public String backup;
        public String retiredBackup;
        public List<String> copiedFiles = new ArrayList<>();
        public List<String> provisionalTeams = new ArrayList<>();
        public List<String> retiringTeams = new ArrayList<>();
        public MapStorageMigration.ArchiveReceipt archiveReceipt;
    }

    private final MinecraftServer server;
    private final ServerMapStorage storage;
    private final Path directory;

    public MapManagementJournal(MinecraftServer server, ServerMapStorage storage) {
        this.server = server;
        this.storage = storage;
        directory = storage.paths().metadata().resolve("management");
    }

    public Operation prepareRename(String mode, String oldName, String newName, List<String> copiedFiles,
                                   Set<String> retiringTeams) throws IOException {
        Operation op = base("RENAME", mode, oldName);
        op.newName = newName;
        op.destination = storage.paths().map(storage.migration().registration(mode).directory(), newName).toString();
        op.backup = directory.resolve("backups").resolve(op.id).resolve(MapStoragePaths.mapDirectory(oldName)).toString();
        op.retiredBackup = storage.paths().metadata().resolve("trash").resolve("rename-" + op.id)
                .resolve(MapStoragePaths.mapDirectory(oldName)).toString();
        op.copiedFiles = new ArrayList<>(copiedFiles);
        op.retiringTeams = new ArrayList<>(retiringTeams);
        write(op);
        return op;
    }

    public Operation prepareDelete(String mode, String name, MapStorageMigration.ArchiveReceipt receipt,
                                   Set<String> retiringTeams) throws IOException {
        Operation op = base("DELETE", mode, name);
        op.archiveReceipt = receipt;
        op.retiringTeams = new ArrayList<>(retiringTeams);
        write(op);
        return op;
    }

    private Operation base(String kind, String mode, String name) {
        Operation op = new Operation();
        op.id = UUID.randomUUID().toString();
        op.kind = kind;
        op.mode = mode;
        op.directory = storage.migration().registration(mode).directory();
        op.owner = storage.migration().registration(mode).owner();
        op.oldName = name;
        op.source = storage.paths().map(storage.migration().registration(mode).directory(), name).toString();
        return op;
    }

    private Path file(Operation op) { return directory.resolve(op.id + ".json"); }

    public void write(Operation op) throws IOException {
        validate(op);
        StorageFiles.write(file(op), JSON.toJson(op));
        try (var channel = java.nio.channels.FileChannel.open(file(op), java.nio.file.StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    public void recordProvisionalTeam(Operation op, String name) {
        if (op.provisionalTeams.contains(name)) return;
        op.provisionalTeams.add(name);
        try { write(op); }
        catch (IOException failure) { throw new IllegalStateException("Cannot journal constructor resource", failure); }
    }

    public void commit(Operation op) throws IOException {
        op.state = "COMMITTED";
        write(op);
    }

    public Operation read(Operation op) throws IOException { return read(file(op)); }

    private Operation read(Path file) throws IOException {
        StorageFiles.checkPath(file);
        if (Files.size(file) > 1024 * 1024) throw new IOException("Management record is too large");
        Operation op;
        try {
            var encoded = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (String required : List.of("version", "state", "id", "kind", "mode", "directory", "owner",
                    "oldName", "source", "copiedFiles", "provisionalTeams", "retiringTeams")) {
                if (!encoded.has(required) || encoded.get(required).isJsonNull())
                    throw new IOException("Management record lacks " + required);
            }
            op = JSON.fromJson(encoded, Operation.class);
        } catch (RuntimeException failure) { throw new IOException("Malformed management operation", failure); }
        validate(op);
        if (!file.equals(file(op))) throw new IOException("Management operation ID/path mismatch");
        return op;
    }

    private void validate(Operation op) throws IOException {
        if (op == null || op.version != VERSION || op.id == null || op.kind == null || op.state == null
                || op.mode == null || op.directory == null || op.owner == null
                || op.oldName == null || op.source == null || op.copiedFiles == null
                || op.provisionalTeams == null || op.retiringTeams == null
                || !Set.of("RENAME", "DELETE").contains(op.kind)
                || !Set.of("PREPARED", "COMMITTED", "COMPLETE").contains(op.state)) {
            throw new IOException("Invalid management operation");
        }
        try { UUID.fromString(op.id); }
        catch (IllegalArgumentException failure) { throw new IOException("Invalid management operation ID", failure); }
        if (op.copiedFiles.size() > 512 || op.provisionalTeams.size() > 512 || op.retiringTeams.size() > 512)
            throw new IOException("Management record exceeds resource limit");
        Path source = storage.paths().map(op.directory, op.oldName);
        if (!source.equals(Path.of(op.source).toAbsolutePath().normalize())) throw new IOException("Source identity mismatch");
        if (op.kind.equals("RENAME")) {
            if (op.newName == null || op.destination == null || op.backup == null || op.retiredBackup == null)
                throw new IOException("Incomplete rename operation");
            if (op.oldName.equals(op.newName) || !op.copiedFiles.contains("map.json"))
                throw new IOException("Invalid rename identity or file manifest");
            Path target = storage.paths().map(op.directory, op.newName);
            Path backup = directory.resolve("backups").resolve(op.id).resolve(MapStoragePaths.mapDirectory(op.oldName));
            Path retired = storage.paths().metadata().resolve("trash").resolve("rename-" + op.id)
                    .resolve(MapStoragePaths.mapDirectory(op.oldName));
            if (!target.equals(Path.of(op.destination).toAbsolutePath().normalize())
                    || !backup.equals(Path.of(op.backup).toAbsolutePath().normalize())
                    || !retired.equals(Path.of(op.retiredBackup).toAbsolutePath().normalize()))
                throw new IOException("Rename path mismatch");
            for (String relative : op.copiedFiles) {
                Path part = Path.of(relative).normalize();
                if (part.isAbsolute() || part.startsWith("..") || part.toString().isEmpty() || part.toString().equals("."))
                    throw new IOException("Invalid copied path");
            }
        } else if (op.archiveReceipt == null || !op.archiveReceipt.mode().equals(op.mode)
                || !op.archiveReceipt.name().equals(op.oldName)) {
            throw new IOException("Incomplete archive receipt");
        } else {
            Path archive = Path.of(op.archiveReceipt.target()).toAbsolutePath().normalize();
            if (!source.equals(Path.of(op.archiveReceipt.source()).toAbsolutePath().normalize())
                    || !archive.getFileName().toString().equals(MapStoragePaths.mapDirectory(op.oldName))
                    || archive.getParent() == null || !storage.paths().metadata().resolve("trash").equals(archive.getParent().getParent()))
                throw new IOException("Archive path mismatch");
            try { UUID.fromString(archive.getParent().getFileName().toString()); }
            catch (IllegalArgumentException invalid) { throw new IOException("Invalid archive operation ID", invalid); }
        }
        for (String name : op.provisionalTeams) if (name == null || name.isBlank()) throw new IOException("Invalid team name");
        for (String name : op.retiringTeams) if (name == null || name.isBlank()) throw new IOException("Invalid team name");
    }

    public void rollbackPrepared(Operation op) throws IOException {
        if (!op.state.equals("PREPARED")) throw new IOException("Cannot roll back a committed operation");
        if (op.kind.equals("RENAME")) rollbackRename(op);
        else storage.migration().restoreArchive(op.archiveReceipt);
        finish(op);
    }

    private void rollbackRename(Operation op) throws IOException {
        Path source = Path.of(op.source);
        Path backup = Path.of(op.backup);
        Path target = Path.of(op.destination);
        StorageFiles.checkPath(source);
        StorageFiles.checkPath(backup);
        StorageFiles.checkPath(target);
        if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Both original and backup exist");
            Files.createDirectories(source.getParent());
            Files.move(backup, source);
        }
        if (!Files.isRegularFile(source.resolve("map.json"))) throw new IOException("Original map is missing");
        removeOwnedDestination(target, op.copiedFiles);
        removeTeams(op.provisionalTeams);
    }

    private void removeOwnedDestination(Path target, List<String> ownedFiles) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        StorageFiles.checkPath(target);
        Set<String> owned = new HashSet<>(ownedFiles);
        try (var tree = Files.walk(target)) {
            List<Path> paths = tree.limit(1025).toList();
            if (paths.size() > 1024) throw new IOException("Provisional map exceeds recovery limit");
            for (Path path : paths) {
                StorageFiles.checkPath(path);
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && !owned.contains(target.relativize(path).toString()))
                    throw new IOException("Untracked provisional file: " + path);
                if (Files.isSymbolicLink(path)) throw new IOException("Symbolic link in provisional map");
            }
            for (Path path : paths.stream().sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private void removeTeams(List<String> names) throws IOException {
        var scoreboard = server.overworld().getScoreboard();
        for (String name : names) {
            PlayerTeam team = scoreboard.getPlayerTeam(name);
            if (team == null) continue;
            if (!team.getPlayers().isEmpty()) throw new IOException("Owned scoreboard team has players: " + name);
            scoreboard.removePlayerTeam(team);
        }
    }

    public void finishCommitted(Operation op) throws IOException {
        if (!op.state.equals("COMMITTED")) throw new IOException("Operation is not committed");
        if (op.kind.equals("RENAME")) {
            Path source = Path.of(op.source);
            Path target = Path.of(op.destination);
            Path backup = Path.of(op.backup);
            Path retired = Path.of(op.retiredBackup);
            StorageFiles.checkPath(source);
            StorageFiles.checkPath(target.resolve("map.json"));
            StorageFiles.checkPath(backup);
            if (Files.exists(source, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(target.resolve("map.json")))
                throw new IOException("Committed rename identities are inconsistent");
            if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.exists(retired, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Duplicate old-map backup");
                StorageFiles.checkPath(retired);
                Files.createDirectories(retired.getParent());
                Files.move(backup, retired);
            }
        } else {
            Path source = Path.of(op.source);
            Path archive = Path.of(op.archiveReceipt.target());
            StorageFiles.checkPath(source);
            StorageFiles.checkPath(archive.resolve("map.json"));
            if (Files.exists(source, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(archive.resolve("map.json")))
                throw new IOException("Committed delete identities are inconsistent");
        }
        removeTeams(op.retiringTeams);
        finish(op);
    }

    private void finish(Operation op) throws IOException {
        op.state = "COMPLETE";
        write(op);
        Files.deleteIfExists(file(op));
    }

    /** Runs after storage registration but before any map definition is loaded. */
    public void recoverAll() {
        try {
            StorageFiles.checkPath(directory);
            if (!Files.isDirectory(directory)) return;
            List<Path> records;
            try (var listing = Files.list(directory)) {
                records = listing.filter(path -> path.getFileName().toString().endsWith(".json")).limit(MAX_RECORDS + 1L).toList();
            }
            if (records.size() > MAX_RECORDS) throw new IOException("Too many management recovery records");
            for (Path file : records) {
                Operation op;
                try { op = read(file); }
                catch (Exception failure) {
                    LOGGER.error("Cannot inspect map-management recovery record {}", file, failure);
                    storage.blockAllManagement("Malformed map-management recovery record");
                    continue;
                }
                try {
                    com.cdp.codpattern.config.storage.MapStorageRegistration registration;
                    try { registration = storage.migration().registration(op.mode); }
                    catch (IllegalArgumentException absent) {
                        LOGGER.warn("Preserving management operation {} until owner {} is installed", op.id, op.owner);
                        storage.blockManagement(op.mode);
                        continue;
                    }
                    if (!registration.directory().equals(op.directory) || !registration.owner().equals(op.owner))
                        throw new IOException("Management operation owner changed");
                    if (op.state.equals("PREPARED")) rollbackPrepared(op);
                    else if (op.state.equals("COMMITTED")) finishCommitted(op);
                    else Files.deleteIfExists(file);
                } catch (Exception failure) {
                    LOGGER.error("Cannot recover map-management operation {} for mode {}", op.id, op.mode, failure);
                    storage.blockManagement(op.mode);
                }
            }
        } catch (Exception failure) {
            LOGGER.error("Cannot scan map-management recovery records", failure);
            storage.blockAllManagement("Map-management recovery scan failed");
        }
    }
}
