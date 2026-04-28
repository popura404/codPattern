package com.cdp.codpattern.app.tdm.model;

import com.cdp.codpattern.app.match.model.ClientModePresentation;
import com.cdp.codpattern.app.match.model.ClientModePresentationRegistry;

public final class TdmClientModePresentations {
    private TdmClientModePresentations() {
    }

    public static void registerDefaults() {
        ClientModePresentationRegistry.register(TdmGameTypes.CDP_TDM, new ClientModePresentation(
                "textures/gui/modes/frontline_preview.png",
                16001,
                9001,
                0xFF62F08A,
                "screen.codpattern.mode_select.hover_frontline",
                "frontline"));
        ClientModePresentationRegistry.register(TdmGameTypes.CDP_TACTICAL_TDM, new ClientModePresentation(
                "textures/gui/modes/team_death_match_preview.png",
                16047,
                9001,
                0xFF5FC7C3,
                "screen.codpattern.mode_select.hover_teamdeathmatch",
                "teamdeathmatch"));
    }
}
