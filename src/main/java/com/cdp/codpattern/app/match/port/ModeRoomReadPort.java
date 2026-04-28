package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.MetricDisplay;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.model.RoomSummaryMetric;
import com.cdp.codpattern.app.match.model.TeamDescriptor;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ModeRoomReadPort extends ModeRoomSummaryPort {
    @Override
    RoomId roomId();

    @Override
    String gameType();

    @Override
    String modeDisplayNameKey();

    List<TeamDescriptor> teamDescriptors();

    @Override
    String mapName();

    boolean containsJoinedPlayer(UUID playerId);

    boolean containsSpectator(ServerPlayer player);

    boolean isStarted();

    String phaseName();

    boolean isPlayingPhase();

    boolean isWaitingPhase();

    boolean canDealDamage();

    boolean isPlayerInvincible(UUID playerId);

    boolean hasMatchEndTeleportPoint();

    int getRemainingTimeTicks();

    Map<String, Integer> getTeamScoresSnapshot();

    Map<String, Integer> getTeamPlayerCountsSnapshot();

    int getMaxPlayerCapacity();

    boolean hasTeam(String teamName);

    boolean isTeamFull(String teamName);

    Optional<String> findTeamNameByPlayer(ServerPlayer player);

    Optional<String> chooseAutoJoinTeam(int maxTeamDiff);

    boolean canJoinWithBalance(String teamName, int maxTeamDiff);

    AreaData mapArea();

    String dimensionId();

    Optional<SpawnPointData> matchEndTeleportPoint();

    @Override
    default String lifecycleStateKey() {
        return phaseName();
    }

    @Override
    default boolean isJoinable() {
        return isWaitingPhase();
    }

    @Override
    default boolean isRunning() {
        return isStarted();
    }

    @Override
    default int playerCount() {
        return getTeamPlayerCountsSnapshot().values().stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    default int maxPlayers() {
        return getMaxPlayerCapacity();
    }

    @Override
    default int remainingTimeTicks() {
        return getRemainingTimeTicks();
    }

    @Override
    default List<RoomSummaryMetric> metrics() {
        Map<String, Integer> teamScores = getTeamScoresSnapshot();
        if (teamScores.isEmpty()) {
            return List.of();
        }

        List<RoomSummaryMetric> metrics = new ArrayList<>();
        for (TeamDescriptor descriptor : teamDescriptors()) {
            Integer score = teamScores.get(descriptor.teamName());
            if (score != null) {
                metrics.add(new RoomSummaryMetric(
                        "team_score." + descriptor.teamName(),
                        descriptor.shortNameKey(),
                        score,
                        MetricDisplay.SCORE));
            }
        }
        for (Map.Entry<String, Integer> entry : teamScores.entrySet()) {
            boolean alreadyAdded = metrics.stream()
                    .anyMatch(metric -> metric.key().equals("team_score." + entry.getKey()));
            if (!alreadyAdded) {
                metrics.add(new RoomSummaryMetric(
                        "team_score." + entry.getKey(),
                        entry.getKey(),
                        entry.getValue(),
                        MetricDisplay.SCORE));
            }
        }
        return List.copyOf(metrics);
    }
}
