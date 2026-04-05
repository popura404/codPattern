package com.cdp.codpattern.network.handler;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.tdm.RoomListSyncPacket.RoomInfo;
import com.cdp.codpattern.network.tdm.RoomPlayerDeltaPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

public final class ClientPacketBridge {
    private static final Handler NOOP = new Handler() {
    };

    private static volatile Handler handler = NOOP;

    private ClientPacketBridge() {
    }

    public static void install(Handler handler) {
        ClientPacketBridge.handler = handler == null ? NOOP : handler;
    }

    public static void openBackpackScreen() {
        handler.openBackpackScreen();
    }

    public static void syncBackpackConfig(String configJson) {
        handler.syncBackpackConfig(configJson);
    }

    public static void syncAttachmentPreset(int bagId, String slot, String presetPayload, String expectedGunId) {
        handler.syncAttachmentPreset(bagId, slot, presetPayload, expectedGunId);
    }

    public static void updateWeaponResult(boolean success, String code, String message) {
        handler.updateWeaponResult(success, code, message);
    }

    public static void syncThrowableInventory(ItemStack[] stacks, int activeSlot) {
        handler.syncThrowableInventory(stacks, activeSlot);
    }

    public static void voteDialog(String roomName, long voteId, String voteType, String initiatorName,
            int requiredVotes, int totalVoters) {
        handler.voteDialog(roomName, voteId, voteType, initiatorName, requiredVotes, totalVoters);
    }

    public static void roomListSync(long snapshotVersion, Map<RoomId, RoomInfo> rooms) {
        handler.roomListSync(snapshotVersion, rooms);
    }

    public static void teamPlayerList(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        handler.teamPlayerList(roomKey, rosterVersion, teamPlayers);
    }

    public static void roomPreviewRoster(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        handler.roomPreviewRoster(roomKey, rosterVersion, teamPlayers);
    }

    public static void roomPlayerDelta(
            String roomKey,
            int rosterVersion,
            List<RoomPlayerDeltaPacket.PlayerDelta> updates
    ) {
        handler.roomPlayerDelta(roomKey, rosterVersion, updates);
    }

    public static void popupNotice(Component title, Component message) {
        handler.popupNotice(title, message);
    }

    public static void deathCam(String killerName, int respawnDelayTicks) {
        handler.deathCam(killerName, respawnDelayTicks);
    }

    public static void killFeed(String killerName, String victimName, ItemStack weaponStack, boolean blunder) {
        handler.killFeed(killerName, victimName, weaponStack, blunder);
    }

    public static void physicsMobRetain(int entityId, double x, double y, double z, float yRot, float xRot,
            float yHeadRot, float yBodyRot, double motionX, double motionY, double motionZ) {
        handler.physicsMobRetain(entityId, x, y, z, yRot, xRot, yHeadRot, yBodyRot, motionX, motionY, motionZ);
    }

    public static void gamePhase(String phase, int remainingTicks) {
        handler.gamePhase(phase, remainingTicks);
    }

    public static void countdown(int countdown, boolean blackout) {
        handler.countdown(countdown, blackout);
    }

    public static void scoreUpdate(Map<String, Integer> teamScores, int team1Score, int team2Score,
            int gameTimeTicks) {
        handler.scoreUpdate(teamScores, team1Score, team2Score, gameTimeTicks);
    }

    public static void combatMarkerConfig(float focusHalfAngleDegrees, int focusRequiredTicks,
            double barMaxDistance, int barVisibleGraceTicks) {
        handler.combatMarkerConfig(focusHalfAngleDegrees, focusRequiredTicks, barMaxDistance, barVisibleGraceTicks);
    }

    public static void joinRoomResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        handler.joinRoomResult(success, roomKey, reasonCode, reasonMessage);
    }

    public static void leaveRoomResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        handler.leaveRoomResult(success, roomKey, reasonCode, reasonMessage);
    }

    public interface Handler {
        default void openBackpackScreen() {
        }

        default void syncBackpackConfig(String configJson) {
        }

        default void syncAttachmentPreset(int bagId, String slot, String presetPayload, String expectedGunId) {
        }

        default void updateWeaponResult(boolean success, String code, String message) {
        }

        default void syncThrowableInventory(ItemStack[] stacks, int activeSlot) {
        }

        default void voteDialog(String roomName, long voteId, String voteType, String initiatorName,
                int requiredVotes, int totalVoters) {
        }

        default void roomListSync(long snapshotVersion, Map<RoomId, RoomInfo> rooms) {
        }

        default void teamPlayerList(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        }

        default void roomPreviewRoster(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        }

        default void roomPlayerDelta(String roomKey, int rosterVersion,
                List<RoomPlayerDeltaPacket.PlayerDelta> updates) {
        }

        default void popupNotice(Component title, Component message) {
        }

        default void deathCam(String killerName, int respawnDelayTicks) {
        }

        default void killFeed(String killerName, String victimName, ItemStack weaponStack, boolean blunder) {
        }

        default void physicsMobRetain(int entityId, double x, double y, double z, float yRot, float xRot,
                float yHeadRot, float yBodyRot, double motionX, double motionY, double motionZ) {
        }

        default void gamePhase(String phase, int remainingTicks) {
        }

        default void countdown(int countdown, boolean blackout) {
        }

        default void scoreUpdate(Map<String, Integer> teamScores, int team1Score, int team2Score,
                int gameTimeTicks) {
        }

        default void combatMarkerConfig(float focusHalfAngleDegrees, int focusRequiredTicks,
                double barMaxDistance, int barVisibleGraceTicks) {
        }

        default void joinRoomResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        }

        default void leaveRoomResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        }
    }
}
