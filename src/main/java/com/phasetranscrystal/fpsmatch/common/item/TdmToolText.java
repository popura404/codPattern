package com.phasetranscrystal.fpsmatch.common.item;

import com.cdp.codpattern.app.tdm.model.TdmClientModePresentations;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class TdmToolText {
    private TdmToolText() {
    }

    static Component itemName(String translationKey) {
        return Component.translatable(
                translationKey,
                teamDeathmatchLabel(),
                frontlineLabel()
        );
    }

    static Component applicableModesTooltip() {
        return Component.translatable(
                "tooltip.codpattern.applicable_modes",
                teamDeathmatchLabel(),
                frontlineLabel()
        );
    }

    private static MutableComponent teamDeathmatchLabel() {
        return coloredModeLabel(
                "mode.codpattern.teamdeathmatch.tool_name",
                TdmClientModePresentations.TEAM_DEATHMATCH_ACCENT_COLOR
        );
    }

    private static MutableComponent frontlineLabel() {
        return coloredModeLabel(
                "mode.codpattern.frontline.tool_name",
                TdmClientModePresentations.FRONTLINE_ACCENT_COLOR
        );
    }

    private static MutableComponent coloredModeLabel(String translationKey, int argbColor) {
        return Component.translatable(translationKey)
                .withStyle(style -> style.withColor(argbColor & 0xFFFFFF));
    }
}
