package com.cdp.codpattern.fpsmatch.room;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.app.match.port.ModeRoomReadPort;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.tdm.RoomListSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 房间管理器（单例）
 * 从 FPSMCore 获取已注册模式的房间摘要
 */
@Mod.EventBusSubscriber(modid = CodPattern.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CodTdmRoomManager {
    private static final long ROOM_PUSH_DIRTY_THROTTLE_MS = 350L;
    private static final long ROOM_PUSH_STEADY_REFRESH_MS = 1000L;
    private static CodTdmRoomManager INSTANCE = null;
    private boolean roomListDirty = true;
    private long lastRoomPushAtMs = 0L;
    private long snapshotVersion = 0L;
    private final Set<UUID> lobbySubscribers = new HashSet<>();

    private CodTdmRoomManager() {
    }

    /**
     * 获取单例实例
     */
    public static CodTdmRoomManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CodTdmRoomManager();
        }
        return INSTANCE;
    }

    public void markRoomListDirty() {
        roomListDirty = true;
    }

    public void subscribeLobbySummary(ServerPlayer player) {
        if (player == null) {
            return;
        }
        lobbySubscribers.add(player.getUUID());
        syncRoomListToClient(player);
    }

    public void unsubscribeLobbySummary(ServerPlayer player) {
        if (player == null) {
            return;
        }
        lobbySubscribers.remove(player.getUUID());
    }

    /**
     * 向客户端同步房间列表
     */
    public void syncRoomListToClient(ServerPlayer player) {
        ModNetworkChannel.sendToPlayer(new RoomListSyncPacket(snapshotVersion, buildRoomInfos()), player);
    }

    private Map<RoomId, RoomListSyncPacket.RoomInfo> buildRoomInfos() {
        Map<RoomId, RoomListSyncPacket.RoomInfo> roomInfos = new HashMap<>();

        for (ModeRoomReadPort readPort : FpsMatchGatewayProvider.gateway().listRoomReadPorts()) {
            Map<String, Integer> teamPlayerCounts = readPort.getTeamPlayerCountsSnapshot();
            int playerCount = teamPlayerCounts.values().stream().mapToInt(Integer::intValue).sum();
            int maxPlayers = readPort.getMaxPlayerCapacity();
            Map<String, Integer> teamScores = readPort.getTeamScoresSnapshot();
            int remainingTimeTicks = readPort.getRemainingTimeTicks();

            RoomListSyncPacket.RoomInfo info = new RoomListSyncPacket.RoomInfo(
                    readPort.phaseName(),
                    playerCount,
                    maxPlayers,
                    teamPlayerCounts,
                    teamScores,
                    remainingTimeTicks,
                    readPort.hasMatchEndTeleportPoint());
            roomInfos.put(readPort.roomId(), info);
        }
        return roomInfos;
    }

    /**
     * 同步房间列表到客户端（重载方法）
     */
    public void syncToClient(ServerPlayer player) {
        syncRoomListToClient(player);
    }

    private void pushRoomListToAllPlayers() {
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            return;
        }
        Map<UUID, ServerPlayer> onlinePlayers = new HashMap<>();
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            onlinePlayers.put(player.getUUID(), player);
        }
        lobbySubscribers.retainAll(onlinePlayers.keySet());
        if (lobbySubscribers.isEmpty()) {
            return;
        }

        RoomListSyncPacket packet = new RoomListSyncPacket(snapshotVersion, buildRoomInfos());
        for (UUID subscriberId : lobbySubscribers) {
            ServerPlayer player = onlinePlayers.get(subscriberId);
            if (player != null) {
                ModNetworkChannel.sendToPlayer(packet, player);
            }
        }
    }

    private void flushPendingRoomPush() {
        if (lobbySubscribers.isEmpty()) {
            roomListDirty = false;
            return;
        }

        long now = System.currentTimeMillis();
        boolean dueForSteadyRefresh = now - lastRoomPushAtMs >= ROOM_PUSH_STEADY_REFRESH_MS;
        if (!roomListDirty && !dueForSteadyRefresh) {
            return;
        }
        if (roomListDirty && now - lastRoomPushAtMs < ROOM_PUSH_DIRTY_THROTTLE_MS) {
            return;
        }
        if (roomListDirty) {
            snapshotVersion++;
        }
        pushRoomListToAllPlayers();
        roomListDirty = false;
        lastRoomPushAtMs = now;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        getInstance().flushPendingRoomPush();
    }
}
