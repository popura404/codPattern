package com.cdp.codpattern.network.handler;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.client.gui.screen.BackpackMenuScreen;
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
        String text = message.isBlank() ? "配装写入被拒绝: " + code : message;
        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c" + text));
    }

    public static void handleRoomListSync(Map<String, RoomInfo> rooms) {
        Minecraft.getInstance().execute(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof TdmRoomScreen tdmScreen) {
                Map<String, TdmRoomData> roomDataMap = new HashMap<>();
                for (Map.Entry<String, RoomInfo> entry : rooms.entrySet()) {
                    RoomInfo info = entry.getValue();
                    roomDataMap.put(entry.getKey(), new TdmRoomData(
                            entry.getKey(),
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

    public static void handleTeamPlayerList(String mapName, Map<String, List<PlayerInfo>> teamPlayers) {
        Minecraft.getInstance().execute(() -> {
            ClientTdmState.updateTeamPlayers(mapName, teamPlayers);
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof TdmRoomScreen tdmScreen) {
                tdmScreen.updatePlayerList(mapName, teamPlayers);
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

    public static void handleJoinRoomResult(boolean success, String mapName, String reasonCode, String reasonMessage) {
        Minecraft.getInstance().execute(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof TdmRoomScreen tdmRoomScreen) {
                tdmRoomScreen.handleJoinResult(success, mapName, reasonCode, reasonMessage);
            }
        });
    }

    public static void handleLeaveRoomResult(boolean success, String roomName, String reasonCode, String reasonMessage) {
        Minecraft.getInstance().execute(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof TdmRoomScreen tdmRoomScreen) {
                tdmRoomScreen.handleLeaveResult(success, roomName, reasonCode, reasonMessage);
            }
            if (!success && Minecraft.getInstance().player != null) {
                String message = reasonMessage.isBlank() ? "离开房间失败: " + reasonCode : reasonMessage;
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c" + message));
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
        snapshot.setDeltaMovement(motionX, motionY, motionZ);
        snapshot.setHealth(Math.max(0.01f, sourcePlayer.getHealth()));

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            snapshot.setItemSlot(slot, sourcePlayer.getItemBySlot(slot).copy());
        }

        PhysicsModClientBridge.blockifySnapshot(level, snapshot);
    }
}
