package com.cdp.codpattern.app.match.management;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.persistence.ModeMapMutationProvider;
import com.cdp.codpattern.app.match.persistence.ModeMapPersistenceRegistry;
import net.minecraft.server.MinecraftServer;

/** Server-thread entry point for map identity mutations. */
public final class MapMutationService {
    private MapMutationService() { }

    public enum Outcome {
        RENAMED, DELETED, UNCHANGED, STALE, NOT_FOUND, UNSUPPORTED, IN_USE,
        NAME_CONFLICT, INVALID_NAME, STORAGE_UNAVAILABLE, BUSY, SOURCE_MISSING,
        RECOVERY_REQUIRED, FAILED
    }

    public record Result(Outcome outcome, RoomId oldRoom, RoomId newRoom, String detail) {
        public boolean success() {
            return outcome == Outcome.RENAMED || outcome == Outcome.DELETED || outcome == Outcome.UNCHANGED;
        }
    }

    public static boolean supportsRename(MinecraftServer server, RoomId room) {
        return room != null && ModeMapPersistenceRegistry.find(room.gameType())
                .filter(ModeMapMutationProvider.class::isInstance).isPresent();
    }

    public static boolean supportsDelete(MinecraftServer server, RoomId room) {
        return supportsRename(server, room);
    }

    public static Result rename(MinecraftServer server, RoomId room, String expectedRevision, String newName) {
        return MapMutationEngine.rename(server, room, expectedRevision, newName);
    }

    public static Result delete(MinecraftServer server, RoomId room, String expectedRevision) {
        return MapMutationEngine.delete(server, room, expectedRevision);
    }
}
