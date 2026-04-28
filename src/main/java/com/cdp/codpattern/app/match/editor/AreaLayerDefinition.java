package com.cdp.codpattern.app.match.editor;

import java.util.Objects;

public record AreaLayerDefinition(
        String key,
        String displayNameKey,
        boolean repeatable
) {
    public AreaLayerDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
    }
}
