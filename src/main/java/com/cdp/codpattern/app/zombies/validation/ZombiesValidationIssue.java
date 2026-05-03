package com.cdp.codpattern.app.zombies.validation;

import com.cdp.codpattern.app.zombies.service.ZombiesErrorCode;

import java.util.Objects;

/**
 * Top-level validation issue DTO used by MVP validators.
 * Bridges to the existing contributor issue record without changing that contract.
 */
public record ZombiesValidationIssue(
        ZombiesMapValidationContributor.Severity severity,
        ZombiesErrorCode code,
        String subject,
        String message
) {
    public ZombiesValidationIssue {
        severity = severity == null ? ZombiesMapValidationContributor.Severity.ERROR : severity;
        code = code == null ? ZombiesErrorCode.OK : code;
        subject = Objects.requireNonNullElse(subject, "").trim();
        message = Objects.requireNonNullElse(message, "");
    }

    public static ZombiesValidationIssue error(ZombiesErrorCode code, String subject, String message) {
        return new ZombiesValidationIssue(ZombiesMapValidationContributor.Severity.ERROR, code, subject, message);
    }

    public static ZombiesValidationIssue warning(ZombiesErrorCode code, String subject, String message) {
        return new ZombiesValidationIssue(ZombiesMapValidationContributor.Severity.WARNING, code, subject, message);
    }

    public static ZombiesValidationIssue fromContributorIssue(
            ZombiesMapValidationContributor.ZombiesValidationIssue issue
    ) {
        Objects.requireNonNull(issue, "issue");
        return new ZombiesValidationIssue(issue.severity(), issue.code(), issue.subject(), issue.message());
    }

    public ZombiesMapValidationContributor.ZombiesValidationIssue toContributorIssue() {
        return new ZombiesMapValidationContributor.ZombiesValidationIssue(severity, code, subject, message);
    }

    public boolean isError() {
        return severity == ZombiesMapValidationContributor.Severity.ERROR;
    }
}
