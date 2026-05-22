package com.cdp.codpattern.network.handler;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.model.ModeObjectState;
import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;
import com.cdp.codpattern.client.ClientMatchState;
import com.cdp.codpattern.client.ClientModeObjectState;
import com.cdp.codpattern.client.ClientModeRuntimeState;
import com.cdp.codpattern.client.TdmCombatMarkerTracker;
import com.cdp.codpattern.client.gui.screen.BackpackMenuScreen;
import com.cdp.codpattern.client.gui.screen.ModeRoomScreen;
import com.cdp.codpattern.client.gui.screen.NoticePopupScreen;
import com.cdp.codpattern.client.gui.screen.PopupNoticeHelper;
import com.cdp.codpattern.client.gui.screen.match.ModeRoomData;
import com.cdp.codpattern.client.network.ModeRoomClientPackets;
import com.cdp.codpattern.client.refit.AttachmentRefitClientState;
import com.cdp.codpattern.compat.physicsmod.PhysicsModClientBridge;
import com.cdp.codpattern.config.backpack.BackpackClientCache;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.match.RoomRosterDelta;
import com.cdp.codpattern.network.match.RoomSyncInfo;
import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {
    private static final Gson GSON = new Gson();
    private static Screen activeVoteDialogScreen;
    private static Screen activeVoteDialogPreviousScreen;

    public static void handleOpenBackpackScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            //一直想把这个生草的COW_HURT音效替换掉，找了半天忘记放哪了，结果在这。。。
            minecraft.player.playNotifySound(SoundEvents.BRUSH_GRAVEL, SoundSource.PLAYERS, 1.5f, 1f);
            minecraft.setScreen(new BackpackMenuScreen());
        }
    }

    public static void handleVoteDialog(String roomName, long voteId, String voteType, String initiatorName,
            int requiredVotes, int totalVoters) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        closeActiveVoteDialog(minecraft);
        Screen previous = minecraft.screen;
        boolean startVote = "START".equalsIgnoreCase(voteType);
        Component title = startVote
                ? Component.translatable("screen.codpattern.vote_dialog.title_start")
                : Component.translatable("screen.codpattern.vote_dialog.title_end");
        Component message = startVote
                ? Component.translatable("screen.codpattern.vote_dialog.message_start", initiatorName, roomName,
                        requiredVotes, totalVoters)
                : Component.translatable("screen.codpattern.vote_dialog.message_end", initiatorName, roomName,
                        requiredVotes, totalVoters);

        final ConfirmScreen[] voteDialogHolder = new ConfirmScreen[1];
        ConfirmScreen voteDialog = new ConfirmScreen(accepted -> {
            clearActiveVoteDialog(voteDialogHolder[0]);
            minecraft.setScreen(previous);
            ModeRoomClientPackets.respondToVote(voteId, accepted);
        },
                title,
                message,
                Component.translatable("screen.codpattern.vote_dialog.accept"),
                Component.translatable("screen.codpattern.vote_dialog.reject"));
        voteDialogHolder[0] = voteDialog;
        activeVoteDialogScreen = voteDialog;
        activeVoteDialogPreviousScreen = previous;
        minecraft.setScreen(voteDialog);
    }

    public static void handlePopupNotice(Component title, Component message) {
        Minecraft.getInstance().execute(() -> {
            Screen current = Minecraft.getInstance().screen;
            if (current instanceof ModeRoomScreen || current instanceof NoticePopupScreen) {
                PopupNoticeHelper.show(title, message);
                return;
            }
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(message);
            }
        });
    }

    public static void handleSyncBackpackConfig(String configJson) {
        BackpackConfig.PlayerBackpackData playerData =
                GSON.fromJson(configJson, BackpackConfig.PlayerBackpackData.class);
        BackpackClientCache.set(playerData);

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BackpackMenuScreen screen) {
            screen.reloadFromPlayerData();
        }
    }

    public static void handleSyncAttachmentPreset(int bagId, String slot, String presetPayload, String expectedGunId) {
        AttachmentRefitClientState.onPresetSync(bagId, slot, presetPayload, expectedGunId);
    }

    public static void handleSyncAttachmentCandidates(int bagId, String slot, List<ItemStack> attachmentCandidates) {
        AttachmentRefitClientState.onAttachmentCandidatesSync(bagId, slot, attachmentCandidates);
    }

    public static void handleUpdateWeaponResult(boolean success, String code, String message) {
        if (success || Minecraft.getInstance().player == null) {
            return;
        }
        Minecraft.getInstance().player.sendSystemMessage(resolveWeaponUpdateFailure(code, message));
    }

    public static void handleRoomListSync(long snapshotVersion, Map<RoomId, ? extends RoomSyncInfo> rooms) {
        Minecraft.getInstance().execute(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof ModeRoomScreen modeRoomScreen) {
                Map<String, ModeRoomData> roomDataMap = new HashMap<>();
                for (Map.Entry<RoomId, ? extends RoomSyncInfo> entry : rooms.entrySet()) {
                    RoomId roomId = entry.getKey();
                    RoomSyncInfo info = entry.getValue();
                    roomDataMap.put(roomId.encode(), new ModeRoomData(
                            roomId.gameType(),
                            roomId.mapName(),
                            info.state,
                            info.playerCount,
                            info.maxPlayers,
                            info.teamPlayerCounts,
                            info.teamScores,
                            info.remainingTimeTicks,
                            info.hasMatchEndTeleportPoint,
                            info.metrics,
                            info.capabilities));
                }
                modeRoomScreen.updateRoomList(snapshotVersion, roomDataMap);
            }
        });
    }

    public static void handleTeamPlayerList(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        Minecraft.getInstance().execute(() -> {
            ClientMatchState.updateTeamPlayers(roomKey, rosterVersion, teamPlayers);
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof ModeRoomScreen modeRoomScreen) {
                modeRoomScreen.updatePlayerList(roomKey, rosterVersion, teamPlayers);
            }
        });
    }

    public static void handleRoomPreviewRoster(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        Minecraft.getInstance().execute(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof ModeRoomScreen modeRoomScreen) {
                modeRoomScreen.updatePreviewPlayerList(roomKey, rosterVersion, teamPlayers);
            }
        });
    }

    public static void handleRoomPlayerDelta(
            String roomKey,
            int rosterVersion,
            List<RoomRosterDelta> updates
    ) {
        Minecraft.getInstance().execute(() -> {
            ClientMatchState.RosterDeltaApplyResult result = ClientMatchState.applyTeamPlayerDelta(
                    roomKey,
                    rosterVersion,
                    updates);
            if (result != ClientMatchState.RosterDeltaApplyResult.APPLIED) {
                ModeRoomClientPackets.requestRoomRosterResync();
            }
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof ModeRoomScreen modeRoomScreen) {
                modeRoomScreen.updatePlayerDelta(roomKey, rosterVersion);
            }
        });
    }

    public static void handleDeathCam(String killerName, int deathCamTicks, int respawnDelayTicks, float lockedYaw, float lockedPitch) {
        Minecraft.getInstance().execute(() -> {
            if (respawnDelayTicks <= 0) {
                ClientMatchState.clearDeathCam();
                return;
            }
            ClientMatchState.setDeathCam(killerName, respawnDelayTicks, deathCamTicks > 0, lockedYaw, lockedPitch);
        });
    }

    public static void handleKillFeed(String killerName, String victimName, ItemStack weaponStack, boolean blunder) {
        Minecraft.getInstance().execute(() -> ClientMatchState.pushKillFeed(killerName, victimName, weaponStack, blunder));
    }

    public static void handleGamePhase(String phase, int remainingTicks) {
        Minecraft.getInstance().execute(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if ("COUNTDOWN".equals(phase) || "WARMUP".equals(phase)) {
                closeMatchStartScreens(minecraft);
            } else if ("ENDED".equals(phase)) {
                closeActiveVoteDialog(minecraft);
            }
            ClientMatchState.updatePhase(phase, remainingTicks);
        });
    }

    public static void handleCountdown(int countdown, boolean blackout) {
        Minecraft.getInstance().execute(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (countdown > 0 || blackout) {
                closeMatchStartScreens(minecraft);
            }
            ClientMatchState.updateCountdown(countdown, blackout);
        });
    }

    public static void handleScoreUpdate(Map<String, Integer> teamScores, int team1Score, int team2Score,
            int gameTimeTicks) {
        Minecraft.getInstance().execute(
                () -> ClientMatchState.updateScore(teamScores, team1Score, team2Score, gameTimeTicks));
    }

    public static void handleCombatMarkerConfig(float focusHalfAngleDegrees,
            int focusRequiredTicks,
            double barMaxDistance,
            int barVisibleGraceTicks) {
        Minecraft.getInstance().execute(() -> TdmCombatMarkerTracker.INSTANCE.updateConfig(
                focusHalfAngleDegrees,
                focusRequiredTicks,
                barMaxDistance,
                barVisibleGraceTicks));
    }

    public static void handleModeRuntimeState(ModeRuntimeStateSnapshot snapshot) {
        Minecraft.getInstance().execute(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            closeStaleModeVoteDialog(minecraft, snapshot);
            ClientModeRuntimeState.update(snapshot);
        });
    }

    public static void handleModeObjectStates(String roomKey, List<ModeObjectState> states, long revision) {
        Minecraft.getInstance().execute(() -> ClientModeObjectState.replaceRoomStates(roomKey, states, revision));
    }

    public static void handleJoinRoomResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        Minecraft.getInstance().execute(() -> {
            if (success) {
                ClientMatchState.setRoomContext(roomKey);
            }
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof ModeRoomScreen modeRoomScreen) {
                modeRoomScreen.handleJoinResult(success, roomKey, reasonCode, reasonMessage);
            }
        });
    }

    public static void handleLeaveRoomResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        Minecraft.getInstance().execute(() -> {
            if (success) {
                String clearedRoomKey = roomKey == null || roomKey.isBlank()
                        ? ClientMatchState.roomContextName()
                        : roomKey;
                ClientModeRuntimeState.clear(clearedRoomKey);
                ClientModeObjectState.clear(clearedRoomKey);
                ClientMatchState.clearRoomContext();
            }
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof ModeRoomScreen modeRoomScreen) {
                modeRoomScreen.handleLeaveResult(success, roomKey, reasonCode, reasonMessage);
            }
            if (!success
                    && !(screen instanceof ModeRoomScreen)
                    && Minecraft.getInstance().player != null) {
                PopupNoticeHelper.show(Component.translatable(
                        "screen.codpattern.tdm_room.error.leave_failed",
                        resolveRoomActionReason(reasonCode, reasonMessage)));
            }
        });
    }

    public static void handlePhysicsMobRetain(int entityId, double x, double y, double z, float yRot, float xRot,
            float yHeadRot, float yBodyRot, double motionX, double motionY, double motionZ) {
        if (!PhysicsModClientBridge.isPhysicsModLoaded()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        Entity source = level.getEntity(entityId);
        if (!(source instanceof Player sourcePlayer)) {
            return;
        }

        GameProfile profile = sourcePlayer.getGameProfile();
        RemotePlayer snapshot = new RemotePlayer(level, profile);
        snapshot.setPos(x, y, z);
        snapshot.setYRot(yRot);
        snapshot.setXRot(xRot);
        snapshot.yRotO = yRot;
        snapshot.xRotO = xRot;
        snapshot.xo = x;
        snapshot.yo = y;
        snapshot.zo = z;
        snapshot.setOldPosAndRot();
        snapshot.yHeadRot = yHeadRot;
        snapshot.yHeadRotO = yHeadRot;
        snapshot.yBodyRot = yBodyRot;
        snapshot.yBodyRotO = yBodyRot;
        double compensatedMotionY = compensatePlayerRagdollMotionY(level, x, y, z, motionY);
        snapshot.setDeltaMovement(motionX, compensatedMotionY, motionZ);
        snapshot.setHealth(Math.max(0.01f, sourcePlayer.getHealth()));

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            snapshot.setItemSlot(slot, sourcePlayer.getItemBySlot(slot).copy());
        }

        PhysicsModClientBridge.blockifySnapshot(level, snapshot);
    }

    /**
     * physicsmod 会在玩家实体 blockify 时额外注入一段朝上的 ragdoll 初速度（y=2 归一化后再 *5）。
     * 这里把该分量预先从快照速度里抵消，避免玩家死亡残影“上抛”。
     */
    private static double compensatePlayerRagdollMotionY(ClientLevel level, double x, double y, double z,
            double originalMotionY) {
        Player nearestPlayer = level.getNearestPlayer(x, y, z, 8.0, false);
        if (nearestPlayer == null) {
            return originalMotionY;
        }

        double dx = x - nearestPlayer.getX();
        double dz = z - nearestPlayer.getZ();
        double length = Math.sqrt(dx * dx + 4.0 + dz * dz);
        if (length <= 1.0E-6) {
            return originalMotionY;
        }

        double injectedUpwardVelocity = 10.0 / length;
        return originalMotionY - injectedUpwardVelocity / 10.0;
    }

    private static void closeActiveVoteDialog(Minecraft minecraft) {
        closeActiveVoteDialog(minecraft, false);
    }

    private static void closeStaleModeVoteDialog(Minecraft minecraft, ModeRuntimeStateSnapshot snapshot) {
        if (snapshot == null || "START_VOTE".equalsIgnoreCase(snapshot.phaseKey())) {
            return;
        }
        closeActiveVoteDialog(minecraft, true);
    }

    private static void closeActiveVoteDialog(Minecraft minecraft, boolean restorePreviousScreen) {
        Screen dialogScreen = activeVoteDialogScreen;
        if (dialogScreen == null) {
            return;
        }
        Screen previousScreen = activeVoteDialogPreviousScreen;
        clearActiveVoteDialog(dialogScreen);
        if (minecraft.screen == dialogScreen) {
            minecraft.setScreen(restorePreviousScreen ? previousScreen : null);
        }
    }

    private static void closeMatchStartScreens(Minecraft minecraft) {
        closeActiveVoteDialog(minecraft);
        if (minecraft.screen != null) {
            minecraft.setScreen(null);
        }
    }

    private static void clearActiveVoteDialog(Screen dialogScreen) {
        if (activeVoteDialogScreen != dialogScreen) {
            return;
        }
        activeVoteDialogScreen = null;
        activeVoteDialogPreviousScreen = null;
    }

    private static Component resolveWeaponUpdateFailure(String code, String message) {
        if (message != null && !message.isBlank()) {
            return Component.literal(message);
        }
        return switch (code == null ? "" : code) {
            case "BAG_NOT_FOUND" -> Component.translatable("message.codpattern.weapon_update.error.bag_not_found");
            case "SLOT_INVALID" -> Component.translatable("message.codpattern.weapon_update.error.slot_invalid");
            case "ITEM_ID_INVALID" -> Component.translatable("message.codpattern.weapon_update.error.item_id_invalid");
            case "ITEM_NOT_REGISTERED" -> Component.translatable("message.codpattern.weapon_update.error.item_not_registered");
            case "NBT_INVALID" -> Component.translatable("message.codpattern.weapon_update.error.nbt_invalid");
            case "ITEM_BLOCKED" -> Component.translatable("message.codpattern.weapon_update.error.item_blocked");
            case "ITEM_NAMESPACE_BLOCKED" -> Component.translatable("message.codpattern.weapon_update.error.item_namespace_blocked");
            case "ATTACHMENT_BLOCKED" -> Component.translatable("message.codpattern.weapon_update.error.attachment_blocked");
            case "ITEM_CATEGORY_INVALID" -> Component.translatable("message.codpattern.weapon_update.error.item_category_invalid");
            case "THROWABLES_DISABLED" -> Component.translatable("message.codpattern.weapon_update.error.throwables_disabled");
            case "THROWABLES_UNAVAILABLE" -> Component.translatable("message.codpattern.weapon_update.error.throwables_unavailable");
            default -> Component.translatable("message.codpattern.weapon_update.error.unknown",
                    code == null || code.isBlank() ? "UNKNOWN" : code);
        };
    }

    private static Component resolveRoomActionReason(String reasonCode, String reasonMessage) {
        if (reasonMessage != null && !reasonMessage.isBlank()) {
            return Component.literal(reasonMessage);
        }
        return switch (reasonCode == null ? "" : reasonCode) {
            case "MAP_NOT_FOUND" -> Component.translatable("screen.codpattern.tdm_room.error.map_not_found");
            case "PHASE_LOCKED" -> Component.translatable("screen.codpattern.tdm_room.error.phase_locked");
            case "TEAM_NOT_FOUND" -> Component.translatable("screen.codpattern.tdm_room.error.team_not_found");
            case "TEAM_FULL" -> Component.translatable("screen.codpattern.tdm_room.error.team_full");
            case "TEAM_BALANCE_EXCEEDED" -> Component.translatable("screen.codpattern.tdm_room.error.team_balance_exceeded");
            case "NOT_IN_ROOM" -> Component.translatable("screen.codpattern.tdm_room.error.not_in_room");
            case "UNKNOWN" -> Component.translatable("screen.codpattern.tdm_room.error.unknown");
            default -> Component.translatable("screen.codpattern.tdm_room.error.unknown");
        };
    }
}
