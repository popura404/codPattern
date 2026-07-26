package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.extension.ModePlayerLoginContributor;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.compat.fpsmatch.map.FpsMatchMapRegistry;
import com.cdp.codpattern.compat.fpsmatch.map.ZombiesMap;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** Addon-owned adapter for the existing Zombies reconnect and post-game recovery flow. */
public final class ZombiesLoginRecoveryContributor implements ModePlayerLoginContributor {
    @Override
    public String id() {
        return "zombies-login-recovery";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public LoginDisposition onPlayerLogin(ServerPlayer player) {
        recover(player);
        return LoginDisposition.CONTINUE;
    }

    public static ZombiesReconnectRecoveryService.LoginRecoveryResult recover(ServerPlayer player) {
        return ZombiesReconnectRecoveryService.instance().recoverPlayer(player, new ZombiesLoginRecoveryResolver());
    }

    private static final class ZombiesLoginRecoveryResolver
            implements ZombiesReconnectRecoveryService.RecoveryResolver {
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
                    .flatMap(ZombiesMap::matchEndTeleportPoint)
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

        @Override
        public boolean restoreActiveRoomPlayer(ServerPlayer player, RoomId roomId) {
            return findZombiesMap(roomId)
                    .map(map -> map.restoreActiveRoundReconnect(player))
                    .orElse(false);
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
