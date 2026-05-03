package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.RoomId;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active zombies map ownership so one map snapshot is used by at most one room at a time.
 */
public final class ZombiesMapOccupancyService {
    private static final String DEFAULT_GAME_TYPE = "zombies";
    private static final ZombiesMapOccupancyService INSTANCE = new ZombiesMapOccupancyService();

    private final Map<OccupancyKey, RoomId> owners = new ConcurrentHashMap<>();

    public static ZombiesMapOccupancyService instance() {
        return INSTANCE;
    }

    public ZombiesServiceResult<Void> acquire(String mapName, RoomId roomId) {
        Objects.requireNonNull(roomId, "roomId");
        return acquire(roomId.gameType(), mapName, roomId);
    }

    public ZombiesServiceResult<Void> acquire(RoomId roomId) {
        Objects.requireNonNull(roomId, "roomId");
        return acquire(roomId.gameType(), roomId.mapName(), roomId);
    }

    public ZombiesServiceResult<Void> acquire(String gameType, String mapName, RoomId roomId) {
        Objects.requireNonNull(roomId, "roomId");
        OccupancyKey key = OccupancyKey.of(gameType, mapName);
        RoomId canonicalOwner = RoomId.of(key.gameType(), key.mapName());
        RoomId existing = owners.putIfAbsent(key, canonicalOwner);
        if (existing == null || existing.equals(canonicalOwner)) {
            return ZombiesServiceResult.ok();
        }
        return ZombiesServiceResult.failure(
                ZombiesErrorCode.of("map.occupied"),
                Map.of(
                        "gameType", ModePlayerValue.ofString(key.gameType()),
                        "mapName", ModePlayerValue.ofString(key.mapName()),
                        "owner", ModePlayerValue.ofString(existing.encode())
                ),
                "Zombies map " + key.encoded() + " is occupied by " + existing.encode()
        );
    }

    public boolean release(String mapName, RoomId roomId) {
        Objects.requireNonNull(roomId, "roomId");
        return release(roomId.gameType(), mapName, roomId);
    }

    public boolean release(RoomId roomId) {
        Objects.requireNonNull(roomId, "roomId");
        return release(roomId.gameType(), roomId.mapName(), roomId);
    }

    public boolean release(String gameType, String mapName, RoomId roomId) {
        Objects.requireNonNull(roomId, "roomId");
        OccupancyKey key = OccupancyKey.of(gameType, mapName);
        return owners.remove(key, RoomId.of(key.gameType(), key.mapName()));
    }

    public Optional<RoomId> forceRelease(String gameType, String mapName) {
        return Optional.ofNullable(owners.remove(OccupancyKey.of(gameType, mapName)));
    }

    public boolean isOccupied(String gameType, String mapName) {
        return owners.containsKey(OccupancyKey.of(gameType, mapName));
    }

    public boolean isOccupied(RoomId roomId) {
        Objects.requireNonNull(roomId, "roomId");
        return isOccupied(roomId.gameType(), roomId.mapName());
    }

    public Optional<RoomId> owner(String gameType, String mapName) {
        return Optional.ofNullable(owners.get(OccupancyKey.of(gameType, mapName)));
    }

    public Optional<RoomId> owner(RoomId roomId) {
        Objects.requireNonNull(roomId, "roomId");
        return owner(roomId.gameType(), roomId.mapName());
    }

    public void clear() {
        owners.clear();
    }

    private static String canonicalGameType(String gameType) {
        String canonical = GameModeRegistry.canonicalize(gameType);
        if (canonical.isBlank()) {
            canonical = DEFAULT_GAME_TYPE;
        }
        return canonical;
    }

    private static String canonicalMapName(String mapName) {
        return Objects.requireNonNullElse(mapName, "").trim().toLowerCase(Locale.ROOT);
    }

    private record OccupancyKey(String gameType, String mapName) {
        private OccupancyKey {
            gameType = canonicalGameType(gameType);
            mapName = canonicalMapName(mapName);
            if (mapName.isBlank()) {
                throw new IllegalArgumentException("mapName must not be blank");
            }
        }

        private static OccupancyKey of(String gameType, String mapName) {
            return new OccupancyKey(gameType, mapName);
        }

        private String encoded() {
            return gameType + "|" + mapName;
        }
    }
}
