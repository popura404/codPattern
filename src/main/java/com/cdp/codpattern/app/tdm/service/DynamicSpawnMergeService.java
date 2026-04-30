package com.cdp.codpattern.app.tdm.service;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DynamicSpawnMergeService {
    private DynamicSpawnMergeService() {
    }

    public static MergeResult mergeDynamicSpawnCandidates(List<BaseTeam> teams) {
        com.cdp.codpattern.app.match.service.DynamicSpawnMergeService.MergeResult result =
                com.cdp.codpattern.app.match.service.DynamicSpawnMergeService.mergeDynamicSpawnCandidates(teams);
        return new MergeResult(result.dynamicPointsByTeam(), result.uniqueDynamicPointCount());
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
