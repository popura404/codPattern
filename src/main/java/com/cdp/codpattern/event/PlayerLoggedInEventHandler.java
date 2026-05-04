package com.cdp.codpattern.event;

import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.service.ZombiesPlayerRuntimeMarkerService;
import com.cdp.codpattern.app.zombies.service.ZombiesPostGameTeleportService;
import com.cdp.codpattern.app.zombies.service.ZombiesReconnectRecoveryService;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmDeferredLeaveRegistry;
import com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry;
import com.cdp.codpattern.compat.fpsmatch.map.ZombiesMap;
import com.cdp.codpattern.network.SyncBackpackConfigPacket;
import com.cdp.codpattern.network.SyncWeaponFilterPacket;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;
import java.util.Optional;


@Mod.EventBusSubscriber(modid = "codpattern")
public class PlayerLoggedInEventHandler {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide){
            return;
        }

        Player player = event.getEntity();
        MinecraftServer server = player.getServer();

        Path backpackPath = ConfigPath.SERVERBACKPACK.getPath(server);
        Path filterPath = ConfigPath.SERVER_FILTER.getPath(server);

        var fliterconfig = WeaponFilterConfigRepository.loadOrCreate(filterPath);
        var playerBackpackData = BackpackConfigRepository.loadOrCreatePlayer(player.getStringUUID(), backpackPath);

        // 同步到客户端
        ModNetworkChannel.sendToPlayer(new SyncWeaponFilterPacket(fliterconfig), (ServerPlayer) player);
        ModNetworkChannel.sendToPlayer(new SyncBackpackConfigPacket(playerBackpackData), (ServerPlayer) player);

        ServerPlayer serverPlayer = (ServerPlayer) player;
        recoverZombiesLogin(serverPlayer);

        if (CodTdmDeferredLeaveRegistry.applyIfPresent(serverPlayer)) {
            return;
        }
        FPSMCore.handlePlayerLogin(serverPlayer);
    }

    static ZombiesReconnectRecoveryService.LoginRecoveryResult recoverZombiesLogin(ServerPlayer player) {
        return ZombiesReconnectRecoveryService.instance().recoverPlayer(player, new ZombiesLoginRecoveryResolver());
    }

    private static final class ZombiesLoginRecoveryResolver implements ZombiesReconnectRecoveryService.RecoveryResolver {
        private final ZombiesPlayerRuntimeMarkerService markerService = ZombiesPlayerRuntimeMarkerService.instance();

        @Override
        public boolean isRoomActive(RoomId roomId) {
            return findZombiesMap(roomId)
                    .map(map -> map.isStart)
                    .orElse(false);
        }

        @Override
        public Optional<ZombiesPostGameTeleportService.TeleportTarget> endTeleport(RoomId roomId) {
            return findZombiesMap(roomId)
                    .flatMap(map -> map.matchEndTeleportPoint())
                    .flatMap(markerService::targetFromSpawnPoint);
        }

        @Override
        public Optional<RoomId> inactiveZombiesRoomContaining(ServerPlayer player) {
            if (player == null || !FPSMCore.initialized()) {
                return Optional.empty();
            }
            for (BaseMap map : FpsMatchMapRegistry.listMaps(BuiltInGameModes.ZOMBIES)) {
                if (!(map instanceof ZombiesMap zombiesMap) || zombiesMap.isStart) {
                    continue;
                }
                if (!zombiesMap.getServerLevel().dimension().equals(player.serverLevel().dimension())) {
                    continue;
                }
                if (zombiesMap.getMapArea().isBlockPosInArea(player.blockPosition())) {
                    return Optional.of(RoomId.of(BuiltInGameModes.ZOMBIES, zombiesMap.getMapName()));
                }
            }
            return Optional.empty();
        }

        @Override
        public void clearZombiesTemporaryState(ServerPlayer player, RoomId roomId, boolean clearInventory) {
            Optional<ZombiesMap> map = findZombiesMap(roomId);
            if (map.isPresent()) {
                map.get().clearRecoveredPlayerState(player, clearInventory);
                return;
            }
            markerService.clearTemporaryPlayerState(player, clearInventory);
        }

        private static Optional<ZombiesMap> findZombiesMap(RoomId roomId) {
            if (roomId == null || !BuiltInGameModes.isZombies(roomId.gameType()) || !FPSMCore.initialized()) {
                return Optional.empty();
            }
            return FpsMatchMapRegistry.findByName(BuiltInGameModes.ZOMBIES, roomId.mapName())
                    .filter(ZombiesMap.class::isInstance)
                    .map(ZombiesMap.class::cast);
        }
    }
}
