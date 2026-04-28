package com.cdp.codpattern.app.match.editor;

import java.util.Objects;

public record ObjectFeatureDefinition(
        String key,
        String displayNameKey,
        boolean repeatable
) {
    public ObjectFeatureDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
    }
}
