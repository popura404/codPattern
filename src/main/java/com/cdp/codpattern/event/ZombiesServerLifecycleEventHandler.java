package com.cdp.codpattern.event;

import com.cdp.codpattern.CodPatternConstants;
import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.service.ZombiesCrashRecoveryService;
import com.cdp.codpattern.app.zombies.service.ZombiesLoginRecoveryContributor;
import com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry;
import com.cdp.codpattern.compat.fpsmatch.map.ZombiesMap;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = CodPatternConstants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ZombiesServerLifecycleEventHandler {
    private ZombiesServerLifecycleEventHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        ZombiesCrashRecoveryService.instance().recoverServerStartup(event.getServer());
        for (BaseMap map : FpsMatchMapRegistry.listMaps(BuiltInGameModes.ZOMBIES)) {
            if (map instanceof ZombiesMap zombiesMap) {
                zombiesMap.clearBarrierBlockResidue();
            }
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ZombiesLoginRecoveryContributor.recover(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStopping(ServerStoppingEvent event) {
        ZombiesCrashRecoveryService.instance().cleanupServerStopping(event.getServer(), shutdownRooms());
    }

    private static List<ZombiesCrashRecoveryService.ShutdownRoom> shutdownRooms() {
        if (!FPSMCore.initialized()) {
            return List.of();
        }
        List<ZombiesCrashRecoveryService.ShutdownRoom> rooms = new ArrayList<>();
        for (BaseMap map : FpsMatchMapRegistry.listMaps(BuiltInGameModes.ZOMBIES)) {
            if (map instanceof ZombiesMap zombiesMap) {
                rooms.add(new ZombiesShutdownRoom(zombiesMap));
            }
        }
        return rooms;
    }

    private record ZombiesShutdownRoom(ZombiesMap map) implements ZombiesCrashRecoveryService.ShutdownRoom {
        @Override
        public RoomId roomId() {
            return RoomId.of(BuiltInGameModes.ZOMBIES, map.getMapName());
        }

        @Override
        public boolean running() {
            return map.isStart;
        }

        @Override
        public void cleanupForServerStopping() {
            map.resetGame();
        }
    }
}
