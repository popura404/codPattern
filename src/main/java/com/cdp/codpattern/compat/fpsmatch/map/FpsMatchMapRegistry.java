package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;

import java.util.List;
import java.util.Optional;

public final class FpsMatchMapRegistry {
    private FpsMatchMapRegistry() {
    }

    public static Optional<BaseMap> findByName(String gameType, String mapName) {
        if (mapName == null || mapName.isBlank()) {
            return Optional.empty();
        }
        return FPSMCore.getInstance().getMapByTypeWithName(canonicalGameType(gameType), mapName);
    }

    public static List<BaseMap> listMaps(String gameType) {
        return FPSMCore.getInstance()
                .getAllMaps()
                .getOrDefault(canonicalGameType(gameType), List.of());
    }

    public static void register(String gameType, BaseMap map) {
        if (map == null) {
            return;
        }
        FPSMCore.getInstance().registerMap(canonicalGameType(gameType), map);
    }

    private static String canonicalGameType(String gameType) {
        return GameModeRegistry.canonicalize(gameType);
    }
}
