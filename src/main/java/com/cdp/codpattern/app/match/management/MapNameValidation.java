package com.cdp.codpattern.app.match.management;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Stream;

/** Shared name rules; no client or server runtime dependencies. */
public final class MapNameValidation {
    public enum Status { VALID, EMPTY, TOO_LONG, INVALID_CHARACTERS, UNCHANGED, CONFLICT }
    public record Result(String normalized, Status status) {
        public boolean valid() { return status == Status.VALID; }
    }

    private MapNameValidation() { }

    public static Result validate(String input) {
        String name = Objects.requireNonNullElse(input, "").trim();
        if (name.isBlank()) return new Result(name, Status.EMPTY);
        if (name.codePoints().anyMatch(c -> Character.isISOControl(c)
                || Character.getType(c) == Character.SURROGATE))
            return new Result(name, Status.INVALID_CHARACTERS);
        if (name.getBytes(StandardCharsets.UTF_8).length > 100) return new Result(name, Status.TOO_LONG);
        return new Result(name, Status.VALID);
    }

    /** Caller supplies names from the selected mode, including the current name. */
    public static Result rename(String input, String currentName, Stream<String> sameModeNames) {
        Result result = validate(input);
        if (!result.valid()) return result;
        if (result.normalized().equals(currentName)) return new Result(result.normalized(), Status.UNCHANGED);
        if (sameModeNames.anyMatch(result.normalized()::equalsIgnoreCase))
            return new Result(result.normalized(), Status.CONFLICT);
        return result;
    }
}
