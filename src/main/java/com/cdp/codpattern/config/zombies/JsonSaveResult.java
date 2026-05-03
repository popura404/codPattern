package com.cdp.codpattern.config.zombies;

import java.io.IOException;
import java.nio.file.Path;

public final class JsonSaveResult {
    private final boolean success;
    private final boolean skipped;
    private final Path path;
    private final String message;
    private final IOException exception;

    private JsonSaveResult(boolean success, boolean skipped, Path path, String message, IOException exception) {
        this.success = success;
        this.skipped = skipped;
        this.path = path;
        this.message = message;
        this.exception = exception;
    }

    public static JsonSaveResult success(Path path) {
        return new JsonSaveResult(true, false, path, "Saved JSON config: " + path, null);
    }

    public static JsonSaveResult skipped(Path path, String message) {
        return new JsonSaveResult(false, true, path, message, null);
    }

    public static JsonSaveResult failure(Path path, String message, IOException exception) {
        return new JsonSaveResult(false, false, path, message, exception);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public boolean isFailure() {
        return !success && !skipped;
    }

    public Path getPath() {
        return path;
    }

    public String getMessage() {
        return message;
    }

    public IOException getException() {
        return exception;
    }
}
