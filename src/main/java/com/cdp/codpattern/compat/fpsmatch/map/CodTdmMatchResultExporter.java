package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.service.TeamPlayerSnapshotService;
import com.cdp.codpattern.config.path.ConfigPath;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Function;

final class CodTdmMatchResultExporter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final CodTdmCoordinatorComposition.MapPort mapPort;
    private final CodTdmMatchRuntimeState matchState;
    private final CodTdmPlayerRuntimeState playerState;
    private final Supplier<String> mapNameSupplier;
    private final Function<MinecraftServer, Path> exportDirResolver;

    CodTdmMatchResultExporter(
            CodTdmCoordinatorComposition.MapPort mapPort,
            CodTdmMatchRuntimeState matchState,
            CodTdmPlayerRuntimeState playerState,
            Supplier<String> mapNameSupplier,
            Function<MinecraftServer, Path> exportDirResolver
    ) {
        this.mapPort = mapPort;
        this.matchState = matchState;
        this.playerState = playerState;
        this.mapNameSupplier = mapNameSupplier;
        this.exportDirResolver = exportDirResolver;
    }

    void exportOnMatchEnded() {
        if (matchState.isResultExported()) {
            return;
        }

        ServerLevel serverLevel = mapPort.serverLevel();
        if (serverLevel == null || serverLevel.getServer() == null) {
            return;
        }

        long endedAt = System.currentTimeMillis();
        long startedAt = matchState.playingStartEpochMillis();
        if (startedAt <= 0L || startedAt > endedAt) {
            startedAt = endedAt;
        }

        String mapName = mapNameSupplier.get() == null || mapNameSupplier.get().isBlank()
                ? "unknown_map"
                : mapNameSupplier.get();
        Map<String, Integer> teamScores = new HashMap<>(matchState.teamScoresSnapshot());

        List<PlayerMatchStats> players = new ArrayList<>();
        Map<UUID, Integer> kills = playerState.playerKills();
        Map<UUID, Integer> deaths = playerState.playerDeaths();
        for (TeamPlayerSnapshotService.TeamRoster teamRoster : mapPort.teamRosters()) {
            for (UUID playerId : teamRoster.playerIds()) {
                String playerName = playerId.toString();
                if (serverLevel.getPlayerByUUID(playerId) instanceof ServerPlayer player) {
                    playerName = player.getGameProfile().getName();
                }
                int playerKills = kills.getOrDefault(playerId, 0);
                int playerDeaths = deaths.getOrDefault(playerId, 0);
                double kd = playerDeaths <= 0
                        ? playerKills
                        : roundTo3((double) playerKills / playerDeaths);

                players.add(new PlayerMatchStats(
                        playerId.toString(),
                        playerName,
                        teamRoster.name(),
                        playerKills,
                        playerDeaths,
                        kd
                ));
            }
        }

        players.sort(Comparator
                .comparing(PlayerMatchStats::selectedTeam, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PlayerMatchStats::kills, Comparator.reverseOrder())
                .thenComparing(PlayerMatchStats::deaths)
                .thenComparing(PlayerMatchStats::playerName, String.CASE_INSENSITIVE_ORDER));

        MatchResultFile record = new MatchResultFile(
                mapName,
                startedAt,
                Instant.ofEpochMilli(startedAt).toString(),
                endedAt,
                Instant.ofEpochMilli(endedAt).toString(),
                Math.max(0L, (endedAt - startedAt) / 1000L),
                resolveWinnerTeam(teamScores),
                teamScores,
                players
        );

        try {
            Path exportDir = exportDirResolver.apply(serverLevel.getServer());
            Files.createDirectories(exportDir);

            String fileName = endedAt
                    + "-"
                    + sanitizeFilePart(mapName)
                    + "-"
                    + UUID.randomUUID().toString().substring(0, 8)
                    + ".json";
            Path output = exportDir.resolve(fileName);
            Files.writeString(
                    output,
                    GSON.toJson(record),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW
            );

            matchState.markResultExported();
            LOGGER.info("Exported TDM match result: map={} file={} players={}",
                    mapName,
                    output,
                    players.size());
        } catch (Exception e) {
            LOGGER.warn("Failed to export TDM match result for map={}", mapName, e);
        }
    }

    private static String resolveWinnerTeam(Map<String, Integer> teamScores) {
        if (teamScores == null || teamScores.isEmpty()) {
            return "";
        }
        int bestScore = Integer.MIN_VALUE;
        String winner = "";
        boolean tie = false;
        for (Map.Entry<String, Integer> entry : teamScores.entrySet()) {
            int score = entry.getValue() == null ? 0 : entry.getValue();
            if (score > bestScore) {
                bestScore = score;
                winner = entry.getKey();
                tie = false;
            } else if (score == bestScore) {
                tie = true;
            }
        }
        return tie ? "TIE" : winner;
    }

    private static double roundTo3(double value) {
        return Math.round(value * 1000.0d) / 1000.0d;
    }

    private static String sanitizeFilePart(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown_map";
        }
        String sanitized = raw.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isEmpty() ? "unknown_map" : sanitized;
    }

    private record PlayerMatchStats(
            String playerUuid,
            String playerName,
            String selectedTeam,
            int kills,
            int deaths,
            double kd
    ) {
    }

    private record MatchResultFile(
            String mapName,
            long startedAtEpochMillis,
            String startedAtIso,
            long endedAtEpochMillis,
            String endedAtIso,
            long durationSeconds,
            String winnerTeam,
            Map<String, Integer> teamScores,
            List<PlayerMatchStats> players
    ) {
    }
}
