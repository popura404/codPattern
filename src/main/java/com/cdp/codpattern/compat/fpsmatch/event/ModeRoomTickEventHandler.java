package com.cdp.codpattern.compat.fpsmatch.event;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.EntityLifecycleContext;
import com.cdp.codpattern.app.match.model.ModeRoomTickContext;
import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeEntityLifecyclePort;
import com.cdp.codpattern.app.match.port.ModePlayerRuntimeStatePort;
import com.cdp.codpattern.app.match.port.ModeRoomSummaryPort;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.service.ZombiesActiveMobCounter;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGateway;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import com.cdp.codpattern.network.match.ModeObjectStateSyncPacket;
import com.cdp.codpattern.network.match.ModeRuntimeStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModeRoomTickEventHandler {
    private static final int STATE_SYNC_INTERVAL_TICKS = 10;
    private static final Map<String, String> LAST_LIFECYCLE_STATE_BY_ROOM = new ConcurrentHashMap<>();

    private ModeRoomTickEventHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        FpsMatchGateway gateway = FpsMatchGatewayProvider.gateway();
        ModeEntityOwnershipRegistry ownershipRegistry = gateway.entityOwnershipRegistry();
        for (ServerLevel level : server.getAllLevels()) {
            clearMissingRoomEntities(gateway, ownershipRegistry, level);
        }

        ServerLevel level = server.overworld();
        long gameTime = level.getGameTime();
        gateway.listRoomTickPorts().forEach(port -> port.tick(new ModeRoomTickContext(
                server,
                level,
                port.roomId(),
                gameTime)));

        clearRoomStateOnLifecycleTransitions(gateway);
        if (gameTime % STATE_SYNC_INTERVAL_TICKS == 0L) {
            syncRuntimeStateToPlayers(server, gateway);
            syncObjectStateToPlayers(server, gateway);
        }
    }

    private static void clearMissingRoomEntities(
            FpsMatchGateway gateway,
            ModeEntityOwnershipRegistry ownershipRegistry,
            ServerLevel level
    ) {
        for (ModeEntityOwnershipRegistry.Entry entry : ownershipRegistry.missingEntities(level)) {
            ModeEntityLifecyclePort lifecyclePort = gateway.findRoomEntityLifecyclePort(entry.roomId()).orElse(null);
            boolean handled = lifecyclePort != null
                    && lifecyclePort.onRoomEntityMissing(entry, new EntityLifecycleContext(entry.roomId()));
            if (!handled) {
                if (BuiltInGameModes.isZombies(entry.roomId().gameType())) {
                    ZombiesActiveMobCounter.instance().unregister(entry.roomId(), entry.entityId());
                }
                ownershipRegistry.unregister(entry.entityId());
            }
        }
    }

    private static void clearRoomStateOnLifecycleTransitions(FpsMatchGateway gateway) {
        Set<String> currentRooms = new HashSet<>();
        for (ModeRoomSummaryPort summaryPort : gateway.listRoomSummaryPorts()) {
            RoomId roomId = summaryPort.roomId();
            if (roomId == null) {
                continue;
            }
            String roomKey = roomId.encode();
            currentRooms.add(roomKey);
            String currentState = normalizeState(summaryPort.lifecycleStateKey());
            String previousState = LAST_LIFECYCLE_STATE_BY_ROOM.put(roomKey, currentState);
            if (previousState == null || previousState.equals(currentState)) {
                continue;
            }
            gateway.findRoomStatePort(roomId)
                    .filter(port -> port.shouldClearRoomStateOnLifecycleStateChange(previousState, currentState))
                    .ifPresent(ModePlayerRuntimeStatePort::clearRoomState);
        }
        LAST_LIFECYCLE_STATE_BY_ROOM.keySet().removeIf(roomKey -> !currentRooms.contains(roomKey));
    }

    private static void syncRuntimeStateToPlayers(MinecraftServer server, FpsMatchGateway gateway) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            gateway.findPlayerRuntimeStatePort(player).ifPresent(port -> {
                ModeRuntimeStateSnapshot snapshot = port.runtimeStateSnapshot(player);
                if (snapshot != null) {
                    ModNetworkChannel.sendToPlayer(new ModeRuntimeStatePacket(snapshot), player);
                }
            });
        }
    }

    private static void syncObjectStateToPlayers(MinecraftServer server, FpsMatchGateway gateway) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            gateway.findPlayerInteractableObjectPort(player).ifPresent(port -> {
                var states = port.objectStatesForClient(player);
                long revision = states == null ? 0L : states.stream()
                        .filter(state -> state != null)
                        .mapToLong(state -> state.revision())
                        .max()
                        .orElse(0L);
                ModNetworkChannel.sendToPlayer(new ModeObjectStateSyncPacket(
                        port.roomId().encode(),
                        states,
                        revision), player);
            });
        }
    }

    private static String normalizeState(String stateKey) {
        return stateKey == null ? "" : stateKey.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
