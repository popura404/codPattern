package com.cdp.codpattern.app.tdm.model;

import com.cdp.codpattern.app.match.model.ClientModePresentation;
import net.minecraft.resources.ResourceLocation;

public final class TdmClientModePresentations {
    public static final int FRONTLINE_ACCENT_COLOR = 0xFF62F08A;
    public static final int TEAM_DEATHMATCH_ACCENT_COLOR = 0xFF5FC7C3;

    private TdmClientModePresentations() {
    }

    public static ClientModePresentation frontlinePresentation() {
        return new ClientModePresentation(
                new ResourceLocation("codpattern", "textures/gui/modes/frontline_preview.png"),
                16001,
                9001,
                FRONTLINE_ACCENT_COLOR,
                "screen.codpattern.mode_select.hover_frontline",
                "frontline");
    }

    public static ClientModePresentation teamDeathmatchPresentation() {
        return new ClientModePresentation(
                new ResourceLocation("codpattern", "textures/gui/modes/team_death_match_preview.png"),
                16047,
                9001,
                TEAM_DEATHMATCH_ACCENT_COLOR,
                "screen.codpattern.mode_select.hover_teamdeathmatch",
                "teamdeathmatch");
    }
}
