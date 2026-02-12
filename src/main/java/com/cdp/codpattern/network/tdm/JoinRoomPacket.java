package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * C→S: 加入房间数据包
 */
public class JoinRoomPacket {
    private static final String CODE_MAP_NOT_FOUND = "MAP_NOT_FOUND";
    private static final String CODE_PHASE_LOCKED = "PHASE_LOCKED";
    private static final String CODE_TEAM_NOT_FOUND = "TEAM_NOT_FOUND";
    private static final String CODE_TEAM_FULL = "TEAM_FULL";
    private static final String CODE_BALANCE_EXCEEDED = "TEAM_BALANCE_EXCEEDED";
    private static final String CODE_UNKNOWN = "UNKNOWN";

    private final String mapName;
    private final String teamName; // 可选，null表示自动分配

    public JoinRoomPacket(String mapName, String teamName) {
        this.mapName = mapName;
        this.teamName = teamName;
    }

    public JoinRoomPacket(FriendlyByteBuf buf) {
        this.mapName = buf.readUtf();
        this.teamName = buf.readBoolean() ? buf.readUtf() : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(mapName);
        buf.writeBoolean(teamName != null);
        if (teamName != null) {
            buf.writeUtf(teamName);
        }
    }

    public static JoinRoomPacket decode(FriendlyByteBuf buf) {
        return new JoinRoomPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            Optional<CodTdmMap> mapOptional = FpsMatchGatewayProvider.gateway().findMapByName(mapName);
            if (mapOptional.isEmpty()) {
                sendResult(player, false, mapName, CODE_MAP_NOT_FOUND, "地图不存在");
                return;
            }
            CodTdmMap map = mapOptional.get();

            CodTdmRoomManager roomManager = CodTdmRoomManager.getInstance();
            CodTdmConfig config = CodTdmConfig.getConfig();

            if (map.checkGameHasPlayer(player.getUUID()) || map.checkSpecHasPlayer(player)) {
                sendResult(player, true, mapName, "ALREADY_JOINED", "");
                return;
            }

            FpsMatchGatewayProvider.gateway().leaveCurrentMapIfDifferent(player, map);

            CodTdmMap.GamePhase phase = map.getPhase();
            boolean isPlaying = phase == CodTdmMap.GamePhase.PLAYING;

            if (isPlaying && !config.isAllowJoinDuringPlaying()) {
                sendResult(player, false, mapName, CODE_PHASE_LOCKED, "对局进行中，当前配置禁止中途加入");
                return;
            }
            if (!isPlaying && phase != CodTdmMap.GamePhase.WAITING) {
                sendResult(player, false, mapName, CODE_PHASE_LOCKED, "当前阶段不可加入房间");
                return;
            }

            if (isPlaying && config.isJoinAsSpectatorWhenPlaying()) {
                map.joinSpec(player);
                if (!map.hasMatchEndTeleportPoint()) {
                    player.sendSystemMessage(
                            Component.translatable("message.codpattern.game.warning_no_end_teleport", map.mapName));
                }
                map.syncToClient();
                roomManager.markRoomListDirty();
                sendResult(player, true, mapName, "SPECTATOR", "");
                return;
            }

            String requestedTeam = normalizeTeam(teamName);
            if (requestedTeam != null && !map.getMapTeams().checkTeam(requestedTeam)) {
                sendResult(player, false, mapName, CODE_TEAM_NOT_FOUND, "队伍不存在");
                return;
            }

            String targetTeam = requestedTeam;
            if (targetTeam == null) {
                Optional<String> autoTeam = map.chooseAutoJoinTeam(config.getMaxTeamDiff());
                if (autoTeam.isEmpty()) {
                    sendResult(player, false, mapName, CODE_BALANCE_EXCEEDED, "无法分配队伍：队伍已满或超出人数差限制");
                    return;
                }
                targetTeam = autoTeam.get();
            } else {
                if (map.getMapTeams().testTeamIsFull(targetTeam)) {
                    sendResult(player, false, mapName, CODE_TEAM_FULL, "目标队伍已满");
                    return;
                }
                if (!map.canJoinWithBalance(targetTeam, config.getMaxTeamDiff())) {
                    sendResult(player, false, mapName, CODE_BALANCE_EXCEEDED, "加入该队伍会超出人数差限制");
                    return;
                }
            }

            map.join(targetTeam, player);
            if (!map.checkGameHasPlayer(player.getUUID())) {
                sendResult(player, false, mapName, CODE_UNKNOWN, "加入失败，请稍后重试");
                return;
            }

            map.initializeReadyState(player);
            if (!map.hasMatchEndTeleportPoint()) {
                player.sendSystemMessage(
                        Component.translatable("message.codpattern.game.warning_no_end_teleport", map.mapName));
            }
            map.syncToClient();
            roomManager.markRoomListDirty();
            sendResult(player, true, mapName, "OK", "");
        });
        ctx.get().setPacketHandled(true);
    }

    private static String normalizeTeam(String team) {
        if (team == null || team.isBlank()) {
            return null;
        }
        return team.trim();
    }

    private static void sendResult(ServerPlayer player, boolean success, String mapName, String code, String message) {
        CodTdmRoomManager.getInstance().markRoomListDirty();
        JoinRoomResultPacket packet = new JoinRoomResultPacket(success, mapName, code, message);
        com.cdp.codpattern.adapter.forge.network.ModNetworkChannel.sendToPlayer(packet, player);
    }
}
