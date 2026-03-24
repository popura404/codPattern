package com.cdp.codpattern.network.handler;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.client.TdmCombatMarkerTracker;
import com.cdp.codpattern.client.gui.screen.BackpackMenuScreen;
import com.cdp.codpattern.client.gui.screen.NoticePopupScreen;
import com.cdp.codpattern.client.gui.screen.PopupNoticeHelper;
import com.cdp.codpattern.client.gui.screen.TdmRoomScreen;
import com.cdp.codpattern.client.gui.screen.tdm.TdmRoomData;
import com.cdp.codpattern.client.refit.AttachmentRefitClientState;
import com.cdp.codpattern.compat.physicsmod.PhysicsModClientBridge;
import com.cdp.codpattern.config.backpack.BackpackClientCache;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.network.tdm.VoteResponsePacket;
import com.cdp.codpattern.network.tdm.RoomListSyncPacket.RoomInfo;
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

        minecraft.setScreen(new ConfirmScreen(accepted -> {
            minecraft.setScreen(previous);
            ModNetworkChannel.sendToServer(new VoteResponsePacket(voteId, accepted));
        },
                title,
                message,
                Component.translatable("screen.codpattern.vote_dialog.accept"),
                Component.translatable("screen.codpattern.vote_dialog.reject")));
    }

    public static void handlePopupNotice(Component title, Component message) {
        Minecraft.getInstance().execute(() -> {
            Screen current = Minecraft.getInstance().screen;
            if (current instanceof TdmRoomScreen || current instanceof NoticePopupScreen) {
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

    public static void handleUpdateWeaponResult(boolean success, String code, String message) {
        if (success || Minecraft.getInstance().player == null) {
            return;
        }
        Minecraft.getInstance().player.sendSystemMessage(resolveWeaponUpdateFailure(code, message));
    }

    public static void handleRoomListSync(Map<RoomId, RoomInfo> rooms) {
        Minecraft.getInstance().execute(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof TdmRoomScreen tdmScreen) {
                Map<String, TdmRoomData> roomDataMap = new HashMap<>();
                for (Map.Entry<RoomId, RoomInfo> entry : rooms.entrySet()) {
                    RoomId roomId = entry.getKey();
                    RoomInfo info = entry.getValue();
                    roomDataMap.put(roomId.encode(), new TdmRoomData(
                            roomId.gameType(),
                            roomId.mapName(),
                            info.state,
                            info.playerCount,
                            info.maxPlayers,
                            info.teamPlayerCounts,
                            info.teamScores,
                            info.remainingTimeTicks,
                            info.hasMatchEndTeleportPoint));
                }
                tdmScreen.updateRoomList(roomDataMap);
            }
        });
    }

    public static void handleTeamPlayerList(String roomKey, Map<String, List<PlayerInfo>> teamPlayers) {
        Minecraft.getInstance().execute(() -> {
            ClientTdmState.updateTeamPlayers(roomKey, teamPlayers);
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof TdmRoomScreen tdmScreen) {
                tdmScreen.updatePlayerList(roomKey, teamPlayers);
            }
        });
    }

    public static void handleDeathCam(String killerName, int respawnDelayTicks) {
        Minecraft.getInstance().execute(() -> {
            if (respawnDelayTicks <= 0) {
                ClientTdmState.clearDeathCam();
                return;
            }
            ClientTdmState.setDeathCam(killerName, respawnDelayTicks);
        });
    }

    public static void handleKillFeed(String killerName, String victimName, ItemStack weaponStack, boolean blunder) {
        Minecraft.getInstance().execute(() -> ClientTdmState.pushKillFeed(killerName, victimName, weaponStack, blunder));
    }

    public static void handleGamePhase(String phase, int remainingTicks) {
        Minecraft.getInstance().execute(() -> ClientTdmState.updatePhase(phase, remainingTicks));
    }

    public static void handleCountdown(int countdown, boolean blackout) {
        Minecraft.getInstance().execute(() -> ClientTdmState.updateCountdown(countdown, blackout));
    }

    public static void handleScoreUpdate(Map<String, Integer> teamScores, int team1Score, int team2Score,
            int gameTimeTicks) {
        Minecraft.getInstance().execute(
                () -> ClientTdmState.updateScore(teamScores, team1Score, team2Score, gameTimeTicks));
    }

    public static void handleCombatMarkerConfig(boolean enemyMarkerHealthBar,
            float focusHalfAngleDegrees,
            int focusRequiredTicks,
            double barMaxDistance,
            int barVisibleGraceTicks) {
        Minecraft.getInstance().execute(() -> TdmCombatMarkerTracker.INSTANCE.updateConfig(
                enemyMarkerHealthBar,
                focusHalfAngleDegrees,
                focusRequiredTicks,
                barMaxDistance,
                barVisibleGraceTicks));
    }

    public static void handleJoinRoomResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        Minecraft.getInstance().execute(() -> {
            if (success) {
                ClientTdmState.setRoomContext(roomKey);
            }
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof TdmRoomScreen tdmRoomScreen) {
                tdmRoomScreen.handleJoinResult(success, roomKey, reasonCode, reasonMessage);
            }
        });
    }

    public static void handleLeaveRoomResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        Minecraft.getInstance().execute(() -> {
            if (success) {
                ClientTdmState.clearRoomContext();
            }
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof TdmRoomScreen tdmRoomScreen) {
                tdmRoomScreen.handleLeaveResult(success, roomKey, reasonCode, reasonMessage);
            }
            if (!success
                    && !(screen instanceof TdmRoomScreen)
                    && Minecraft.getInstance().player != null) {
                PopupNoticeHelper.show(Component.translatable(
                        "screen.codpattern.tdm_room.error.leave_failed",
                        resolveRoomActionReason(reasonCode, reasonMessage)));
            }
        });
    }

    public static void handleJoinGameResult(
            boolean success,
            long requestId,
            String roomKey,
            String reasonCode,
            String reasonMessage) {
        Minecraft.getInstance().execute(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof TdmRoomScreen tdmRoomScreen) {
                tdmRoomScreen.handleJoinGameResult(success, requestId, roomKey, reasonCode, reasonMessage);
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
            case "ITEM_CATEGORY_INVALID" -> Component.translatable("message.codpattern.weapon_update.error.item_category_invalid");
            case "THROWABLES_DISABLED" -> Component.translatable("message.codpattern.weapon_update.error.throwables_disabled");
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
            case "MID_JOIN_DISABLED" -> Component.translatable("message.codpattern.room.mid_join_disabled");
            case "TEAM_NOT_FOUND" -> Component.translatable("screen.codpattern.tdm_room.error.team_not_found");
            case "TEAM_FULL" -> Component.translatable("screen.codpattern.tdm_room.error.team_full");
            case "TEAM_BALANCE_EXCEEDED" -> Component.translatable("screen.codpattern.tdm_room.error.team_balance_exceeded");
            case "NOT_IN_ROOM" -> Component.translatable("screen.codpattern.tdm_room.error.not_in_room");
            case "NOT_SPECTATOR" -> Component.translatable("screen.codpattern.tdm_room.error.not_spectator");
            case "UNKNOWN" -> Component.translatable("screen.codpattern.tdm_room.error.unknown");
            default -> Component.translatable("screen.codpattern.tdm_room.error.unknown");
        };
    }
}
