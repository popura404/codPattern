package com.cdp.codpattern.app.zombies.deploy;

import com.cdp.codpattern.app.match.model.result.ModeErrorCode;
import com.cdp.codpattern.app.match.model.result.ModeOperationResult;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @deprecated Use {@link ModeOperationResult} at new public/shared boundaries. Existing deploy
 * callers remain supported through the lossless conversion methods during preparation.
 */
@Deprecated(forRemoval = false, since = "mode-split-phase1")
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

    public ModeOperationResult<T> toModeResult() {
        return new ModeOperationResult<>(
                success,
                ModeErrorCode.of(code),
                messageKey,
                java.util.Map.of(),
                arguments,
                value,
                "");
    }

    public static <T> ZombiesDeployServiceResult<T> fromModeResult(ModeOperationResult<T> result) {
        Objects.requireNonNull(result, "result");
        if (!result.parameters().isEmpty() || !result.logMessage().isEmpty()) {
            throw new IllegalArgumentException(
                    "ZombiesDeployServiceResult cannot represent named parameters or log diagnostics");
        }
        return new ZombiesDeployServiceResult<>(
                result.success(),
                result.code().key(),
                result.messageKey(),
                result.arguments(),
                result.value());
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
