package com.cdp.codpattern.app.tdm.service;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class DynamicSpawnMergeService {
    private DynamicSpawnMergeService() {
    }

    public static MergeResult mergeDynamicSpawnCandidates(List<BaseTeam> teams) {
        List<BaseTeam> normalizedTeams = normalizeTeams(teams);
        if (normalizedTeams.size() < 2) {
            return new MergeResult(Map.of(), 0);
        }

        List<SpawnPointData> mergedPoints = collectMergedDynamicPoints(normalizedTeams);
        Map<String, List<SpawnPointData>> mergedPointsByTeam = new LinkedHashMap<>();
        for (BaseTeam team : normalizedTeams) {
            mergedPointsByTeam.put(team.name, mergedPoints);
        }
        return new MergeResult(mergedPointsByTeam, mergedPoints.size());
    }

    private static List<BaseTeam> normalizeTeams(List<BaseTeam> teams) {
        List<BaseTeam> normalizedTeams = new ArrayList<>();
        if (teams == null) {
            return normalizedTeams;
        }
        for (BaseTeam team : teams) {
            if (team != null) {
                normalizedTeams.add(team);
            }
        }
        return normalizedTeams;
    }

    private static List<SpawnPointData> collectMergedDynamicPoints(List<BaseTeam> teams) {
        LinkedHashSet<SpawnPointData> uniquePoints = new LinkedHashSet<>();
        for (BaseTeam team : teams) {
            uniquePoints.addAll(team.getSpawnPointsData(SpawnPointKind.DYNAMIC_CANDIDATE));
        }
        return uniquePoints.stream()
                .map(point -> point.withKind(SpawnPointKind.DYNAMIC_CANDIDATE))
                .toList();
    }

    public record MergeResult(Map<String, List<SpawnPointData>> dynamicPointsByTeam, int uniqueDynamicPointCount) {
        public MergeResult {
            Map<String, List<SpawnPointData>> copied = new LinkedHashMap<>();
            if (dynamicPointsByTeam != null) {
                dynamicPointsByTeam.forEach((teamName, points) -> copied.put(
                        teamName,
                        List.copyOf(points == null ? List.of() : points)
                ));
            }
            dynamicPointsByTeam = Map.copyOf(copied);
            uniqueDynamicPointCount = Math.max(0, uniqueDynamicPointCount);
        }

        public int countForTeam(String teamName) {
            return dynamicPointsByTeam.getOrDefault(teamName, List.of()).size();
        }
    }
}
