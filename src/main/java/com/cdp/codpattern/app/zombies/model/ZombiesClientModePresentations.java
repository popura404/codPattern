package com.cdp.codpattern.app.zombies.model;

import com.cdp.codpattern.app.match.GameModeBootstrap;
import com.cdp.codpattern.app.match.model.ClientModePresentation;

public final class ZombiesClientModePresentations {
    private ZombiesClientModePresentations() {
    }

    public static void registerDefaults() {
        GameModeBootstrap.registerClientPresentations();
    }

    public static ClientModePresentation zombiesPresentation() {
        return new ClientModePresentation(
                "textures/gui/modes/zombies_preview.png",
                1920,
                1080,
                0xFF9B2F2F,
                "screen.codpattern.mode_select.hover_zombies",
                "zombies");
    }
}
