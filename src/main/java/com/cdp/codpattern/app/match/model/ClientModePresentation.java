package com.cdp.codpattern.app.match.model;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ClientModePresentation(
        ResourceLocation previewTexture,
        int textureWidth,
        int textureHeight,
        int accentColor,
        String descriptionKey,
        String overlayStyle
) {
    public ClientModePresentation {
        Objects.requireNonNull(descriptionKey, "descriptionKey");
        overlayStyle = overlayStyle == null ? "" : overlayStyle;
    }

    /** Compatibility constructor for legacy main-namespace path-only presentations. */
    public ClientModePresentation(
            String previewTexturePath,
            int textureWidth,
            int textureHeight,
            int accentColor,
            String descriptionKey,
            String overlayStyle
    ) {
        this(
                previewTexturePath == null || previewTexturePath.isBlank()
                        ? null
                        : new ResourceLocation("codpattern", previewTexturePath),
                textureWidth,
                textureHeight,
                accentColor,
                descriptionKey,
                overlayStyle);
    }
}
