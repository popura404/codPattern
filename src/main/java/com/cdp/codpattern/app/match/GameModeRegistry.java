package com.cdp.codpattern.app.match;

import com.cdp.codpattern.app.match.model.GameModeDefinition;
import com.cdp.codpattern.app.match.model.JoinPolicy;
import com.cdp.codpattern.app.match.model.LifecycleKind;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.ModeDescriptor;
import com.cdp.codpattern.app.match.model.ModeFamily;
import com.cdp.codpattern.app.match.model.ScoreboardKind;
import com.cdp.codpattern.app.match.model.TeamDescriptor;
import com.cdp.codpattern.app.match.model.TeamPolicy;
import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.app.tdm.model.TdmTeamNames;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;

public final class GameModeRegistry {
    private static final Map<String, GameModeDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        registerDefinition(new GameModeDefinition(
                TdmGameTypes.FRONTLINE,
                List.of(TdmGameTypes.LEGACY_CDP_TDM),
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
                ),
                ModeFamily.PVP_TEAM,
                TeamPolicy.FIXED_TEAMS,
                JoinPolicy.MODE_DEFINED,
                LifecycleKind.WAITING_START_PLAYING_ENDED,
                ScoreboardKind.TEAM_SCORE,
                Set.of(
                        ModeCapability.TEAM_SELECTION,
                        ModeCapability.TEAM_BALANCE,
                        ModeCapability.READY_STATE,
                        ModeCapability.START_VOTE,
                        ModeCapability.END_VOTE,
                        ModeCapability.MATCH_END_TELEPORT,
                        ModeCapability.ROUND_START_SPAWNS,
                        ModeCapability.KILL_FEED,
                        ModeCapability.MATCH_RECORD_EXPORT,
                        ModeCapability.MODE_SPECIFIC_MAP_FEATURES
                )
        ));
        registerDefinition(new GameModeDefinition(
                TdmGameTypes.TEAM_DEATHMATCH,
                List.of(TdmGameTypes.LEGACY_CDP_TACTICAL_TDM),
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
                ),
                ModeFamily.PVP_TEAM,
                TeamPolicy.FIXED_TEAMS,
                JoinPolicy.MODE_DEFINED,
                LifecycleKind.WAITING_START_PLAYING_ENDED,
                ScoreboardKind.TEAM_SCORE,
                Set.of(
                        ModeCapability.TEAM_SELECTION,
                        ModeCapability.TEAM_BALANCE,
                        ModeCapability.READY_STATE,
                        ModeCapability.START_VOTE,
                        ModeCapability.END_VOTE,
                        ModeCapability.MATCH_END_TELEPORT,
                        ModeCapability.ROUND_START_SPAWNS,
                        ModeCapability.DYNAMIC_RESPAWN_POINTS,
                        ModeCapability.KILL_FEED,
                        ModeCapability.MATCH_RECORD_EXPORT,
                        ModeCapability.MODE_SPECIFIC_MAP_FEATURES
                )
        ));
    }

    private GameModeRegistry() {
    }

    public static void register(ModeDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        registerDefinition(new GameModeDefinition(
                descriptor.gameType(),
                List.of(),
                descriptor.displayNameKey(),
                descriptor.roomHeaderKey(),
                descriptor.createCommand(),
                descriptor.teams(),
                ModeFamily.CUSTOM,
                TeamPolicy.NONE,
                JoinPolicy.MODE_DEFINED,
                LifecycleKind.MODE_DEFINED,
                ScoreboardKind.MODE_DEFINED,
                Set.of()
        ));
    }

    public static void registerDefinition(GameModeDefinition definition) {
        if (definition == null) {
            return;
        }
        String canonical = normalize(definition.gameType());
        GameModeDefinition normalizedDefinition = new GameModeDefinition(
                canonical,
                definition.aliases(),
                definition.displayNameKey(),
                definition.roomHeaderKey(),
                definition.createCommand(),
                definition.teams(),
                definition.family(),
                definition.teamPolicy(),
                definition.joinPolicy(),
                definition.lifecycleKind(),
                definition.scoreboardKind(),
                definition.capabilities()
        );
        DEFINITIONS.put(canonical, normalizedDefinition);
        ALIASES.put(canonical, canonical);
        for (String alias : normalizedDefinition.aliases()) {
            String normalizedAlias = normalize(alias);
            if (!normalizedAlias.isBlank()) {
                ALIASES.put(normalizedAlias, canonical);
            }
        }
    }

    public static Optional<ModeDescriptor> find(String gameType) {
        return findDefinition(gameType).map(GameModeDefinition::descriptor);
    }

    public static Optional<GameModeDefinition> findDefinition(String gameType) {
        String canonical = canonicalize(gameType);
        if (canonical.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(DEFINITIONS.get(canonical));
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

    public static List<ModeDescriptor> orderedModes() {
        return DEFINITIONS.values().stream()
                .map(GameModeDefinition::descriptor)
                .toList();
    }

    public static String canonicalize(String gameType) {
        String normalized = normalize(gameType);
        if (normalized.isBlank()) {
            return normalized;
        }
        return ALIASES.getOrDefault(normalized, normalized);
    }

    public static Set<ModeCapability> capabilities(String gameType) {
        return findDefinition(gameType)
                .map(GameModeDefinition::capabilities)
                .orElseGet(Set::of);
    }

    public static boolean hasCapability(String gameType, ModeCapability capability) {
        return capability != null && capabilities(gameType).contains(capability);
    }

    private static String normalize(String gameType) {
        return gameType == null ? "" : gameType.trim().toLowerCase(Locale.ROOT);
    }
}
