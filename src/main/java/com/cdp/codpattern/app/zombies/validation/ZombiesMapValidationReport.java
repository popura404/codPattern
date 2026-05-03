package com.cdp.codpattern.app.zombies.validation;

import com.cdp.codpattern.app.match.model.RoomId;

import java.util.List;
import java.util.Objects;

public record ZombiesMapValidationReport(
        RoomId roomId,
        String profileKey,
        List<ZombiesValidationIssue> issues
) {
    public ZombiesMapValidationReport {
        Objects.requireNonNull(roomId, "roomId");
        profileKey = Objects.requireNonNullElse(profileKey, "").trim();
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static ZombiesMapValidationReport ok(RoomId roomId, String profileKey) {
        return new ZombiesMapValidationReport(roomId, profileKey, List.of());
    }

    public boolean valid() {
        return errors().isEmpty();
    }

    public boolean hasErrors() {
        return !errors().isEmpty();
    }

    public List<ZombiesValidationIssue> errors() {
        return issues.stream()
                .filter(ZombiesValidationIssue::isError)
                .toList();
    }

    public List<ZombiesValidationIssue> warnings() {
        return issues.stream()
                .filter(issue -> !issue.isError())
                .toList();
    }
}
