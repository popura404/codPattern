package com.cdp.codpattern.app.match.persistence;

import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;

import java.util.Objects;
import java.util.Optional;

public record CommonModeMapData(
        int schemaVersion,
        String gameType,
        String mapName,
        String levelName,
        AreaData areaData,
        Optional<SpawnPointData> fallbackExitPoint
) {
    public CommonModeMapData {
        Objects.requireNonNull(gameType, "gameType");
        Objects.requireNonNull(mapName, "mapName");
        Objects.requireNonNull(levelName, "levelName");
        Objects.requireNonNull(areaData, "areaData");
        fallbackExitPoint = fallbackExitPoint == null ? Optional.empty() : fallbackExitPoint;
    }
}
