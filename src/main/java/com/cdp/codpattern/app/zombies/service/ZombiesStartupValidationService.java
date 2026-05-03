package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.ZombiesMapObjects;
import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;
import com.cdp.codpattern.app.zombies.validation.ZombiesMapValidationReport;
import com.cdp.codpattern.app.zombies.validation.ZombiesMapValidator;
import com.cdp.codpattern.config.zombies.ZombiesRulesRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class ZombiesStartupValidationService {
    private final ZombiesMapValidator mapValidator;
    private final Supplier<ZombiesWaveConfigRepository> waveRepositorySupplier;

    public ZombiesStartupValidationService(Path wavesDirectory) {
        this(new ZombiesMapValidator(), repositorySupplier(wavesDirectory));
    }

    public ZombiesStartupValidationService(ZombiesMapValidator mapValidator, Path wavesDirectory) {
        this(mapValidator, repositorySupplier(wavesDirectory));
    }

    public ZombiesStartupValidationService(ZombiesWaveConfigRepository waveRepository) {
        this(new ZombiesMapValidator(), waveRepository);
    }

    public ZombiesStartupValidationService(
            ZombiesMapValidator mapValidator,
            ZombiesWaveConfigRepository waveRepository
    ) {
        this(mapValidator, () -> Objects.requireNonNull(waveRepository, "waveRepository"));
    }

    public ZombiesStartupValidationService(
            ZombiesMapValidator mapValidator,
            Supplier<ZombiesWaveConfigRepository> waveRepositorySupplier
    ) {
        this.mapValidator = mapValidator == null ? new ZombiesMapValidator() : mapValidator;
        this.waveRepositorySupplier = Objects.requireNonNull(waveRepositorySupplier, "waveRepositorySupplier");
    }

    public ZombiesServiceResult<ZombiesStartupPreflightSnapshot> validate(
            ZombiesMapSnapshot mapSnapshot
    ) {
        return validatePreflight(mapSnapshot);
    }

    public ZombiesServiceResult<ZombiesStartupPreflightSnapshot> preflight(
            ZombiesMapSnapshot mapSnapshot
    ) {
        return validatePreflight(mapSnapshot);
    }

    public ZombiesServiceResult<ZombiesStartupPreflightSnapshot> validatePreflight(
            ZombiesMapSnapshot mapSnapshot
    ) {
        Objects.requireNonNull(mapSnapshot, "mapSnapshot");
        ZombiesMapValidationReport mapReport = mapValidator.validate(mapSnapshot);
        ZombiesWaveConfigRepository.LoadResult waveLoadResult = waveRepositorySupplier.get().load();
        return resultFor(mapSnapshot, mapReport, waveLoadResult);
    }

    public ZombiesServiceResult<ZombiesStartupPreflightSnapshot> validate(
            RoomId roomId,
            String mapName,
            boolean hasEndTeleportPoint,
            ZombiesMapObjects objects
    ) {
        return validatePreflight(roomId, mapName, hasEndTeleportPoint, objects);
    }

    public ZombiesServiceResult<ZombiesStartupPreflightSnapshot> preflight(
            RoomId roomId,
            String mapName,
            boolean hasEndTeleportPoint,
            ZombiesMapObjects objects
    ) {
        return validatePreflight(roomId, mapName, hasEndTeleportPoint, objects);
    }

    public ZombiesServiceResult<ZombiesStartupPreflightSnapshot> validatePreflight(
            RoomId roomId,
            String mapName,
            boolean hasEndTeleportPoint,
            ZombiesMapObjects objects
    ) {
        return validatePreflight(ZombiesMapSnapshot.fromMapObjects(roomId, mapName, hasEndTeleportPoint, objects));
    }

    public ZombiesMapValidator mapValidator() {
        return mapValidator;
    }

    private ZombiesServiceResult<ZombiesStartupPreflightSnapshot> resultFor(
            ZombiesMapSnapshot mapSnapshot,
            ZombiesMapValidationReport mapReport,
            ZombiesWaveConfigRepository.LoadResult waveLoadResult
    ) {
        int maxWave = waveLoadResult.getMaxWave();
        ZombiesStartupPreflightSnapshot snapshot = new ZombiesStartupPreflightSnapshot(
                mapSnapshot,
                mapReport,
                waveLoadResult,
                maxWave,
                collectIssues(mapReport, waveLoadResult, maxWave));
        if (snapshot.valid()) {
            return ZombiesServiceResult.success(snapshot);
        }
        return new ZombiesServiceResult<>(
                false,
                ZombiesErrorCode.STARTUP_PREFLIGHT_FAILED,
                failureParams(snapshot),
                Optional.of(snapshot),
                failureLog(snapshot));
    }

    private static List<ZombiesStartupPreflightSnapshot.Issue> collectIssues(
            ZombiesMapValidationReport mapReport,
            ZombiesWaveConfigRepository.LoadResult waveLoadResult,
            int maxWave
    ) {
        List<ZombiesStartupPreflightSnapshot.Issue> issues = new ArrayList<>();
        mapReport.issues().stream()
                .map(ZombiesStartupPreflightSnapshot.Issue::fromMapIssue)
                .forEach(issues::add);
        waveLoadResult.getIssues().stream()
                .map(ZombiesStartupPreflightSnapshot.Issue::fromWaveIssue)
                .forEach(issues::add);
        if (maxWave <= 0 && !hasWaveIssue(waveLoadResult, ZombiesWaveValidator.NO_VALID_WAVE)) {
            issues.add(ZombiesStartupPreflightSnapshot.Issue.waveError(
                    ZombiesErrorCode.RULES_NO_VALID_WAVE,
                    "waves",
                    "Zombies startup requires at least one valid wave."));
        }
        return issues;
    }

    private static boolean hasWaveIssue(
            ZombiesWaveConfigRepository.LoadResult waveLoadResult,
            String code
    ) {
        return waveLoadResult.getIssues().stream()
                .anyMatch(issue -> Objects.equals(issue.getCode(), code));
    }

    private static Map<String, ModePlayerValue> failureParams(ZombiesStartupPreflightSnapshot snapshot) {
        Map<String, ModePlayerValue> params = new LinkedHashMap<>();
        params.put("roomId", ModePlayerValue.ofString(snapshot.mapSnapshot().roomId().encode()));
        params.put("mapName", ModePlayerValue.ofString(snapshot.mapSnapshot().mapName()));
        params.put("maxWave", ModePlayerValue.ofInt(snapshot.maxWave()));
        params.put("mapIssueCount", ModePlayerValue.ofInt(snapshot.mapReport().errors().size()));
        params.put("waveIssueCount", ModePlayerValue.ofInt(snapshot.waveIssues().size()));
        snapshot.firstError().ifPresent(issue -> {
            params.put("firstIssueSource", ModePlayerValue.ofString(issue.source()));
            params.put("firstIssueCode", ModePlayerValue.ofString(issue.code().key()));
        });
        return params;
    }

    private static String failureLog(ZombiesStartupPreflightSnapshot snapshot) {
        String firstIssue = snapshot.firstError()
                .map(issue -> issue.code().key() + "@" + issue.subject())
                .orElse("unknown");
        return "Zombies startup preflight failed for " + snapshot.mapSnapshot().roomId().encode()
                + " (maxWave=" + snapshot.maxWave()
                + ", mapErrors=" + snapshot.mapReport().errors().size()
                + ", waveIssues=" + snapshot.waveIssues().size()
                + ", firstIssue=" + firstIssue + ")";
    }

    private static Supplier<ZombiesWaveConfigRepository> repositorySupplier(Path wavesDirectory) {
        return () -> new ZombiesWaveConfigRepository(
                wavesDirectory,
                ZombiesRulesRepository.getConfig().getDefaults(),
                new ZombiesWaveValidator());
    }
}
