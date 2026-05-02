package com.cdp.codpattern.app.match.runtime;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModePlayerRuntimeStatePort;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ModePlayerRuntimeState implements ModePlayerRuntimeStatePort {
    private final RoomId roomId;
    private final Map<UUID, Map<String, ModePlayerValue>> valuesByPlayer = new HashMap<>();

    public ModePlayerRuntimeState(RoomId roomId) {
        this.roomId = Objects.requireNonNull(roomId, "roomId");
    }

    @Override
    public RoomId roomId() {
        return roomId;
    }

    @Override
    public String gameType() {
        return roomId.gameType();
    }

    @Override
    public String mapName() {
        return roomId.mapName();
    }

    @Override
    public String modeDisplayNameKey() {
        return GameModeRegistry.getOrDefault(gameType()).displayNameKey();
    }

    @Override
    public Map<String, ModePlayerValue> getPlayerState(UUID playerId) {
        if (playerId == null) {
            return Map.of();
        }
        return Map.copyOf(valuesByPlayer.getOrDefault(playerId, Map.of()));
    }

    @Override
    public void setPlayerValue(UUID playerId, String key, ModePlayerValue value) {
        if (playerId == null || key == null || key.isBlank()) {
            return;
        }
        if (value == null) {
            clearPlayerValue(playerId, key);
            return;
        }
        valuesByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>())
                .put(key.trim(), value);
    }

    public void clearPlayerValue(UUID playerId, String key) {
        if (playerId == null || key == null || key.isBlank()) {
            return;
        }
        Map<String, ModePlayerValue> values = valuesByPlayer.get(playerId);
        if (values == null) {
            return;
        }
        values.remove(key.trim());
        if (values.isEmpty()) {
            valuesByPlayer.remove(playerId);
        }
    }

    @Override
    public void clearPlayerState(UUID playerId) {
        if (playerId != null) {
            valuesByPlayer.remove(playerId);
        }
    }

    @Override
    public void clearRoomState() {
        valuesByPlayer.clear();
    }

    @Override
    public Map<String, ModePlayerValue> snapshotForClient(ServerPlayer viewer) {
        return viewer == null ? Map.of() : getPlayerState(viewer.getUUID());
    }
}
