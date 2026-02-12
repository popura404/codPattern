package com.cdp.codpattern.compat.fpsmatch;

import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Optional;
import java.util.UUID;

public final class FpsMatchCoreGateway implements FpsMatchGateway {
    @Override
    public boolean isInMatch(UUID playerId) {
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            return false;
        }
        ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerId);
        return player != null && findPlayerTdmMap(player).isPresent();
    }

    @Override
    public Optional<CodTdmMap> findMapByName(String mapName) {
        return FPSMCore.getInstance().getMapByName(mapName)
                .filter(map -> map instanceof CodTdmMap)
                .map(map -> (CodTdmMap) map);
    }

    @Override
    public Optional<CodTdmMap> findPlayerTdmMap(ServerPlayer player) {
        return FPSMCore.getInstance().getMapByPlayer(player)
                .filter(map -> map instanceof CodTdmMap)
                .map(map -> (CodTdmMap) map);
    }

    @Override
    public void leaveCurrentMapIfDifferent(ServerPlayer player, CodTdmMap targetMap) {
        FPSMCore.getInstance().getMapByPlayer(player).ifPresent(currentMap -> {
            if (currentMap == targetMap) {
                return;
            }
            if (currentMap instanceof CodTdmMap codMap) {
                codMap.leaveRoom(player);
            } else {
                currentMap.leave(player);
            }
        });
    }

    @Override
    public Optional<String> leaveCurrentMapIncludingSpectator(ServerPlayer player) {
        Optional<BaseMap> mapOptional = FPSMCore.getInstance().getMapByPlayerWithSpec(player);
        mapOptional.ifPresent(map -> {
            if (map instanceof CodTdmMap tdmMap) {
                tdmMap.leaveRoom(player);
            } else {
                map.leave(player);
            }
        });
        return mapOptional.map(map -> map.mapName);
    }
}
