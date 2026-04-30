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

public final class CodTacticalTdmRuntimeProvider implements GameModeRuntimeProvider {
    public static final CodTacticalTdmRuntimeProvider INSTANCE = new CodTacticalTdmRuntimeProvider();

    private CodTacticalTdmRuntimeProvider() {
    }

    @Override
    public String gameType() {
        return BuiltInGameModes.TEAM_DEATHMATCH;
    }

    @Override
    public BaseMap createMap(ServerLevel level, String mapName, AreaData areaData) {
        return new CodTacticalTdmMap(level, mapName, areaData);
    }

    @Override
    public Optional<ModeRoomHandle> roomHandle(BaseMap map) {
        if (map instanceof CodTacticalTdmMap tacticalMap) {
            return Optional.of(tacticalMap.roomHandle());
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
