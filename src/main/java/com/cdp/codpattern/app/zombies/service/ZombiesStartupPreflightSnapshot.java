package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;
import com.cdp.codpattern.app.zombies.validation.ZombiesMapValidationReport;
import com.cdp.codpattern.app.zombies.validation.ZombiesValidationIssue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ZombiesStartupPreflightSnapshot(
        ZombiesMapSnapshot mapSnapshot,
        ZombiesMapValidationReport mapReport,
        ZombiesWaveConfigRepository.LoadResult waveLoadResult,
        int maxWave,
        List<Issue> issues
) {
    public ZombiesStartupPreflightSnapshot {
        Objects.requireNonNull(mapSnapshot, "mapSnapshot");
        Objects.requireNonNull(mapReport, "mapReport");
        Objects.requireNonNull(waveLoadResult, "waveLoadResult");
        maxWave = Math.max(0, maxWave);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean valid() {
        return mapValid() && wavesValid();
    }

    public boolean mapValid() {
        return mapReport.valid();
    }

    public boolean wavesValid() {
        return waveLoadResult.isValid() && maxWave > 0;
    }

    public boolean hasErrors() {
        return firstError().isPresent();
    }

    public Optional<Issue> firstError() {
        return issues.stream()
                .filter(Issue::error)
                .findFirst();
    }

    public List<ZombiesValidationIssue> mapIssues() {
        return mapReport.issues();
    }

    public List<ZombiesWaveValidator.ValidationIssue> waveIssues() {
        return waveLoadResult.getIssues();
    }

    public record Issue(
            String source,
            boolean error,
            ZombiesErrorCode code,
            String subject,
            String message
    ) {
        public static final String SOURCE_MAP = "map";
        public static final String SOURCE_WAVE = "wave";

        public Issue {
            source = Objects.requireNonNullElse(source, "").trim();
            code = code == null ? ZombiesErrorCode.OK : code;
            subject = Objects.requireNonNullElse(subject, "").trim();
            message = Objects.requireNonNullElse(message, "");
        }

        public static Issue fromMapIssue(ZombiesValidationIssue issue) {
            Objects.requireNonNull(issue, "issue");
            return new Issue(SOURCE_MAP, issue.isError(), issue.code(), issue.subject(), issue.message());
        }

        public static Issue fromWaveIssue(ZombiesWaveValidator.ValidationIssue issue) {
            Objects.requireNonNull(issue, "issue");
            return new Issue(
                    SOURCE_WAVE,
                    true,
                    ZombiesErrorCode.of(issue.getCode()),
                    issue.getPath() == null ? "waves" : issue.getPath().toString(),
                    issue.getMessage());
        }

        public static Issue waveError(ZombiesErrorCode code, String subject, String message) {
            return new Issue(SOURCE_WAVE, true, code, subject, message);
        }
    }
}
