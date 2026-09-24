package com.cdp.codpattern.config.storage;

import com.google.gson.JsonObject;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real temporary files; no Minecraft server or user saves are touched. */
public final class MapStorageMigrationCompatTest {
    private static int checks;
    private record Fixture(Path base, MapStoragePaths paths, MapStorageMigration migration) {}
    public static void main(String[] args) throws Exception {
        testReadOnlyAndMove();
        testConflict();
        testInterruptedResume();
        testRestartBeforeConfirm();
        testInterruptedCleanup();
        testDeletedMapDoesNotReturn();
        testRulesAndAddonIsolation();
        testMissingRulesAndCollision();
        testSymlinks();
        testCorruptJournal();
        testMarkerFailure();
        testSourceChanged();
        testCommittedTargetRemoved();
        testSafeNames();
        testCommonRules();
        testOtherSaveClaims();
        testGlobalDataIgnored();
        System.out.println("PASS map storage filesystem suite (" + checks + " assertions)");
    }
    private static Fixture fixture() throws Exception {
        Path base = Files.createTempDirectory("codpattern-storage-");
        MapStoragePaths paths = new MapStoragePaths(base.resolve("save"), base.resolve("game"));
        MapStorageMigration migration = engine(paths, false);
        return new Fixture(base, paths, migration);
    }
    private static MapStorageMigration engine(MapStoragePaths paths, boolean addon) {
        MapStorageMigration migration = new MapStorageMigration(paths, "World");
        migration.register(new MapStorageRegistration("frontline", "codpattern", "builtin/frontline", List.of("frontline", "cdptdm"), MapStorageMigrationCompatTest::validate, null));
        if (addon) migration.register(new MapStorageRegistration("zombies", "test-addon", "zombies", List.of("zombies"), MapStorageMigrationCompatTest::validate,
                new MapStorageRegistration.LegacyRules("serverconfig/codpattern/zombies_rules", name -> name.replace(':', '_'), root -> {
                    if (!Files.isRegularFile(root.resolve("room.json"))) throw new IllegalStateException("Missing room.json");
                })));
        return migration;
    }
    private static void validate(JsonObject json) {
        if (!json.has("mapName") || !json.has("levelName")) throw new IllegalArgumentException("Invalid map");
    }
    private static Path oldMap(Fixture f, String mode, String file, String name) throws Exception {
        Path source = f.paths.legacy().resolve("World/" + mode + "/" + file + ".json");
        write(source, "{\"mapName\":\"" + name + "\",\"levelName\":\"minecraft:overworld\"}");
        return source;
    }
    private static void write(Path file, String text) throws Exception { Files.createDirectories(file.getParent()); Files.writeString(file, text); }
    private static MapStorageMigration.Result execute(MapStorageMigration engine) { return engine.execute(engine.inspect(), Map.of("codpattern", "test"), () -> false); }
    private static void require(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }

    private static void testReadOnlyAndMove() throws Exception {
        Fixture f = fixture();
        Path source = oldMap(f, "frontline", "Arena", "Arena");
        String bytes = Files.readString(source);
        require(f.migration.detect().pending(), "detect legacy map");
        require(f.migration.inspect().units().size() == 1, "one map unit");
        require(!Files.exists(f.paths.root()), "detection and check must not write");
        var result = execute(f.migration);
        Path target = f.paths.map("builtin/frontline", "Arena").resolve("map.json");
        require(result.completed() == 1 && result.failed() == 0 && result.markerWritten(), result.toString());
        require(Files.readString(target).equals(bytes), "JSON bytes unchanged");
        require(!Files.exists(source), "successful source moved");
        require(Files.exists(f.migration.markerPath()), "old-root marker exists");
        require(!f.migration.detect().pending() && !f.migration.detect().uncertain(), "empty old roots and marker do not warn");
        require(f.migration.inspect().units().isEmpty(), "repeated command is no-op");
    }
    private static void testConflict() throws Exception {
        Fixture f = fixture(); Path source = oldMap(f, "frontline", "arena", "arena");
        Path target = f.paths.map("builtin/frontline", "arena").resolve("map.json");
        write(target, "{\"mapName\":\"arena\",\"levelName\":\"other\"}");
        String before = Files.readString(target);
        var plan = f.migration.inspect();
        require(plan.units().isEmpty() && !plan.problems().isEmpty(), "conflict blocks unit");
        execute(f.migration);
        require(Files.exists(source) && Files.readString(target).equals(before), "no overwrite or source removal on conflict");
    }
    private static void testInterruptedResume() throws Exception {
        Fixture f = fixture(); Path source = oldMap(f, "frontline", "arena", "arena");
        AtomicInteger checkpoints = new AtomicInteger();
        var result = f.migration.execute(f.migration.inspect(), Map.of(), () -> checkpoints.incrementAndGet() >= 3);
        require(result.failed() > 0 && Files.exists(source), "interrupted source retained");
        MapStorageMigration restarted = engine(f.paths, false);
        require(restarted.blocksMap("frontline", "arena"), "partial target must not load on restart");
        require(Files.exists(source), "restart does not resume");
        var resumed = execute(restarted);
        require(resumed.completed() == 1 && resumed.failed() == 0, "explicit resume completes: " + resumed);
    }
    private static void testInterruptedCleanup() throws Exception {
        Fixture f = fixture(); oldMap(f, "zombies", "arena", "arena");
        Path rules = f.paths.world().resolve("serverconfig/codpattern/zombies_rules/arena");
        write(rules.resolve("room.json"), "{}");
        MapStorageMigration addon = engine(f.paths, true);
        AtomicInteger count = new AtomicInteger();
        addon.execute(addon.inspect(), Map.of(), () -> count.incrementAndGet() >= 7);
        MapStorageMigration restarted = engine(f.paths, true);
        require(restarted.detect().pending(), "partial source cleanup remains pending");
        var result = execute(restarted);
        require(result.failed() == 0 && result.completed() == 1, "cleanup resumes despite missing original map JSON: " + result);
    }
    private static void testRestartBeforeConfirm() throws Exception {
        Fixture f = fixture(); Path old = oldMap(f, "frontline", "arena", "arena");
        MapStorageMigration fresh = engine(f.paths, false);
        fresh.detect(); fresh.inspect();
        require(Files.exists(old) && !Files.exists(f.paths.root()), "restart and check are read-only");
    }
    private static void testDeletedMapDoesNotReturn() throws Exception {
        Fixture f = fixture(); Path old = oldMap(f, "frontline", "arena", "arena");
        String data = Files.readString(old); execute(f.migration);
        f.migration.archive("frontline", "arena");
        require(!Files.exists(f.paths.map("builtin/frontline", "arena")), "archive entire map directory");
        require(!f.migration.detect().pending(), "deleted map not reported as unfinished migration");
        require(!f.migration.blocksMap("frontline", "arena"), "new map of same name allowed after archive");
        write(old, data);
        var plan = engine(f.paths, false).inspect();
        require(plan.units().isEmpty() && !plan.problems().isEmpty(), "old files cannot resurrect deleted map");
    }
    private static void testRulesAndAddonIsolation() throws Exception {
        Fixture f = fixture(); oldMap(f, "zombies", "undead", "undead");
        Path rules = f.paths.world().resolve("serverconfig/codpattern/zombies_rules/undead");
        write(rules.resolve("room.json"), "{\"schemaVersion\":1}");
        write(rules.resolve("waves/1.json"), "{\"wave\":1}");
        require(f.migration.inspect().units().isEmpty(), "missing addon cannot migrate unknown data");
        require(f.migration.detect().pending(), "unknown addon files warn");
        MapStorageMigration addon = engine(f.paths, true);
        require(addon.blocksMap("zombies", "undead"), "unresolved old rules prevent defaults");
        require(execute(addon).completed() == 1, "addon map and rules move together");
        require(Files.exists(f.paths.rules("zombies", "undead").resolve("waves/1.json")), "nested wave relocated");
        require(!Files.exists(rules), "old rule directory removed only when empty");
        MapStorageMigration removedAddon = engine(f.paths, false);
        require(removedAddon.inspect().units().isEmpty(), "new addon files untouched without addon");
    }
    private static void testMissingRulesAndCollision() throws Exception {
        Fixture f = fixture(); oldMap(f, "zombies", "ab", "a:b"); oldMap(f, "zombies", "a_b", "a_b");
        MapStorageMigration addon = engine(f.paths, true);
        require(addon.inspect().units().isEmpty(), "missing rules prevent destructive move");
        write(f.paths.world().resolve("serverconfig/codpattern/zombies_rules/a_b/room.json"), "{}");
        var plan = addon.inspect();
        require(plan.units().isEmpty() && !plan.problems().isEmpty(), "ambiguous old sanitization never combines two maps");
    }
    private static void testSymlinks() throws Exception {
        Fixture f = fixture(); Path old = oldMap(f, "frontline", "arena", "arena");
        Path external = f.base.resolve("outside"); Files.createDirectories(external);
        Files.createDirectories(f.paths.root().resolve("builtin"));
        Files.createSymbolicLink(f.paths.root().resolve("builtin/frontline"), external);
        require(f.migration.inspect().units().isEmpty(), "target symlink blocks migration");
        require(Files.exists(old), "symlink rejection retains source");
    }
    private static void testCorruptJournal() throws Exception {
        Fixture f = fixture(); oldMap(f, "frontline", "arena", "arena");
        write(f.migration.journalPath(), "not json");
        MapStorageMigration broken = engine(f.paths, false);
        require(broken.detect().uncertain(), "corrupt state not treated as no data");
        require(broken.inspect().units().isEmpty(), "corrupt journal must not permit migration");
    }
    private static void testMarkerFailure() throws Exception {
        Fixture f = fixture(); oldMap(f, "frontline", "arena", "arena");
        write(f.paths.legacy().resolve(".codpattern-migrations"), "blocked");
        var result = execute(f.migration);
        require(result.completed() == 1 && !result.markerWritten() && result.failed() == 1, "marker failure is distinct from file completion");
        require(Files.exists(f.paths.map("builtin/frontline", "arena").resolve("map.json")), "marker error cannot erase target");
        require(f.migration.inspect().units().isEmpty(), "marker failure cannot duplicate successful move");
        Files.delete(f.paths.legacy().resolve(".codpattern-migrations"));
        var repaired = execute(f.migration);
        require(repaired.completed() == 0 && repaired.markerWritten() && repaired.failed() == 0, "explicit retry can repair marker without moving files again");
    }
    private static void testSourceChanged() throws Exception {
        Fixture f = fixture(); Path old = oldMap(f, "frontline", "arena", "arena");
        var plan = f.migration.inspect(); Files.writeString(old, "changed");
        var result = f.migration.execute(plan, Map.of(), () -> false);
        require(result.failed() == 1 && Files.readString(old).equals("changed"), "revalidate sources before mutation");
        require(!Files.exists(f.paths.map("builtin/frontline", "arena").resolve("map.json")), "changed source not imported");
    }
    private static void testCommittedTargetRemoved() throws Exception {
        Fixture f = fixture(); Path old = oldMap(f, "frontline", "arena", "arena");
        Path target = f.paths.map("builtin/frontline", "arena").resolve("map.json");
        AtomicInteger checkpoints = new AtomicInteger();
        var result = f.migration.execute(f.migration.inspect(), Map.of(), () -> {
            if (checkpoints.incrementAndGet() == 4) {
                try { Files.delete(target); } catch (Exception e) { throw new IllegalStateException(e); }
            }
            return false;
        });
        require(result.failed() == 1 && Files.exists(old), "lost committed target must retain source");
        require(execute(engine(f.paths, false)).completed() == 1, "retained source permits explicit recovery");
    }
    private static void testSafeNames() throws Exception {
        Fixture f = fixture();
        Set<String> encoded = new HashSet<>();
        for (String name : List.of("Arena", "arena", "地图", "CON", "..", "../escape", "a:b", "a_b", "a ", "a.")) {
            String value = MapStoragePaths.mapDirectory(name);
            require(encoded.add(value.toLowerCase(Locale.ROOT)), "case insensitive identity: " + name);
            require(f.paths.map("builtin/frontline", name).startsWith(f.paths.mode("builtin/frontline")), "name stays within mode");
        }
        var other = new MapStoragePaths(f.base.resolve("otherSave"), f.base.resolve("game"));
        require(!other.root().equals(f.paths.root()), "same game does not share storage between saves");
    }
    private static void testCommonRules() throws Exception {
        Fixture f = fixture(); write(f.paths.oldCommonRules(), "{\"scoreLimit\":123}");
        f.migration.detect();
        require(f.migration.commonRulesPending(), "detected legacy rules block default writes");
        require(execute(f.migration).completed() == 1, "shared rules migrated");
        require(Files.readString(f.paths.commonRules()).contains("123"), "common values preserved");
        require(!engine(f.paths, false).commonRulesPending(), "new startup can load common rules");
    }
    private static void testOtherSaveClaims() throws Exception {
        Fixture first = fixture(); Path source = oldMap(first, "cdptdm", "arena", "arena");
        String data = Files.readString(source);
        require(execute(first.migration).completed() == 1, "legacy mode alias can migrate");
        write(source, data);
        var secondPaths = new MapStoragePaths(first.base.resolve("second-save"), first.base.resolve("game"));
        var second = engine(secondPaths, false);
        var plan = second.inspect();
        require(plan.units().isEmpty() && plan.problems().stream().anyMatch(p -> p.contains("another save")),
                "same world display name cannot claim another save's source");
        require(Files.exists(source) && !Files.exists(secondPaths.root()), "ownership conflict is read-only");
    }
    private static void testGlobalDataIgnored() throws Exception {
        Fixture f = fixture();
        write(f.paths.legacy().resolve("global/other/data.json"), "{}");
        write(f.paths.legacy().resolve("DifferentWorld/frontline/arena.json"), "{}");
        var result = f.migration.detect();
        require(!result.pending() && !result.uncertain(), "global and other-world data do not warn for this save");
    }

}
