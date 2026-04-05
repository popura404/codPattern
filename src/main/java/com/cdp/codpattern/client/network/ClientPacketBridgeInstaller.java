package com.cdp.codpattern.client.network;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.core.throwable.ThrowableInventoryService;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.handler.ClientPacketBridge;
import com.cdp.codpattern.network.handler.ClientPacketHandler;
import com.cdp.codpattern.network.tdm.RoomListSyncPacket.RoomInfo;
import com.cdp.codpattern.network.tdm.RoomPlayerDeltaPacket;
import com.phasetranscrystal.fpsmatch.common.client.FpsmClientPacketHandler;
import com.phasetranscrystal.fpsmatch.common.packet.FpsmClientPacketBridge;
import com.phasetranscrystal.fpsmatch.common.packet.OpenMapCreatorToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenSpawnPointToolScreenS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

public final class ClientPacketBridgeInstaller {
    private ClientPacketBridgeInstaller() {
    }

    public static void install() {
        ClientPacketBridge.install(new ClientPacketBridge.Handler() {
            @Override
            public void openBackpackScreen() {
                ClientPacketHandler.handleOpenBackpackScreen();
            }

            @Override
            public void syncBackpackConfig(String configJson) {
                ClientPacketHandler.handleSyncBackpackConfig(configJson);
            }

            @Override
            public void syncAttachmentPreset(int bagId, String slot, String presetPayload, String expectedGunId) {
                ClientPacketHandler.handleSyncAttachmentPreset(bagId, slot, presetPayload, expectedGunId);
            }

            @Override
            public void updateWeaponResult(boolean success, String code, String message) {
                ClientPacketHandler.handleUpdateWeaponResult(success, code, message);
            }

            @Override
            public void syncThrowableInventory(ItemStack[] stacks, int activeSlot) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) {
                    ThrowableInventoryService.applyClientSync(minecraft.player, stacks, activeSlot);
                }
            }

            @Override
            public void voteDialog(String roomName, long voteId, String voteType, String initiatorName,
                    int requiredVotes, int totalVoters) {
                ClientPacketHandler.handleVoteDialog(
                        roomName,
                        voteId,
                        voteType,
                        initiatorName,
                        requiredVotes,
                        totalVoters);
            }

            @Override
            public void roomListSync(long snapshotVersion, Map<RoomId, RoomInfo> rooms) {
                ClientPacketHandler.handleRoomListSync(snapshotVersion, rooms);
            }

            @Override
            public void teamPlayerList(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
                ClientPacketHandler.handleTeamPlayerList(roomKey, rosterVersion, teamPlayers);
            }

            @Override
            public void roomPreviewRoster(String roomKey, int rosterVersion,
                    Map<String, List<PlayerInfo>> teamPlayers) {
                ClientPacketHandler.handleRoomPreviewRoster(roomKey, rosterVersion, teamPlayers);
            }

            @Override
            public void roomPlayerDelta(String roomKey, int rosterVersion,
                    List<RoomPlayerDeltaPacket.PlayerDelta> updates) {
                ClientPacketHandler.handleRoomPlayerDelta(roomKey, rosterVersion, updates);
            }

            @Override
            public void popupNotice(Component title, Component message) {
                ClientPacketHandler.handlePopupNotice(title, message);
            }

            @Override
            public void deathCam(String killerName, int respawnDelayTicks) {
                ClientPacketHandler.handleDeathCam(killerName, respawnDelayTicks);
            }

            @Override
            public void killFeed(String killerName, String victimName, ItemStack weaponStack, boolean blunder) {
                ClientPacketHandler.handleKillFeed(killerName, victimName, weaponStack, blunder);
            }

            @Override
            public void physicsMobRetain(int entityId, double x, double y, double z, float yRot, float xRot,
                    float yHeadRot, float yBodyRot, double motionX, double motionY, double motionZ) {
                ClientPacketHandler.handlePhysicsMobRetain(
                        entityId,
                        x,
                        y,
                        z,
                        yRot,
                        xRot,
                        yHeadRot,
                        yBodyRot,
                        motionX,
                        motionY,
                        motionZ);
            }

            @Override
            public void gamePhase(String phase, int remainingTicks) {
                ClientPacketHandler.handleGamePhase(phase, remainingTicks);
            }

            @Override
            public void countdown(int countdown, boolean blackout) {
                ClientPacketHandler.handleCountdown(countdown, blackout);
            }

            @Override
            public void scoreUpdate(Map<String, Integer> teamScores, int team1Score, int team2Score,
                    int gameTimeTicks) {
                ClientPacketHandler.handleScoreUpdate(teamScores, team1Score, team2Score, gameTimeTicks);
            }

            @Override
            public void combatMarkerConfig(float focusHalfAngleDegrees, int focusRequiredTicks,
                    double barMaxDistance, int barVisibleGraceTicks) {
                ClientPacketHandler.handleCombatMarkerConfig(
                        focusHalfAngleDegrees,
                        focusRequiredTicks,
                        barMaxDistance,
                        barVisibleGraceTicks);
            }

            @Override
            public void joinRoomResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
                ClientPacketHandler.handleJoinRoomResult(success, roomKey, reasonCode, reasonMessage);
            }

            @Override
            public void leaveRoomResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
                ClientPacketHandler.handleLeaveRoomResult(success, roomKey, reasonCode, reasonMessage);
            }
        });

        FpsmClientPacketBridge.install(new FpsmClientPacketBridge.Handler() {
            @Override
            public void openMapCreatorToolScreen(OpenMapCreatorToolScreenS2CPacket packet) {
                FpsmClientPacketHandler.handleOpenMapCreatorToolScreen(packet);
            }

            @Override
            public void openSpawnPointToolScreen(OpenSpawnPointToolScreenS2CPacket packet) {
                FpsmClientPacketHandler.handleOpenSpawnPointToolScreen(packet);
            }
        });
    }
}
