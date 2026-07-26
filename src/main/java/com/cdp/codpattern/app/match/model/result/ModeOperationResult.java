package com.cdp.codpattern.app.match.model.result;

import com.cdp.codpattern.app.match.model.ModePlayerValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Public result contract that keeps identity, presentation, value, and diagnostics independent. */
public record ModeOperationResult<T>(
        boolean success,
        ModeErrorCode code,
        String messageKey,
        Map<String, ModePlayerValue> parameters,
        List<String> arguments,
        Optional<T> value,
        String logMessage
) {
    public ModeOperationResult {
        code = code == null ? ModeErrorCode.OK : code;
        messageKey = Objects.requireNonNullElse(messageKey, "").trim();
        parameters = parameters == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(parameters));
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        value = value == null ? Optional.empty() : value;
        logMessage = Objects.requireNonNullElse(logMessage, "");
    }

    public static <T> ModeOperationResult<T> success(T value) {
        return new ModeOperationResult<>(
                true,
                ModeErrorCode.OK,
                "",
                Map.of(),
                List.of(),
                Optional.ofNullable(value),
                "");
    }

    public static <T> ModeOperationResult<T> failure(
            ModeErrorCode code,
            String messageKey,
            Map<String, ModePlayerValue> parameters,
            List<String> arguments,
            String logMessage
    ) {
        return new ModeOperationResult<>(
                false,
                code,
                messageKey,
                parameters,
                arguments,
                Optional.empty(),
                logMessage);
    }
}
