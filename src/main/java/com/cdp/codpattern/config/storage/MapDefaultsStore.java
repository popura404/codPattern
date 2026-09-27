package com.cdp.codpattern.config.storage;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Save-local creation defaults. Reads never create or repair a file. */
public final class MapDefaultsStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final String session = UUID.randomUUID().toString();

    @FunctionalInterface interface Writer { void write(Path path, String contents) throws IOException; }
    private final Writer writer;
    public MapDefaultsStore(Path file) { this(file, StorageFiles::write); }
    MapDefaultsStore(Path file, Writer writer) { this.file = file; this.writer = writer; }

    public record Snapshot(JsonObject point, String revision) {
        public Snapshot { point = point == null ? null : point.deepCopy(); }
        @Override public JsonObject point() { return point == null ? null : point.deepCopy(); }
    }

    public static final class Unavailable extends IllegalStateException {
        public Unavailable(Throwable cause) { super("Cannot read or write map defaults", cause); }
    }

    public synchronized Snapshot read() {
        try {
            StorageFiles.checkPath(file);
            if (Files.notExists(file)) return new Snapshot(null, session + ":missing");
            if (!Files.isRegularFile(file) || Files.size(file) > 16384) throw new IOException("Invalid defaults file");
            String contents = Files.readString(file);
            JsonObject root = JsonParser.parseString(contents).getAsJsonObject();
            if (!root.has("version") || !root.get("version").isJsonPrimitive() || !root.getAsJsonPrimitive("version").isNumber()
                    || !root.get("version").getAsBigDecimal().equals(java.math.BigDecimal.ONE))
                throw new IOException("Unsupported defaults version");
            JsonObject point = root.has("matchEndTeleportPoint") ? root.getAsJsonObject("matchEndTeleportPoint") : null;
            if (point != null) validatePoint(point);
            return new Snapshot(point, session + ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(contents.getBytes(StandardCharsets.UTF_8))));
        } catch (IOException | RuntimeException | NoSuchAlgorithmException failure) {
            throw new Unavailable(failure);
        }
    }

    /** False means the caller's snapshot is stale; unreadable files are never overwritten. */
    public synchronized boolean save(String expectedRevision, JsonObject point) {
        Snapshot current = read();
        if (!current.revision().equals(expectedRevision)) return false;
        validatePoint(point);
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("matchEndTeleportPoint", point.deepCopy());
        try { writer.write(file, JSON.toJson(root)); }
        catch (IOException failure) { throw new Unavailable(failure); }
        return true;
    }

    private static void validatePoint(JsonObject point) {
        if (point == null || !point.has("Dimension") || !point.get("Dimension").isJsonPrimitive()
                || !point.getAsJsonPrimitive("Dimension").isString()
                || !point.get("Dimension").getAsString().matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("Invalid dimension");
        JsonArray coordinates = point.getAsJsonArray("Position");
        if (coordinates == null || coordinates.size() != 3) throw new IllegalArgumentException("Invalid coordinates");
        for (JsonElement coordinate : coordinates) {
            if (!coordinate.isJsonPrimitive() || !coordinate.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Invalid coordinate");
            coordinate.getAsBigDecimal().intValueExact();
        }
        for (String angle : new String[]{"Yaw", "Pitch"}) {
            if (!point.has(angle) && angle.equals("Pitch")) continue;
            if (!point.has(angle) || !point.get(angle).isJsonPrimitive() || !point.getAsJsonPrimitive(angle).isNumber()
                    || !Float.isFinite(point.get(angle).getAsFloat())) throw new IllegalArgumentException("Invalid angle");
        }
    }
}
