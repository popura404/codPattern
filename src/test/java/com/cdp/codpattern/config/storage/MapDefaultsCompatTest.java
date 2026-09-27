package com.cdp.codpattern.config.storage;

import com.cdp.codpattern.client.gui.screen.EndTeleportDraft;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Comparator;

/** Filesystem and editor regression checks without a game runtime. */
public final class MapDefaultsCompatTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("map-defaults-test");
        try {
            Path file = root.resolve("world-one/defaults.json");
            var store = new MapDefaultsStore(file);
            var empty = store.read();
            require(empty.point() == null && !Files.exists(file), "reading missing config has no side effects");
            JsonObject first = point(12, -20, 45);
            require(store.save(empty.revision(), first), "first save");
            var snapshot = store.read();
            require(snapshot.point().equals(first), "full position round trip");
            snapshot.point().addProperty("Yaw", 90);
            require(store.read().point().equals(first), "caller cannot mutate stored state");
            require(!store.save(empty.revision(), point(30, 1, 0)), "stale writer cannot replace defaults");
            var reopened = new MapDefaultsStore(file);
            require(reopened.read().point().equals(first), "defaults survive a new store instance");
            require(!reopened.save(snapshot.revision(), first), "prior server session revision rejected");
            var other = new MapDefaultsStore(root.resolve("world-two/defaults.json"));
            require(other.read().point() == null, "worlds are isolated");
            require(!other.save(snapshot.revision(), first), "revision cannot be reused for another world");
            var failing = new MapDefaultsStore(file, (path, text) -> { throw new IOException("injected write failure"); });
            String revision = failing.read().revision();
            expectUnavailable(() -> failing.save(revision, point(22, 0, 0)));
            require(store.read().point().equals(first), "failed write preserves saved position");
            for (String corrupt : new String[]{"{broken", "{\"version\":2}", "{\"version\":\"1\"}",
                    "{\"version\":1,\"matchEndTeleportPoint\":{}}",
                    "{\"version\":1,\"matchEndTeleportPoint\":{\"Dimension\":\"INVALID\",\"Position\":[1,2,3],\"Yaw\":0}}",
                    "{\"version\":1,\"matchEndTeleportPoint\":{\"Dimension\":\"minecraft:overworld\",\"Position\":[1.5,2,3],\"Yaw\":0}}",
                    "{\"version\":1,\"matchEndTeleportPoint\":{\"Dimension\":\"minecraft:overworld\",\"Position\":[2147483648,2,3],\"Yaw\":0}}"}) {
                Files.writeString(file, corrupt);
                expectUnavailable(store::read);
                expectUnavailable(() -> store.save(snapshot.revision(), first));
                require(Files.readString(file).equals(corrupt), "corrupt file is never silently repaired");
            }
            Files.writeString(file, " ".repeat(16385));
            expectUnavailable(store::read);
            Path linked = root.resolve("linked.json");
            Files.createSymbolicLink(linked, file);
            expectUnavailable(() -> new MapDefaultsStore(linked).read());
            editorChecks();
            System.out.println("PASS map defaults/editor compatibility (" + checks + " assertions)");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private static void editorChecks() {
        var draft = new EndTeleportDraft();
        var first = new EndTeleportDraft.Value("minecraft:overworld", -12, 70, 5, 45);
        draft.load(null, first);
        require(!draft.dirty() && !draft.canSave(), "unset form starts blank and clean");
        draft.fill(first);
        require(draft.canSave(), "filling position changes only draft");
        draft.load(first, first);
        draft.fill(first);
        require(!draft.canSave(), "same point disables save");
        draft.coordinates(" -0012 ", "070", "+5");
        require(!draft.dirty(), "equivalent integer formatting is unchanged");
        for (String invalid : new String[]{"", "-", "1.5", "NaN", "2147483648", "-2147483649"}) {
            draft.coordinates(invalid, "70", "5");
            require(draft.dirty() && !draft.canSave(), "invalid input cannot save: " + invalid);
        }
        draft.coordinates("-13", "70", "5");
        require(draft.canSave() && draft.value().orElseThrow().dimension().equals(first.dimension())
                && draft.yaw() == 45, "manual coordinates preserve dimension and yaw");
        draft.coordinates("-12", "70", "5");
        require(!draft.canSave(), "restoring original disables save");
        draft.fill(new EndTeleportDraft.Value("minecraft:the_nether", -12, 70, 5, 45));
        require(draft.canSave(), "dimension-only edit can save");
        draft.fill(new EndTeleportDraft.Value(first.dimension(), -12, 70, 5, 405));
        require(!draft.canSave(), "equivalent yaw is unchanged");
        draft.fill(new EndTeleportDraft.Value(first.dimension(), -12, 70, 5, 90));
        require(draft.canSave(), "yaw-only edit can save");
        draft.baseline(new EndTeleportDraft.Value(first.dimension(), 30, 80, 0, 0));
        require(draft.x().equals("-12") && draft.yaw() == 90 && draft.canSave(), "conflict refresh preserves user input");
        draft.load(draft.value().orElseThrow(), first);
        require(!draft.canSave(), "successful save establishes new baseline");
    }
    private static JsonObject point(int x, int z, int yaw) {
        return JsonParser.parseString("{\"Dimension\":\"minecraft:overworld\",\"Position\":["+x+",70,"+z+"],\"Yaw\":"+yaw+",\"Pitch\":0}").getAsJsonObject();
    }
    private static void expectUnavailable(Runnable action) {
        try { action.run(); throw new AssertionError("Expected unavailable defaults"); }
        catch (MapDefaultsStore.Unavailable expected) { checks++; }
    }
    private static void require(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
