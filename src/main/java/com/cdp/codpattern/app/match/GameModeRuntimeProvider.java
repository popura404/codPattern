package com.cdp.codpattern.app.match;

import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.stream.Stream;

public interface GameModeRuntimeProvider {
    String gameType();

    BaseMap createMap(ServerLevel level, String mapName, AreaData areaData);

    Optional<ModeRoomHandle> roomHandle(BaseMap map);

    Stream<ModeRoomHandle> listRoomHandles();
}
