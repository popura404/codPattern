package com.cdp.codpattern.app.match.model;

import java.util.Objects;

public record ModePrompt(
        String key,
        String translationKey
) {
    public ModePrompt {
        key = Objects.requireNonNullElse(key, "").trim();
        translationKey = Objects.requireNonNullElse(translationKey, "").trim();
    }
}
