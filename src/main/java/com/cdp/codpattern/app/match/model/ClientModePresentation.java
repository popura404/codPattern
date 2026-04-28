package com.cdp.codpattern.app.match.model;

import java.util.Objects;

public record ClientModePresentation(
        String previewTexturePath,
        int textureWidth,
        int textureHeight,
        int accentColor,
        String descriptionKey,
        String overlayStyle
) {
    public ClientModePresentation {
        previewTexturePath = previewTexturePath == null ? "" : previewTexturePath;
        Objects.requireNonNull(descriptionKey, "descriptionKey");
        overlayStyle = overlayStyle == null ? "" : overlayStyle;
    }
}
