package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

public interface ModePlayerRuntimeStatePort extends ModeRoomIdentityPort {
    Map<String, ModePlayerValue> getPlayerState(UUID playerId);

    void setPlayerValue(UUID playerId, String key, ModePlayerValue value);

    void clearPlayerState(UUID playerId);

    void clearRoomState();

    Map<String, ModePlayerValue> snapshotForClient(ServerPlayer viewer);

    default boolean shouldClearRoomStateOnLifecycleStateChange(String previousStateKey, String currentStateKey) {
        return isActiveLifecycleState(previousStateKey) && isCleanupLifecycleState(currentStateKey);
    }

    private static boolean isActiveLifecycleState(String stateKey) {
        return !isCleanupLifecycleState(stateKey);
    }

    private static boolean isCleanupLifecycleState(String stateKey) {
        if (stateKey == null) {
            return false;
        }
        return switch (stateKey.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "WAITING", "ENDED", "RESET", "RESETTING", "STOPPED", "IDLE" -> true;
            default -> false;
        };
    }
}
