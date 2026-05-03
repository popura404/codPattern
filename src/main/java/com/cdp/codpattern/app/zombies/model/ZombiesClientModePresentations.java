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
                16047,
                9001,
                0xFF6FD17A,
                "screen.codpattern.mode_select.hover_zombies",
                "zombies");
    }
}
