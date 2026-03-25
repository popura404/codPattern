package com.cdp.codpattern.app.match;

import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.app.match.model.TeamDescriptor;
import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.app.tdm.model.TdmTeamNames;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GameModeRegistry {
    private static final Map<String, ModeDescriptor> DESCRIPTORS = new LinkedHashMap<>();

    static {
        register(new ModeDescriptor(
                TdmGameTypes.CDP_TDM,
                "mode.codpattern.frontline",
                "screen.codpattern.tdm_room.header",
                "/cdp map create frontline <名称> <起点> <终点>",
                List.of(
                        new TeamDescriptor(TdmTeamNames.KORTAC,
                                "screen.codpattern.tdm_room.team.kortac",
                                "hud.codpattern.tdm.team.kortac_short",
                                0xFFE35A5A),
                        new TeamDescriptor(TdmTeamNames.SPECGRU,
                                "screen.codpattern.tdm_room.team.specgru",
                                "hud.codpattern.tdm.team.specgru_short",
                                0xFF66A6FF)
                )
        ));
        register(new ModeDescriptor(
                TdmGameTypes.CDP_TACTICAL_TDM,
                "mode.codpattern.teamdeathmatch",
                "screen.codpattern.tactical_room.header",
                "/cdp map create teamdeathmatch <名称> <起点> <终点>",
                List.of(
                        new TeamDescriptor(TdmTeamNames.KORTAC,
                                "screen.codpattern.tdm_room.team.kortac",
                                "hud.codpattern.tdm.team.kortac_short",
                                0xFFE35A5A),
                        new TeamDescriptor(TdmTeamNames.SPECGRU,
                                "screen.codpattern.tdm_room.team.specgru",
                                "hud.codpattern.tdm.team.specgru_short",
                                0xFF66A6FF)
                )
        ));
    }

    private GameModeRegistry() {
    }

    public static void register(ModeDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        DESCRIPTORS.put(descriptor.gameType(), descriptor);
    }

    public static Optional<ModeDescriptor> find(String gameType) {
        if (gameType == null || gameType.isBlank()) {
            return Optional.empty();
        }
        ModeDescriptor descriptor = DESCRIPTORS.get(gameType);
        if (descriptor != null) {
            return Optional.of(descriptor);
        }
        return Optional.ofNullable(DESCRIPTORS.get(TdmGameTypes.canonicalize(gameType)));
    }

    public static ModeDescriptor getOrDefault(String gameType) {
        return find(gameType).orElseGet(() -> new ModeDescriptor(
                gameType,
                "mode.codpattern.unknown",
                "screen.codpattern.tdm_room.header",
                "",
                List.of()
        ));
    }
}
