package com.cdp.codpattern.app.match.runtime.termination;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Atomic save-local ledger. Failed writes never erase the previous valid checkpoint. */
public final class RoomRecoveryStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    public RoomRecoveryStore(Path path) { this.path = path; }

    public static final class Data {
        public int version = 1;
        public Map<String, ForceEndCoordinator.State> rooms = new LinkedHashMap<>();
        public Map<UUID, PlayerRecoveryRecord> players = new LinkedHashMap<>();
        public Map<UUID, EntityRecord> entities = new LinkedHashMap<>();
    }
    public record EntityRecord(UUID entity, String room, UUID generation, String dimension) { }

    public Data read() throws IOException {
        if (!Files.exists(path)) return new Data();
        try {
            Data data = JSON.fromJson(Files.readString(path), Data.class);
            if (data == null || data.version != 1 || data.rooms == null || data.players == null || data.entities == null)
                throw new IllegalStateException("Invalid recovery ledger");
            for (String room : data.rooms.keySet()) com.cdp.codpattern.app.match.model.RoomId.decode(room);
            for (var state : data.rooms.values()) {
                if (state == null || state.generation == null || state.failures == null)
                    throw new IllegalStateException("Invalid match recovery state");
            }
            for (var entry : data.players.entrySet()) {
                var r = entry.getValue();
                if (r == null || !entry.getKey().equals(r.player) || r.room == null || r.generation == null
                        || r.completed == null || r.failures == null || r.attributes == null || r.customActions == null
                        || r.persistentTags == null || !data.rooms.containsKey(r.room))
                    throw new IllegalStateException("Invalid player recovery record");
            }
            for (var entry : data.entities.entrySet()) {
                var r = entry.getValue();
                if (r == null || !entry.getKey().equals(r.entity()) || r.generation() == null || r.dimension() == null
                        || !data.rooms.containsKey(r.room())) throw new IllegalStateException("Invalid entity recovery record");
            }
            return data;
        } catch (RuntimeException failure) {
            throw new IOException("Cannot read recovery ledger " + path, failure);
        }
    }
    public void write(Data data) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "recovery-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(data));
            try (var channel = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
