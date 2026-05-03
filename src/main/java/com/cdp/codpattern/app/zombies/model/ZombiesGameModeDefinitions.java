package com.cdp.codpattern.app.zombies.model;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.match.model.JoinPolicy;
import com.cdp.codpattern.app.match.model.LifecycleKind;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.ModeFamily;
import com.cdp.codpattern.app.match.model.ScoreboardKind;
import com.cdp.codpattern.app.match.model.TeamDescriptor;
import com.cdp.codpattern.app.match.model.TeamPolicy;
import com.cdp.codpattern.compat.fpsmatch.data.ZombiesMapData;
import com.cdp.codpattern.compat.fpsmatch.map.ZombiesRuntimeProvider;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ZombiesGameModeDefinitions {
    private ZombiesGameModeDefinitions() {
    }

    public static void registerDefaults() {
        definitions().forEach(GameModeRegistry::registerDefinition);
    }

    public static List<GameModeDefinition> definitions() {
        return List.of(zombies());
    }

    private static GameModeDefinition zombies() {
        return new GameModeDefinition(
                BuiltInGameModes.ZOMBIES,
                List.of(),
                "mode.codpattern.zombies",
                "screen.codpattern.zombies_room.header",
                "/cdp map create zombies <名称> <起点> <终点>",
                teams(),
                ModeFamily.PVE_COOP,
                TeamPolicy.SINGLE_COOP_TEAM,
                JoinPolicy.MODE_DEFINED,
                LifecycleKind.WAVE_OR_ROUND_LOOP,
                ScoreboardKind.PROGRESS_METRICS,
                Set.of(
                        ModeCapability.READY_STATE,
                        ModeCapability.START_VOTE,
                        ModeCapability.MATCH_END_TELEPORT,
                        ModeCapability.MODE_SPECIFIC_MAP_FEATURES
                ),
                Optional.of(ZombiesRuntimeProvider.INSTANCE),
                Optional.of(ZombiesMapData.persistenceProvider()),
                Optional.empty(),
                Optional.of(ZombiesClientModePresentations.zombiesPresentation())
        );
    }

    private static List<TeamDescriptor> teams() {
        return List.of(
                new TeamDescriptor(ZombiesTeamNames.SURVIVORS,
                        "screen.codpattern.zombies_room.team.survivors",
                        "hud.codpattern.zombies.team.survivors_short",
                        0xFF6FD17A)
        );
    }
}
