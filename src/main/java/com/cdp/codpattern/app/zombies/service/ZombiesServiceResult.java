package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.result.ModeOperationResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Common result DTO for zombies services; failures carry a stable code and structured prompt/HUD params.
 *
 * @deprecated Use {@link ModeOperationResult} at new public/shared boundaries. Existing Zombies
 * callers remain supported through the lossless conversion methods during preparation.
 */
@Deprecated(forRemoval = false, since = "mode-split-phase1")
public record ZombiesServiceResult<T>(
        boolean success,
        ZombiesErrorCode code,
        Map<String, ModePlayerValue> params,
        Optional<T> value,
        String logMessage
) {
    public ZombiesServiceResult {
        code = code == null ? ZombiesErrorCode.OK : code;
        params = params == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(params));
        value = value == null ? Optional.empty() : value;
        logMessage = Objects.requireNonNullElse(logMessage, "");
    }

    public static <T> ZombiesServiceResult<T> success(T value) {
        return new ZombiesServiceResult<>(true, ZombiesErrorCode.OK, Map.of(), Optional.ofNullable(value), "");
    }

    public static ZombiesServiceResult<Void> ok() {
        return new ZombiesServiceResult<>(true, ZombiesErrorCode.OK, Map.of(), Optional.empty(), "");
    }

    public static <T> ZombiesServiceResult<T> failure(ZombiesErrorCode code) {
        return failure(code, Map.of(), "");
    }

    public static <T> ZombiesServiceResult<T> failure(
            ZombiesErrorCode code,
            Map<String, ModePlayerValue> params,
            String logMessage
    ) {
        return new ZombiesServiceResult<>(false, code, params, Optional.empty(), logMessage);
    }

    public ModeOperationResult<T> toModeResult() {
        return new ModeOperationResult<>(
                success,
                code.toModeErrorCode(),
                "",
                params,
                java.util.List.of(),
                value,
                logMessage);
    }

    public static <T> ZombiesServiceResult<T> fromModeResult(ModeOperationResult<T> result) {
        Objects.requireNonNull(result, "result");
        if (!result.messageKey().isEmpty() || !result.arguments().isEmpty()) {
            throw new IllegalArgumentException(
                    "ZombiesServiceResult cannot represent messageKey or ordered arguments");
        }
        return new ZombiesServiceResult<>(
                result.success(),
                ZombiesErrorCode.fromModeErrorCode(result.code()),
                result.parameters(),
                result.value(),
                result.logMessage());
    }
}
