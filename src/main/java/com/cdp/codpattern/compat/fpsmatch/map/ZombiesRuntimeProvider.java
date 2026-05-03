package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.GameModeRuntimeProvider;
import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.stream.Stream;

public final class ZombiesRuntimeProvider implements GameModeRuntimeProvider {
    public static final ZombiesRuntimeProvider INSTANCE = new ZombiesRuntimeProvider();

    private ZombiesRuntimeProvider() {
    }

    @Override
    public String gameType() {
        return BuiltInGameModes.ZOMBIES;
    }

    @Override
    public BaseMap createMap(ServerLevel level, String mapName, AreaData areaData) {
        return new ZombiesMap(level, mapName, areaData);
    }

    @Override
    public Optional<ModeRoomHandle> roomHandle(BaseMap map) {
        if (map instanceof ZombiesMap zombiesMap) {
            return Optional.of(zombiesMap.roomHandle());
        }
        return Optional.empty();
    }

    @Override
    public Stream<ModeRoomHandle> listRoomHandles() {
        if (!FPSMCore.initialized()) {
            return Stream.empty();
        }
        return FpsMatchMapRegistry.listMaps(gameType())
                .stream()
                .flatMap(map -> roomHandle(map).stream());
    }
}
