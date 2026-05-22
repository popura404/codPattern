package com.cdp.codpattern.client.zombies;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.model.RoomSummaryMetric;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffType;
import com.cdp.codpattern.app.zombies.model.ZombiesTeamNames;
import com.cdp.codpattern.app.zombies.sync.ZombiesRuntimeStateKeys;
import com.cdp.codpattern.client.ClientMatchState;
import com.cdp.codpattern.client.ClientModeRuntimeState;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClientZombiesState {
    private ClientZombiesState() {
    }

    public static Optional<ModeRuntimeStateSnapshot> snapshot() {
        Optional<ModeRuntimeStateSnapshot> latest = latestZombiesSnapshot();
        try {
            String roomKey = ClientMatchState.roomContextName();
            Optional<ModeRuntimeStateSnapshot> current = ClientModeRuntimeState.snapshot(roomKey)
                    .filter(snapshot -> isZombiesRoom(snapshot.roomKey()));
            if (current.isPresent()) {
                return current;
            }
        } catch (ExceptionInInitializerError | NoClassDefFoundError ignored) {
            // Pure Java compatibility tests can exercise cached runtime snapshots without bootstrapping Minecraft client state.
        }
        return latest;
    }

    private static Optional<ModeRuntimeStateSnapshot> latestZombiesSnapshot() {
        return ClientModeRuntimeState.snapshots().values().stream()
                .filter(snapshot -> isZombiesRoom(snapshot.roomKey()))
                .max(Comparator.comparingLong(ModeRuntimeStateSnapshot::revision));
    }

    public static boolean shouldRenderHud() {
        return snapshot()
                .map(snapshot -> !"WAITING".equals(snapshot.phaseKey()) || metric(snapshot, ZombiesRuntimeStateKeys.METRIC_WAVE) > 0)
                .orElse(false);
    }

    public static boolean shouldReplaceVanillaPlayerHud() {
        return hasCurrentZombiesRoomContext() && shouldRenderHud();
    }

    public static String phaseKey() {
        return snapshot().map(ModeRuntimeStateSnapshot::phaseKey).orElse("");
    }

    public static int remainingTimeTicks() {
        return snapshot().map(ModeRuntimeStateSnapshot::remainingTimeTicks).orElse(0);
    }

    public static int wave() {
        return snapshot().map(snapshot -> metric(snapshot, ZombiesRuntimeStateKeys.METRIC_WAVE)).orElse(0);
    }

    public static int zombiesLeft() {
        return snapshot().map(snapshot -> metric(snapshot, ZombiesRuntimeStateKeys.METRIC_ZOMBIES_LEFT)).orElse(0);
    }

    public static int alivePlayers() {
        return snapshot().map(snapshot -> metric(snapshot, ZombiesRuntimeStateKeys.METRIC_ALIVE_PLAYERS)).orElse(0);
    }

    public static int maxPlayers() {
        return snapshot().map(snapshot -> metric(snapshot, ZombiesRuntimeStateKeys.METRIC_MAX_PLAYERS)).orElse(0);
    }

    public static int points() {
        return snapshot()
                .map(snapshot -> intValue(snapshot.playerValues().get(ZombiesRuntimeStateKeys.PLAYER_POINTS), 0))
                .orElse(0);
    }

    public static int totalEarnedPoints() {
        return snapshot()
                .map(snapshot -> intValue(
                        snapshot.playerValues().get(ZombiesRuntimeStateKeys.PLAYER_TOTAL_EARNED_POINTS),
                        0))
                .orElse(0);
    }

    public static int kills() {
        return snapshot()
                .map(snapshot -> intValue(snapshot.playerValues().get(ZombiesRuntimeStateKeys.PLAYER_KILLS), 0))
                .orElse(0);
    }

    public static int assists() {
        return snapshot()
                .map(snapshot -> intValue(snapshot.playerValues().get(ZombiesRuntimeStateKeys.PLAYER_ASSISTS), 0))
                .orElse(0);
    }

    public static int deaths() {
        return snapshot()
                .map(snapshot -> intValue(snapshot.playerValues().get(ZombiesRuntimeStateKeys.PLAYER_DEATHS), 0))
                .orElse(0);
    }

    public static int barriersOpened() {
        return snapshot()
                .map(snapshot -> intValue(snapshot.playerValues().get(ZombiesRuntimeStateKeys.PLAYER_BARRIERS_OPENED), 0))
                .orElse(0);
    }

    public static boolean powerEnabled() {
        return snapshot()
                .map(snapshot -> booleanValue(snapshot.playerValues().get(ZombiesRuntimeStateKeys.PLAYER_POWER_ENABLED), false))
                .orElse(false);
    }

    public static int armorLevel() {
        return snapshot()
                .map(snapshot -> intValue(snapshot.playerValues().get(ZombiesRuntimeStateKeys.PLAYER_ARMOR_LEVEL), 0))
                .orElse(0);
    }

    public static int primaryUpgradeLevel() {
        return snapshot()
                .map(snapshot -> intValue(snapshot.playerValues().get(ZombiesRuntimeStateKeys.PLAYER_WEAPON_PRIMARY_UPGRADE), 0))
                .orElse(0);
    }

    public static boolean buffEnabled(String buffId) {
        return snapshot()
                .map(snapshot -> booleanValue(snapshot.playerValues().get(ZombiesRuntimeStateKeys.playerBuff(buffId)), false))
                .orElse(false);
    }

    public static List<String> ownedBuffIds() {
        Optional<ModeRuntimeStateSnapshot> snapshotOptional = snapshot();
        if (snapshotOptional.isEmpty()) {
            return List.of();
        }

        Set<String> buffIds = new LinkedHashSet<>();
        for (ZombiesBuffType type : ZombiesBuffType.values()) {
            if (buffEnabled(type.id())) {
                buffIds.add(type.id());
            }
        }
        snapshotOptional.get().playerValues().forEach((key, value) -> {
            if (key != null
                    && key.startsWith(ZombiesRuntimeStateKeys.PLAYER_BUFF_PREFIX)
                    && booleanValue(value, false)) {
                String buffId = key.substring(ZombiesRuntimeStateKeys.PLAYER_BUFF_PREFIX.length()).trim();
                if (!buffId.isBlank()) {
                    buffIds.add(buffId);
                }
            }
        });
        return List.copyOf(buffIds);
    }

    public static Set<UUID> activeZombieEntityIds() {
        Optional<ModeRuntimeStateSnapshot> snapshotOptional = snapshot();
        if (snapshotOptional.isEmpty()) {
            return Set.of();
        }
        ModePlayerValue value = snapshotOptional.get().playerValues().get(ZombiesRuntimeStateKeys.ACTIVE_ZOMBIE_ENTITY_IDS);
        if (value == null || value.value().isBlank()) {
            return Set.of();
        }

        Set<UUID> ids = new LinkedHashSet<>();
        for (String rawId : value.value().split(",")) {
            String cleaned = rawId == null ? "" : rawId.trim();
            if (cleaned.isBlank()) {
                continue;
            }
            try {
                ids.add(UUID.fromString(cleaned));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed server values so one bad id does not disable the HUD.
            }
        }
        return Set.copyOf(ids);
    }

    public static List<SurvivorStatus> survivors() {
        Optional<ModeRuntimeStateSnapshot> snapshotOptional = snapshot();
        if (snapshotOptional.isEmpty()) {
            return List.of();
        }
        UUID localPlayerId = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        return buildSurvivorStatuses(snapshotOptional.get(), ClientMatchState.teamPlayersSnapshot(), localPlayerId);
    }

    public static List<SurvivorStatus> roomTeammates() {
        return filterRoomTeammates(survivors());
    }

    public static List<ResultRow> leaderboardRows() {
        Optional<ModeRuntimeStateSnapshot> snapshotOptional = snapshot();
        if (snapshotOptional.isEmpty()) {
            return List.of();
        }
        UUID localPlayerId = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        return buildResultRows(snapshotOptional.get(), ClientMatchState.teamPlayersSnapshot(), localPlayerId);
    }

    static List<SurvivorStatus> buildSurvivorStatuses(
            ModeRuntimeStateSnapshot snapshot,
            Map<String, List<PlayerInfo>> rosters,
            UUID localPlayerId
    ) {
        if (snapshot == null || rosters == null) {
            return List.of();
        }
        List<PlayerInfo> survivorRoster = rosters.getOrDefault(ZombiesTeamNames.SURVIVORS, List.of());
        if (survivorRoster.isEmpty()) {
            return List.of();
        }

        List<SurvivorStatus> statuses = new ArrayList<>();
        for (PlayerInfo playerInfo : survivorRoster) {
            if (playerInfo == null) {
                continue;
            }
            UUID playerId = playerInfo.uuid();
            String playerIdText = playerId == null ? "" : playerId.toString();
            String lifeState = stringValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorLifeState(playerIdText)),
                    playerInfo.isAlive() ? "ALIVE" : "DEAD_SPECTATING");
            String connectionState = stringValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorConnectionState(playerIdText)),
                    "");
            int points = intValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorPoints(playerIdText)),
                    0);
            int totalEarnedPoints = intValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorTotalEarnedPoints(playerIdText)),
                    points);
            int kills = intValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorKills(playerIdText)),
                    playerInfo.kills());
            int deaths = intValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorDeaths(playerIdText)),
                    playerInfo.deaths());
            int barriersOpened = intValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorBarriersOpened(playerIdText)),
                    0);
            int armorLevel = intValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorArmorLevel(playerIdText)),
                    0);
            double fallbackHealth = defaultSurvivorHealth(lifeState, connectionState);
            double health = doubleValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorHealth(playerIdText)),
                    fallbackHealth);
            double maxHealth = doubleValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorMaxHealth(playerIdText)),
                    1.0D);
            statuses.add(new SurvivorStatus(
                    playerId,
                    playerInfo.name(),
                    lifeState,
                    connectionState,
                    points,
                    totalEarnedPoints,
                    kills,
                    deaths,
                    barriersOpened,
                    armorLevel,
                    health,
                    maxHealth,
                    playerId != null && playerId.equals(localPlayerId)));
        }
        return List.copyOf(statuses);
    }

    static List<SurvivorStatus> filterRoomTeammates(List<SurvivorStatus> survivors) {
        if (survivors == null || survivors.isEmpty()) {
            return List.of();
        }
        return survivors.stream()
                .filter(survivor -> survivor != null && !survivor.self())
                .filter(survivor -> !"LEFT".equals(survivor.connectionState()))
                .toList();
    }

    static List<ResultRow> buildResultRows(
            ModeRuntimeStateSnapshot snapshot,
            Map<String, List<PlayerInfo>> rosters,
            UUID localPlayerId
    ) {
        if (snapshot == null) {
            return List.of();
        }
        Map<UUID, PlayerInfo> rosterById = new LinkedHashMap<>();
        if (rosters != null) {
            for (List<PlayerInfo> players : rosters.values()) {
                if (players == null) {
                    continue;
                }
                for (PlayerInfo player : players) {
                    if (player != null && player.uuid() != null) {
                        rosterById.putIfAbsent(player.uuid(), player);
                    }
                }
            }
        }

        Set<UUID> playerIds = new LinkedHashSet<>(rosterById.keySet());
        for (String key : snapshot.playerValues().keySet()) {
            UUID playerId = survivorPlayerIdFromKey(key);
            if (playerId != null) {
                playerIds.add(playerId);
            }
        }

        List<ResultRow> rows = new ArrayList<>();
        for (UUID playerId : playerIds) {
            String playerIdText = playerId.toString();
            PlayerInfo player = rosterById.get(playerId);
            String syncedName = stringValue(snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorName(playerIdText)), "");
            String name = safeName(syncedName.isBlank() && player != null ? player.name() : syncedName);
            int rosterKills = player == null ? 0 : player.kills();
            int rosterDeaths = player == null ? 0 : player.deaths();
            int points = intValue(snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorPoints(playerIdText)), 0);
            int totalEarnedPoints = intValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorTotalEarnedPoints(playerIdText)),
                    points);
            int kills = intValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorKills(playerIdText)),
                    rosterKills);
            int deaths = intValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorDeaths(playerIdText)),
                    rosterDeaths);
            int barriersOpened = intValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorBarriersOpened(playerIdText)),
                    0);
            String lifeState = stringValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorLifeState(playerIdText)),
                    player != null && player.isAlive() ? "ALIVE" : "DEAD_SPECTATING");
            String connectionState = stringValue(
                    snapshot.playerValues().get(ZombiesRuntimeStateKeys.survivorConnectionState(playerIdText)),
                    "");
            rows.add(new ResultRow(
                    playerId,
                    name,
                    lifeState,
                    connectionState,
                    totalEarnedPoints,
                    kills,
                    barriersOpened,
                    deaths,
                    playerId.equals(localPlayerId)));
        }
        rows.sort(RESULT_ROW_COMPARATOR);
        return List.copyOf(rows);
    }

    private static int metric(ModeRuntimeStateSnapshot snapshot, String key) {
        if (snapshot == null || key == null) {
            return 0;
        }
        for (RoomSummaryMetric metric : snapshot.metrics()) {
            if (key.equals(metric.key())) {
                return metric.value();
            }
        }
        return 0;
    }

    private static boolean isZombiesRoom(String roomKey) {
        if (roomKey == null || roomKey.isBlank()) {
            return false;
        }
        try {
            return BuiltInGameModes.isZombies(RoomId.decode(roomKey).gameType());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static final Comparator<ResultRow> RESULT_ROW_COMPARATOR = Comparator
            .comparingInt(ResultRow::totalEarnedPoints).reversed()
            .thenComparing(Comparator.comparingInt(ResultRow::kills).reversed())
            .thenComparing(Comparator.comparingInt(ResultRow::barriersOpened).reversed())
            .thenComparingInt(ResultRow::deaths)
            .thenComparing(row -> safeName(row.name()), String.CASE_INSENSITIVE_ORDER);

    private static UUID survivorPlayerIdFromKey(String key) {
        if (key == null || !key.startsWith("survivor.")) {
            return null;
        }
        String remainder = key.substring("survivor.".length());
        int separator = remainder.indexOf('.');
        if (separator <= 0) {
            return null;
        }
        try {
            return UUID.fromString(remainder.substring(0, separator));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean hasCurrentZombiesRoomContext() {
        try {
            return ClientMatchState.hasRoomContext() && isZombiesRoom(ClientMatchState.roomContextName());
        } catch (ExceptionInInitializerError | NoClassDefFoundError ignored) {
            return false;
        }
    }

    private static int intValue(ModePlayerValue value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return (int) Math.floor(Double.parseDouble(value.value()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double doubleValue(ModePlayerValue value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value.value());
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean booleanValue(ModePlayerValue value, boolean fallback) {
        if (value == null || value.value().isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.value());
    }

    private static String stringValue(ModePlayerValue value, String fallback) {
        if (value == null || value.value().isBlank()) {
            return fallback;
        }
        return value.value();
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "Player" : name;
    }

    private static double defaultSurvivorHealth(String lifeState, String connectionState) {
        if ("DEAD_SPECTATING".equals(lifeState) || "OFFLINE".equals(connectionState) || "LEFT".equals(connectionState)) {
            return 0.0D;
        }
        return 1.0D;
    }

    public record SurvivorStatus(
            UUID playerId,
            String name,
            String lifeState,
            String connectionState,
            int points,
            int totalEarnedPoints,
            int kills,
            int deaths,
            int barriersOpened,
            int armorLevel,
            double health,
            double maxHealth,
            boolean self
    ) {
    }

    public record ResultRow(
            UUID playerId,
            String name,
            String lifeState,
            String connectionState,
            int totalEarnedPoints,
            int kills,
            int barriersOpened,
            int deaths,
            boolean self
    ) {
        public boolean alive() {
            return !"LEFT".equals(connectionState) && "ALIVE".equals(lifeState);
        }
    }
}
