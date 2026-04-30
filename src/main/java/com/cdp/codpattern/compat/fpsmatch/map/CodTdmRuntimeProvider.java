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

public final class CodTdmRuntimeProvider implements GameModeRuntimeProvider {
    public static final CodTdmRuntimeProvider INSTANCE = new CodTdmRuntimeProvider();

    private CodTdmRuntimeProvider() {
    }

    @Override
    public String gameType() {
        return BuiltInGameModes.FRONTLINE;
    }

    @Override
    public BaseMap createMap(ServerLevel level, String mapName, AreaData areaData) {
        return new CodTdmMap(level, mapName, areaData);
    }

    @Override
    public Optional<ModeRoomHandle> roomHandle(BaseMap map) {
        if (map instanceof CodTdmMap tdmMap && !(map instanceof CodTacticalTdmMap)) {
            return Optional.of(tdmMap.roomHandle());
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
