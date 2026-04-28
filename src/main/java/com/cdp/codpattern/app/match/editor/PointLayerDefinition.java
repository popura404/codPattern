package com.cdp.codpattern.app.match.editor;

import java.util.Objects;

public record PointLayerDefinition(
        String key,
        String displayNameKey,
        boolean repeatable,
        boolean teamScoped
) {
    public PointLayerDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
    }
}
