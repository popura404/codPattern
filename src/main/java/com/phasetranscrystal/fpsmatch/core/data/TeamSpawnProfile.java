package com.phasetranscrystal.fpsmatch.core.data;

import java.util.ArrayList;
import java.util.List;

public record TeamSpawnProfile(
        List<SpawnPointData> initialSpawnPoints,
        List<SpawnPointData> dynamicSpawnCandidates
) {
    public TeamSpawnProfile {
        initialSpawnPoints = normalize(initialSpawnPoints, SpawnPointKind.INITIAL);
        dynamicSpawnCandidates = normalize(dynamicSpawnCandidates, SpawnPointKind.DYNAMIC_CANDIDATE);
    }

    public static TeamSpawnProfile empty() {
        return new TeamSpawnProfile(List.of(), List.of());
    }

    public static TeamSpawnProfile fromLegacy(List<SpawnPointData> spawnPoints) {
        return new TeamSpawnProfile(spawnPoints, List.of());
    }

    public List<SpawnPointData> points(SpawnPointKind kind) {
        return kind == SpawnPointKind.DYNAMIC_CANDIDATE
                ? dynamicSpawnCandidates
                : initialSpawnPoints;
    }

    private static List<SpawnPointData> normalize(List<SpawnPointData> points, SpawnPointKind kind) {
        List<SpawnPointData> normalized = new ArrayList<>();
        if (points == null) {
            return normalized;
        }
        for (SpawnPointData point : points) {
            if (point == null) {
                continue;
            }
            normalized.add(point.withKind(kind));
        }
        return List.copyOf(normalized);
    }
}
