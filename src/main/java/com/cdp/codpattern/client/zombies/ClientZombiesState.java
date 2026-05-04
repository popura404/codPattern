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

    public static List<SurvivorStatus> survivors() {
        Optional<ModeRuntimeStateSnapshot> snapshotOptional = snapshot();
        if (snapshotOptional.isEmpty()) {
            return List.of();
        }
        ModeRuntimeStateSnapshot snapshot = snapshotOptional.get();
        Map<String, List<PlayerInfo>> rosters = ClientMatchState.teamPlayersSnapshot();
        List<PlayerInfo> survivorRoster = rosters.getOrDefault(ZombiesTeamNames.SURVIVORS, List.of());
        if (survivorRoster.isEmpty()) {
            return List.of();
        }

        UUID localPlayerId = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        List<SurvivorStatus> statuses = new ArrayList<>();
        for (PlayerInfo playerInfo : survivorRoster) {
            UUID playerId = playerInfo.uuid();
            String prefix = "survivor." + playerId + ".";
            String lifeState = stringValue(snapshot.playerValues().get(prefix + ZombiesRuntimeStateKeys.PLAYER_LIFE_STATE), playerInfo.isAlive() ? "ALIVE" : "DEAD_SPECTATING");
            String connectionState = stringValue(snapshot.playerValues().get(prefix + ZombiesRuntimeStateKeys.PLAYER_CONNECTION_STATE), "");
            int points = intValue(snapshot.playerValues().get(prefix + ZombiesRuntimeStateKeys.PLAYER_POINTS), 0);
            statuses.add(new SurvivorStatus(
                    playerId,
                    playerInfo.name(),
                    lifeState,
                    connectionState,
                    points,
                    playerId != null && playerId.equals(localPlayerId)));
        }
        return List.copyOf(statuses);
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

    public record SurvivorStatus(
            UUID playerId,
            String name,
            String lifeState,
            String connectionState,
            int points,
            boolean self
    ) {
    }
}
