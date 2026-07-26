package com.cdp.codpattern.app.tdm.model;

import com.cdp.codpattern.app.match.GameModeBootstrap;
import com.cdp.codpattern.app.match.model.ClientModePresentation;
import net.minecraft.resources.ResourceLocation;

public final class TdmClientModePresentations {
    private TdmClientModePresentations() {
    }

    public static void registerDefaults() {
        GameModeBootstrap.registerClientPresentations();
    }

    public static ClientModePresentation frontlinePresentation() {
        return new ClientModePresentation(
                new ResourceLocation("codpattern", "textures/gui/modes/frontline_preview.png"),
                16001,
                9001,
                0xFF62F08A,
                "screen.codpattern.mode_select.hover_frontline",
                "frontline");
    }

    public static ClientModePresentation teamDeathmatchPresentation() {
        return new ClientModePresentation(
                new ResourceLocation("codpattern", "textures/gui/modes/team_death_match_preview.png"),
                16047,
                9001,
                0xFF5FC7C3,
                "screen.codpattern.mode_select.hover_teamdeathmatch",
                "teamdeathmatch");
    }
}
