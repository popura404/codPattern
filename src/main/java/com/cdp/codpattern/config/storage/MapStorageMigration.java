package com.cdp.codpattern.config.storage;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Read-only detection/preflight; execute is the only legacy-data mutation entry point. */
public final class MapStorageMigration {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DETECTION_LIMIT = 512;
    private final MapStoragePaths paths;
    private final String oldWorldName;
    private final Map<String, MapStorageRegistration> registrations = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile Journal journal;
    private volatile String journalError;
    private volatile boolean legacyCommonBlocked;

    public record Entry(String source, String target, String sha256) {}
    public record Unit(String id, String mode, List<Entry> files, String state, String detail) {}
    public record Plan(List<Unit> units, List<String> problems) {
        public Set<String> modes() {
            Set<String> result = new HashSet<>();
            for (Unit unit : units) {
                if (unit.mode().equals("builtin")) result.addAll(List.of("frontline", "teamdeathmatch"));
                else result.add(unit.mode());
            }
            return result;
        }
    }
    public record Detection(boolean pending, boolean uncertain, String detail) {}
    public record Result(int completed, int failed, boolean markerWritten, String detail) {}
    private static final class Journal {
        int layoutVersion = 1;
        String world;
        String migrationId;
        String updatedAt;
        Map<String, String> versions = new LinkedHashMap<>();
        Map<String, Unit> units = new java.util.concurrent.ConcurrentHashMap<>();
    }

    public MapStorageMigration(MapStoragePaths paths, String oldWorldName) {
        this.paths = paths;
        this.oldWorldName = MapStoragePaths.legacyFileName(oldWorldName);
        journal = new Journal();
        journal.world = paths.world().toString();
        legacyCommonBlocked = Files.exists(paths.oldCommonRules(), LinkOption.NOFOLLOW_LINKS) && !Files.exists(paths.commonRules());
        Path file = journalPath();
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            try {
                StorageFiles.checkPath(file);
                Journal loaded = JSON.fromJson(Files.readString(file), Journal.class);
                if (loaded == null || loaded.layoutVersion != 1 || loaded.units == null
                        || !journal.world.equals(loaded.world)) throw new IOException("Invalid or relocated migration journal");
                for (var item : loaded.units.entrySet()) {
                    Unit unit = item.getValue();
                    if (unit == null || !item.getKey().equals(unit.id()) || unit.mode() == null || unit.files() == null
                            || !Set.of("PREPARED", "COMMITTED", "INCOMPLETE", "COMPLETE", "DELETED").contains(unit.state())) {
                        throw new IOException("Malformed migration unit");
                    }
                    for (Entry entry : unit.files()) if (entry == null || entry.source() == null || entry.target() == null
                            || entry.sha256() == null || !entry.sha256().matches("[0-9a-f]{64}")) {
                        throw new IOException("Malformed migration file manifest");
                    }
                }
                loaded.units = new java.util.concurrent.ConcurrentHashMap<>(loaded.units);
                journal = loaded;
            } catch (Exception e) {
                journalError = "Cannot read migration journal: " + e.getMessage();
            }
        }
    }

    public MapStoragePaths paths() { return paths; }
    public boolean hasMigrationRecord() { return journal.migrationId != null; }
    public Path journalPath() { return paths.metadata().resolve("migration.json"); }
    public Path markerPath() {
        String key = UUID.nameUUIDFromBytes(paths.world().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        return paths.legacy().resolve(".codpattern-migrations").resolve(key + ".json");
    }

    public synchronized void register(MapStorageRegistration registration) {
        if (registrations.containsKey(registration.mode()) || registrations.values().stream()
                .anyMatch(value -> value.directory().equalsIgnoreCase(registration.directory())
                        || !Collections.disjoint(value.legacyFolders(), registration.legacyFolders()))) {
            throw new IllegalArgumentException("Duplicate map storage registration: " + registration.mode());
        }
        registrations.put(registration.mode(), registration);
    }

    public MapStorageRegistration registration(String mode) {
        MapStorageRegistration result = registrations.get(mode);
        if (result == null) throw new IllegalArgumentException("Unregistered map storage: " + mode);
        return result;
    }

    private Path legacyMode(String folder) { return paths.legacy().resolve(oldWorldName).resolve(folder); }
    private static String mapId(String mode, String name) { return mode + "/" + MapStoragePaths.mapDirectory(name); }

    public boolean blocksMap(String mode, String name) {
        if (journalError != null) return true;
        Unit unit = journal.units.get(mapId(mode, name));
        if (unit != null && !unit.state().equals("COMPLETE") && !unit.state().equals("DELETED")) return true;
        MapStorageRegistration registration = registrations.get(mode);
        if (registration == null || registration.rules() == null) return false;
        Path legacyRules = registration.rules().forMap(paths, name);
        Path rules = paths.rules(registration.directory(), name);
        try {
            if (Files.exists(legacyRules, LinkOption.NOFOLLOW_LINKS)) {
                // Do not create defaults over unresolved legacy rules.
                if (!Files.isDirectory(rules)) return true;
                try (var entries = Files.list(legacyRules)) {
                    return entries.anyMatch(file -> !Files.exists(rules.resolve(file.getFileName()), LinkOption.NOFOLLOW_LINKS));
                }
            }
            return false;
        } catch (IOException e) { return true; }
    }

    public boolean hasLegacyMap(String mode, String name) {
        MapStorageRegistration registration = registration(mode);
        for (String folder : registration.legacyFolders()) {
            if (Files.exists(legacyMode(folder).resolve(MapStoragePaths.legacyFileName(name) + ".json"), LinkOption.NOFOLLOW_LINKS)) return true;
        }
        return false;
    }

    public boolean commonRulesPending() {
        Unit unit = journal.units.get("builtin/rules");
        return journalError != null || (unit != null && !unit.state().equals("COMPLETE"))
                || legacyCommonBlocked;
    }

    /** Bounded metadata-only scan. No JSON map decoding, hashing or writes. */
    public Detection detect() {
        legacyCommonBlocked = Files.exists(paths.oldCommonRules(), LinkOption.NOFOLLOW_LINKS) && !Files.exists(paths.commonRules());
        if (journalError != null) return new Detection(false, true, journalError);
        try {
            for (Unit unit : journal.units.values()) {
                if (!unit.state().equals("COMPLETE") && !unit.state().equals("DELETED")) return new Detection(true, false, "Incomplete migration: " + unit.id());
            }
            if (journal.migrationId != null) {
                StorageFiles.checkPath(markerPath());
                if (!Files.isRegularFile(markerPath())) return new Detection(false, true, "Migration marker missing");
                Journal mirror = JSON.fromJson(Files.readString(markerPath()), Journal.class);
                if (mirror == null || !Objects.equals(mirror.migrationId, journal.migrationId)
                        || !Objects.equals(mirror.updatedAt, journal.updatedAt)) {
                    return new Detection(false, true, "Migration marker mismatch");
                }
            }
            if (Files.exists(paths.oldCommonRules(), LinkOption.NOFOLLOW_LINKS)) return new Detection(true, false, "Legacy common rules");
            for (String name : List.of("tdm_match_records", "tactical_tdm_match_records")) {
                if (hasEntries(paths.world().resolve("serverconfig/codpattern").resolve(name))) return new Detection(true, false, "Legacy records");
            }
            Path oldWorld = paths.legacy().resolve(oldWorldName);
            StorageFiles.checkPath(oldWorld);
            if (Files.isDirectory(oldWorld)) {
                int count = 0;
                try (var dirs = Files.newDirectoryStream(oldWorld)) {
                    for (Path dir : dirs) {
                        if (++count > DETECTION_LIMIT) return new Detection(false, true, "Legacy scan limit reached");
                        if (hasEntries(dir)) return new Detection(true, false, "Legacy mode data (addon may be absent): " + dir.getFileName());
                    }
                }
            }
            for (MapStorageRegistration registration : registrations.values()) {
                if (registration.rules() != null && hasEntries(registration.rules().root(paths))) {
                    return new Detection(true, false, "Legacy addon rules: " + registration.mode());
                }
            }
            // Unknown *_rules roots are only candidates; never parse or migrate their content.
            Path configs = paths.world().resolve("serverconfig/codpattern");
            if (Files.isDirectory(configs)) {
                int count = 0;
                try (var dirs = Files.newDirectoryStream(configs, "*_rules")) {
                    for (Path dir : dirs) {
                        if (++count > DETECTION_LIMIT) return new Detection(false, true, "Rules scan limit reached");
                        if (!dir.getFileName().toString().equals("backpack_rules") && hasEntries(dir)) {
                            return new Detection(true, false, "Legacy rules candidate: " + dir.getFileName());
                        }
                    }
                }
            }
            return new Detection(false, false, "No pending legacy data");
        } catch (Exception e) { return new Detection(false, true, e.getMessage()); }
    }

    private static boolean hasEntries(Path directory) throws IOException {
        StorageFiles.checkPath(directory);
        if (!Files.exists(directory)) return false;
        if (!Files.isDirectory(directory)) return true;
        try (var entries = Files.newDirectoryStream(directory)) { return entries.iterator().hasNext(); }
    }

    public Plan inspect() {
        legacyCommonBlocked = Files.exists(paths.oldCommonRules(), LinkOption.NOFOLLOW_LINKS) && !Files.exists(paths.commonRules());
        List<String> problems = new ArrayList<>();
        Map<String, Unit> units = new LinkedHashMap<>();
        if (journalError != null) return new Plan(List.of(), List.of(journalError));
        for (Unit unit : journal.units.values()) {
            if (unit.state().equals("COMPLETE") || unit.state().equals("DELETED")) continue;
            if (!unit.mode().equals("builtin") && !registrations.containsKey(unit.mode())) {
                problems.add("Missing addon for incomplete unit: " + unit.id());
                continue;
            }
            try { validateEntries(unit.files()); units.put(unit.id(), unit); }
            catch (Exception e) { problems.add(unit.id() + ": " + e.getMessage()); }
        }
        Set<Path> claimedRules = new HashSet<>();
        Set<String> ambiguous = new HashSet<>();
        for (MapStorageRegistration registration : registrations.values()) {
            Map<String, Path> maps = new LinkedHashMap<>();
            for (String folder : registration.legacyFolders()) {
                Path source = legacyMode(folder);
                try {
                    StorageFiles.checkPath(source);
                    if (!Files.isDirectory(source)) continue;
                    try (var files = Files.list(source)) {
                        for (Path file : files.toList()) {
                            if (!file.getFileName().toString().endsWith(".json")) {
                                problems.add("Unrecognized legacy file: " + file); continue;
                            }
                            try {
                                StorageFiles.checkPath(file);
                                JsonObject object = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                                registration.validateMap().accept(object);
                                String name = object.get("mapName").getAsString();
                                MapStoragePaths.mapDirectory(name);
                                if (maps.putIfAbsent(name, file) != null) {
                                    ambiguous.add(mapId(registration.mode(), name));
                                    problems.add("Duplicate legacy map name: " + name);
                                }
                            } catch (Exception e) { problems.add(file + ": " + e.getMessage()); }
                        }
                    }
                } catch (Exception e) { problems.add(source + ": " + e.getMessage()); }
            }
            // Allow a rules-only migration only when a new map supplies the unambiguous logical name.
            try {
                Path mode = paths.mode(registration.directory());
                StorageFiles.checkPath(mode);
                if (Files.isDirectory(mode)) try (var dirs = Files.list(mode)) {
                    for (Path dir : dirs.toList()) {
                        Path file = dir.resolve("map.json");
                        if (!Files.isRegularFile(file)) continue;
                        StorageFiles.checkPath(file);
                        JsonObject object = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                        registration.validateMap().accept(object);
                        String name = object.get("mapName").getAsString();
                        if (!file.equals(paths.map(registration.directory(), name).resolve("map.json"))) {
                            problems.add("Map stored in wrong directory: " + file); continue;
                        }
                        maps.putIfAbsent(name, null);
                    }
                }
            } catch (Exception e) { problems.add("New map scan: " + e.getMessage()); }
            Map<Path, String> ruleOwners = new HashMap<>();
            for (var item : maps.entrySet()) {
                String name = item.getKey();
                String id = mapId(registration.mode(), name);
                if (ambiguous.contains(id)) continue;
                if (units.containsKey(id)) {
                    if (registration.rules() != null) claimedRules.add(registration.rules().forMap(paths, name));
                    continue; // A retry uses the original journal even if cleanup removed some source rules.
                }
                try {
                    List<Entry> entries = new ArrayList<>();
                    if (item.getValue() != null) {
                        entries.add(entry(item.getValue(), paths.map(registration.directory(), name).resolve("map.json")));
                    }
                    if (registration.rules() != null) {
                        Path rules = registration.rules().forMap(paths, name);
                        String previous = ruleOwners.putIfAbsent(rules, id);
                        if (previous != null && Files.exists(rules)) {
                            ambiguous.add(previous); ambiguous.add(id);
                            units.remove(previous);
                            throw new IOException("Ambiguous legacy rules shared by map names: " + rules);
                        }
                        if (!Files.isDirectory(rules) && !Files.isDirectory(paths.rules(registration.directory(), name))) {
                            throw new IOException("Missing addon rules for legacy map: " + rules);
                        }
                        if (Files.isDirectory(rules)) {
                            registration.rules().validate().accept(rules);
                            treeEntries(rules, paths.rules(registration.directory(), name), entries);
                            claimedRules.add(rules);
                        }
                    }
                    addUnit(units, problems, new Unit(id, registration.mode(), List.copyOf(entries), "PREPARED", ""));
                } catch (Exception e) { problems.add(id + ": " + e.getMessage()); }
            }
            if (registration.rules() != null) {
                Path source = registration.rules().root(paths);
                try {
                    StorageFiles.checkPath(source);
                    if (Files.isDirectory(source)) try (var dirs = Files.list(source)) {
                        for (Path dir : dirs.toList()) if (!claimedRules.contains(dir) && hasEntries(dir)) {
                            problems.add("Rules without an unambiguous map: " + dir);
                        }
                    }
                } catch (Exception e) { problems.add(source + ": " + e.getMessage()); }
            }
        }
        try {
            StorageFiles.checkPath(paths.oldCommonRules());
            if (Files.exists(paths.oldCommonRules())) {
                JsonElement json = JsonParser.parseString(Files.readString(paths.oldCommonRules()));
                if (!json.isJsonObject()) throw new IOException("Invalid common rules JSON");
                addUnit(units, problems, new Unit("builtin/rules", "builtin", List.of(entry(paths.oldCommonRules(), paths.commonRules())), "PREPARED", ""));
            }
            for (String mode : List.of("frontline", "teamdeathmatch")) {
                String old = mode.equals("frontline") ? "tdm_match_records" : "tactical_tdm_match_records";
                List<Entry> entries = new ArrayList<>();
                treeEntries(paths.world().resolve("serverconfig/codpattern").resolve(old), paths.mode("builtin/" + mode).resolve("records"), entries);
                addUnit(units, problems, new Unit(mode + "/records", mode, List.copyOf(entries), "PREPARED", ""));
            }
            Path oldWorld = paths.legacy().resolve(oldWorldName);
            if (Files.isDirectory(oldWorld)) try (var dirs = Files.list(oldWorld)) {
                for (Path dir : dirs.toList()) if (registrations.values().stream().noneMatch(r -> r.legacyFolders().contains(dir.getFileName().toString())) && hasEntries(dir)) {
                    problems.add("Unregistered legacy mode; install its addon: " + dir);
                }
            }
        } catch (Exception e) { problems.add(e.getMessage()); }
        // No writes, hashes and parsing are confined to this explicit OP preflight.
        rejectOtherWorldClaims(units, problems);
        try {
            Path configs = paths.world().resolve("serverconfig/codpattern");
            if (Files.isDirectory(configs)) try (var dirs = Files.newDirectoryStream(configs, "*_rules")) {
                for (Path dir : dirs) {
                    String name = dir.getFileName().toString();
                    if (name.equals("backpack_rules") || name.equals("tdm_rules")) continue;
                    boolean registered = registrations.values().stream().anyMatch(r -> r.rules() != null && r.rules().root(paths).equals(dir));
                    if (!registered && hasEntries(dir)) problems.add("Rules owner is not registered; install the matching addon: " + dir);
                }
            }
        } catch (Exception e) { problems.add(e.getMessage()); }
        units.keySet().removeAll(ambiguous);
        return new Plan(List.copyOf(units.values()), List.copyOf(problems));
    }

    private void rejectOtherWorldClaims(Map<String, Unit> units, List<String> problems) {
        Path markers = paths.legacy().resolve(".codpattern-migrations");
        try {
            StorageFiles.checkPath(markers);
            if (!Files.isDirectory(markers)) return;
            try (var files = Files.newDirectoryStream(markers, "*.json")) {
                for (Path file : files) {
                    StorageFiles.checkPath(file);
                    Journal other = JSON.fromJson(Files.readString(file), Journal.class);
                    if (other == null || other.world == null || other.units == null) throw new IOException("Invalid marker: " + file);
                    if (other.world.equals(paths.world().toString())) continue;
                    Set<String> claimed = new HashSet<>();
                    for (Unit unit : other.units.values()) for (Entry entry : unit.files()) claimed.add(entry.source());
                    Iterator<Unit> iterator = units.values().iterator();
                    while (iterator.hasNext()) {
                        Unit unit = iterator.next();
                        if (unit.files().stream().anyMatch(entry -> claimed.contains(entry.source()))) {
                            problems.add("Legacy source claimed by another save (" + other.world + "): " + unit.id());
                            iterator.remove();
                        }
                    }
                }
            }
        } catch (Exception e) {
            problems.add("Cannot verify legacy source ownership: " + e.getMessage());
            units.clear();
        }
    }

    private void addUnit(Map<String, Unit> units, List<String> problems, Unit unit) throws IOException {
        if (unit.files().isEmpty()) return;
        Unit previous = journal.units.get(unit.id());
        if (previous != null && (previous.state().equals("COMPLETE") || previous.state().equals("DELETED"))) {
            problems.add("Previously handled unit has legacy files again; manual review required: " + unit.id()); return;
        }
        if (units.containsKey(unit.id())) return; // Resume uses its original immutable file manifest.
        validateEntries(unit.files());
        units.put(unit.id(), unit);
    }

    private Entry entry(Path source, Path target) throws IOException {
        return new Entry(source.toAbsolutePath().normalize().toString(), target.toAbsolutePath().normalize().toString(), StorageFiles.digest(source));
    }

    private void treeEntries(Path source, Path target, List<Entry> entries) throws IOException {
        StorageFiles.checkPath(source);
        if (!Files.exists(source)) return;
        try (var files = Files.walk(source)) {
            for (Path file : files.toList()) {
                StorageFiles.checkPath(file);
                if (Files.isRegularFile(file)) entries.add(entry(file, target.resolve(source.relativize(file))));
            }
        }
    }

    private void validateEntries(List<Entry> entries) throws IOException {
        for (Entry entry : entries) {
            Path source = Path.of(entry.source()).toAbsolutePath().normalize();
            Path target = Path.of(entry.target()).toAbsolutePath().normalize();
            boolean allowed = registrations.values().stream().anyMatch(r -> r.legacyFolders().stream().anyMatch(f -> source.startsWith(legacyMode(f)))
                    || (r.rules() != null && source.startsWith(r.rules().root(paths))));
            allowed |= source.equals(paths.oldCommonRules()) || source.startsWith(paths.world().resolve("serverconfig/codpattern/tdm_match_records"))
                    || source.startsWith(paths.world().resolve("serverconfig/codpattern/tactical_tdm_match_records"));
            if (!allowed || !target.startsWith(paths.root()) || target.startsWith(paths.metadata())) throw new IOException("Manifest path outside registered storage");
            StorageFiles.checkPath(source); StorageFiles.checkPath(target);
            boolean sourceExists = Files.exists(source);
            boolean targetExists = Files.exists(target);
            if (!sourceExists && !targetExists) throw new IOException("Source and target missing: " + source);
            if (sourceExists && !StorageFiles.digest(source).equals(entry.sha256())) throw new IOException("Source changed: " + source);
            if (targetExists && !StorageFiles.digest(target).equals(entry.sha256())) throw new IOException("Target conflict: " + target);
        }
    }

    public synchronized Result execute(Plan plan, Map<String, String> versions, BooleanSupplier cancelled) {
        int completed = 0;
        int failed = plan.problems().size();
        List<String> errors = new ArrayList<>(plan.problems());
        if (journalError != null) return new Result(0, failed + 1, false, journalError);
        if (!plan.units().isEmpty()) journal.migrationId = UUID.randomUUID().toString();
        journal.versions = new LinkedHashMap<>(versions);
        for (Unit unit : plan.units()) {
            if (cancelled.getAsBoolean()) { failed++; errors.add("Migration interrupted; rerun confirm after restart"); break; }
            Unit previous = journal.units.get(unit.id());
            if (previous != null && (previous.state().equals("COMPLETE") || previous.state().equals("DELETED"))) continue;
            try {
                validateEntries(unit.files());
                record(unit, "PREPARED", ""); // Durable before touching target/source files.
                Path stage = paths.metadata().resolve("staging").resolve(UUID.randomUUID().toString());
                StorageFiles.checkPath(stage);
                Files.createDirectories(stage);
                try {
                    int index = 0;
                    for (Entry entry : unit.files()) {
                        if (cancelled.getAsBoolean()) throw new IOException("Migration interrupted");
                        Path source = Path.of(entry.source());
                        Path target = Path.of(entry.target());
                        if (Files.exists(target)) { index++; continue; }
                        Path staged = stage.resolve(Integer.toString(index++));
                        Files.copy(source, staged);
                        if (!StorageFiles.digest(staged).equals(entry.sha256())) throw new IOException("Staged copy mismatch: " + source);
                    }
                    validateEntries(unit.files());
                    index = 0;
                    for (Entry entry : unit.files()) {
                        if (cancelled.getAsBoolean()) throw new IOException("Migration interrupted");
                        Path target = Path.of(entry.target());
                        Path staged = stage.resolve(Integer.toString(index++));
                        if (Files.exists(target)) continue;
                        StorageFiles.checkPath(target);
                        Files.createDirectories(target.getParent());
                        Files.move(staged, target); // Same filesystem, never replace an existing file.
                    }
                    record(unit, "COMMITTED", "Pending source cleanup");
                    for (Entry entry : unit.files()) {
                        if (cancelled.getAsBoolean()) throw new IOException("Migration interrupted");
                        validateEntries(List.of(entry));
                        Path target = Path.of(entry.target());
                        if (!Files.isRegularFile(target) || !StorageFiles.digest(target).equals(entry.sha256())) {
                            throw new IOException("Committed target missing or changed; source retained: " + target);
                        }
                        Path source = Path.of(entry.source());
                        if (Files.exists(source)) {
                            Files.delete(source);
                            removeEmptyParents(source.getParent());
                        }
                    }
                    record(unit, "COMPLETE", "Files moved; restart and validate gameplay");
                    completed++;
                } finally {
                    try (var files = Files.walk(stage)) {
                        for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
                    }
                }
            } catch (Exception e) {
                failed++; errors.add(unit.id() + ": " + e.getMessage());
                try { record(unit, "INCOMPLETE", e.getMessage()); }
                catch (IOException journalFailure) { journalError = journalFailure.getMessage(); break; }
            }
        }
        boolean marked = false;
        if (journal.migrationId != null && journalError == null) {
            try {
                saveJournal();
                StorageFiles.write(markerPath(), JSON.toJson(journal));
                marked = true;
            } catch (IOException e) { failed++; errors.add("Marker write failed: " + e.getMessage()); }
        }
        legacyCommonBlocked = Files.exists(paths.oldCommonRules(), LinkOption.NOFOLLOW_LINKS) && !Files.exists(paths.commonRules());
        return new Result(completed, failed, marked, String.join("\n", errors));
    }

    private void removeEmptyParents(Path directory) throws IOException {
        while (directory != null && !directory.equals(paths.legacy()) && !directory.equals(paths.world().resolve("serverconfig/codpattern"))) {
            if (!directory.startsWith(paths.legacy()) && !directory.startsWith(paths.world().resolve("serverconfig/codpattern"))) return;
            if (hasEntries(directory)) return;
            Files.deleteIfExists(directory);
            directory = directory.getParent();
        }
    }

    private void record(Unit unit, String state, String detail) throws IOException {
        journal.units.put(unit.id(), new Unit(unit.id(), unit.mode(), unit.files(), state, detail));
        saveJournal();
    }

    private synchronized void saveJournal() throws IOException {
        journal.updatedAt = Instant.now().toString();
        StorageFiles.write(journalPath(), JSON.toJson(journal));
    }

    /** A receipt is captured before archival and can be persisted by its caller. */
    public record ArchiveReceipt(String mode, String name, String source, String target,
                                 Unit previous, boolean markerPresent, JsonElement previousMarkerUnit) { }

    public synchronized ArchiveReceipt prepareArchive(String mode, String name) throws IOException {
        MapStorageRegistration registration = registration(mode);
        Path source = paths.map(registration.directory(), name);
        StorageFiles.checkPath(source);
        if (!Files.isRegularFile(source.resolve("map.json"))) throw new NoSuchFileException(source.toString());
        Path target = paths.metadata().resolve("trash").resolve(UUID.randomUUID().toString())
                .resolve(MapStoragePaths.mapDirectory(name));
        StorageFiles.checkPath(target);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(target.toString());
        if (journalError != null) throw new IOException(journalError);
        boolean markerPresent = Files.exists(markerPath(), LinkOption.NOFOLLOW_LINKS);
        JsonElement previousMarkerUnit = null;
        if (markerPresent) {
            JsonObject marker = readMarker();
            previousMarkerUnit = marker.getAsJsonObject("units").get(mapId(mode, name));
            if (previousMarkerUnit != null) previousMarkerUnit = previousMarkerUnit.deepCopy();
        }
        return new ArchiveReceipt(mode, name, source.toString(), target.toString(),
                journal.units.get(mapId(mode, name)), markerPresent, previousMarkerUnit);
    }

    /** Idempotent archival that uses the caller's durable receipt. */
    public synchronized void archive(ArchiveReceipt receipt) throws IOException {
        validateReceipt(receipt);
        Path source = Path.of(receipt.source());
        Path target = Path.of(receipt.target());
        if (!Files.isRegularFile(source.resolve("map.json"))) throw new NoSuchFileException(source.toString());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(target.toString());
        Files.createDirectories(target.getParent());
        Files.move(source, target);
        try {
            Unit deleted = new Unit(mapId(receipt.mode(), receipt.name()), receipt.mode(),
                    receipt.previous() == null ? List.of() : receipt.previous().files(), "DELETED", target.toString());
            record(deleted, "DELETED", target.toString());
            if (receipt.markerPresent()) updateMarkerUnit(mapId(receipt.mode(), receipt.name()), JSON.toJsonTree(deleted));
        } catch (IOException failure) {
            try { restoreArchive(receipt); }
            catch (IOException restoreFailure) { failure.addSuppressed(restoreFailure); }
            throw failure;
        }
    }

    /** Restores only this map's entry; unrelated journal entries are retained. */
    public synchronized void restoreArchive(ArchiveReceipt receipt) throws IOException {
        validateReceipt(receipt);
        Path source = Path.of(receipt.source());
        Path target = Path.of(receipt.target());
        boolean sourceExists = Files.exists(source, LinkOption.NOFOLLOW_LINKS);
        boolean targetExists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (sourceExists && targetExists) throw new IOException("Both active and archived map exist");
        if (!sourceExists && !targetExists) throw new IOException("Active and archived map are both missing");
        if (targetExists) {
            Files.createDirectories(source.getParent());
            Files.move(target, source);
        }
        String id = mapId(receipt.mode(), receipt.name());
        Unit current = journal.units.get(id);
        if (current != null && !(current.state().equals("DELETED") && receipt.target().equals(current.detail()))
                && !current.equals(receipt.previous())) {
            throw new IOException("Archive journal entry changed: " + id);
        }
        if (receipt.previous() == null) journal.units.remove(id);
        else journal.units.put(id, receipt.previous());
        saveJournal();
        if (receipt.markerPresent()) updateMarkerUnit(id, receipt.previousMarkerUnit());
    }

    private JsonObject readMarker() throws IOException {
        StorageFiles.checkPath(markerPath());
        try {
            JsonObject marker = JsonParser.parseString(Files.readString(markerPath())).getAsJsonObject();
            if (!marker.has("units") || !marker.get("units").isJsonObject()) throw new IOException("Invalid archive marker");
            return marker;
        } catch (RuntimeException invalid) { throw new IOException("Invalid archive marker", invalid); }
    }

    private void updateMarkerUnit(String id, JsonElement value) throws IOException {
        JsonObject marker = readMarker();
        JsonObject units = marker.getAsJsonObject("units");
        if (value == null || value.isJsonNull()) units.remove(id);
        else units.add(id, value.deepCopy());
        StorageFiles.write(markerPath(), JSON.toJson(marker));
    }

    private void validateReceipt(ArchiveReceipt receipt) throws IOException {
        if (receipt == null) throw new IOException("Missing archive receipt");
        MapStorageRegistration registration = registration(receipt.mode());
        Path expected = paths.map(registration.directory(), receipt.name());
        Path source = Path.of(receipt.source()).toAbsolutePath().normalize();
        Path target = Path.of(receipt.target()).toAbsolutePath().normalize();
        if (!expected.equals(source) || !target.startsWith(paths.metadata().resolve("trash"))
                || !target.getFileName().toString().equals(MapStoragePaths.mapDirectory(receipt.name()))) {
            throw new IOException("Archive receipt has an invalid path");
        }
        StorageFiles.checkPath(source);
        StorageFiles.checkPath(target);
    }

    public synchronized void archive(String mode, String name) throws IOException {
        MapStorageRegistration registration = registration(mode);
        Path source = paths.map(registration.directory(), name);
        if (!Files.isRegularFile(source.resolve("map.json"))) return;
        archive(prepareArchive(mode, name));
    }

}
