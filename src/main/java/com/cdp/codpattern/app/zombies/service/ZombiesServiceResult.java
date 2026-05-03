package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Common result DTO for zombies services; failures carry a stable code and structured prompt/HUD params.
 */
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
}
