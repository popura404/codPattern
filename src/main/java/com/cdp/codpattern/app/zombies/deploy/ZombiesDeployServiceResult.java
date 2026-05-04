package com.cdp.codpattern.app.zombies.deploy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ZombiesDeployServiceResult<T>(
        boolean success,
        String code,
        String messageKey,
        List<String> arguments,
        Optional<T> value
) {
    public ZombiesDeployServiceResult {
        code = normalize(code, success ? "ok" : "error");
        messageKey = normalize(messageKey, success
                ? "message.codpattern.zombies.deploy.ok"
                : "message.codpattern.zombies.deploy.failed");
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        value = value == null ? Optional.empty() : value;
    }

    public static <T> ZombiesDeployServiceResult<T> success(T value, String messageKey, String... arguments) {
        return new ZombiesDeployServiceResult<>(
                true,
                "ok",
                messageKey,
                arguments == null ? List.of() : List.of(arguments),
                Optional.ofNullable(value));
    }

    public static <T> ZombiesDeployServiceResult<T> failure(
            String code,
            String messageKey,
            T value,
            String... arguments
    ) {
        return new ZombiesDeployServiceResult<>(
                false,
                code,
                messageKey,
                arguments == null ? List.of() : List.of(arguments),
                Optional.ofNullable(value));
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
