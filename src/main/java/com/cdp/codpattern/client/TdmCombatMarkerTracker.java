package com.cdp.codpattern.client;

import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 客户端战斗标识追踪器：
 * - 每 tick 维护房间敌我关系快照
 * - 每 tick 维护敌方“瞄准触发血条”状态（30°视锥 + 20tick）
 */
public final class TdmCombatMarkerTracker {
    public static final TdmCombatMarkerTracker INSTANCE = new TdmCombatMarkerTracker();

    private static final boolean DEFAULT_ENEMY_MARKER_HEALTH_BAR = true;
    private static final float DEFAULT_ENEMY_FOCUS_HALF_ANGLE_DEGREES = 30.0f;
    private static final int DEFAULT_ENEMY_FOCUS_REQUIRED_TICKS = 20;
    private static final double DEFAULT_ENEMY_BAR_MAX_DISTANCE = 96.0D;
    private static final int DEFAULT_ENEMY_BAR_VISIBLE_GRACE_TICKS = 3;

    private static final float MIN_ENEMY_FOCUS_HALF_ANGLE_DEGREES = 5.0f;
    private static final float MAX_ENEMY_FOCUS_HALF_ANGLE_DEGREES = 80.0f;
    private static final int MIN_ENEMY_FOCUS_REQUIRED_TICKS = 1;
    private static final int MAX_ENEMY_FOCUS_REQUIRED_TICKS = 200;
    private static final double MIN_ENEMY_BAR_MAX_DISTANCE = 8.0D;
    private static final double MAX_ENEMY_BAR_MAX_DISTANCE = 256.0D;
    private static final int MIN_ENEMY_BAR_VISIBLE_GRACE_TICKS = 0;
    private static final int MAX_ENEMY_BAR_VISIBLE_GRACE_TICKS = 20;

    private final Map<UUID, Integer> enemyFocusTicks = new HashMap<>();
    private final Map<UUID, Integer> enemyBarGraceTicks = new HashMap<>();
    private final Set<UUID> visibleEnemyBars = new HashSet<>();

    private boolean enemyMarkerHealthBar = DEFAULT_ENEMY_MARKER_HEALTH_BAR;
    private float enemyFocusHalfAngleDegrees = DEFAULT_ENEMY_FOCUS_HALF_ANGLE_DEGREES;
    private int enemyFocusRequiredTicks = DEFAULT_ENEMY_FOCUS_REQUIRED_TICKS;
    private double enemyBarMaxDistance = DEFAULT_ENEMY_BAR_MAX_DISTANCE;
    private int enemyBarVisibleGraceTicks = DEFAULT_ENEMY_BAR_VISIBLE_GRACE_TICKS;
    private double enemyFocusCosThreshold = Math.cos(Math.toRadians(DEFAULT_ENEMY_FOCUS_HALF_ANGLE_DEGREES));

    private TeamVisionSnapshot latestSnapshot = TeamVisionSnapshot.empty();

    private TdmCombatMarkerTracker() {
    }

    public void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        ClientLevel level = minecraft.level;
        if (localPlayer == null || level == null) {
            clear();
            return;
        }

        latestSnapshot = buildSnapshot(localPlayer.getUUID(), ClientTdmState.teamPlayersSnapshot());

        if (!"PLAYING".equals(ClientTdmState.currentPhase()) || !latestSnapshot.hasLocalTeam()) {
            clearEnemyTrackingOnly();
            return;
        }

        Set<UUID> currentEnemies = latestSnapshot.enemyPlayers();
        enemyFocusTicks.keySet().removeIf(id -> !currentEnemies.contains(id));
        enemyBarGraceTicks.keySet().removeIf(id -> !currentEnemies.contains(id));
        visibleEnemyBars.clear();

        double maxDistanceSqr = enemyBarMaxDistance * enemyBarMaxDistance;
        for (UUID enemyId : currentEnemies) {
            if (!latestSnapshot.isLiving(enemyId)) {
                enemyFocusTicks.remove(enemyId);
                enemyBarGraceTicks.remove(enemyId);
                continue;
            }

            Player enemy = level.getPlayerByUUID(enemyId);
            if (enemy == null || !enemy.isAlive() || enemy.isRemoved()) {
                enemyFocusTicks.remove(enemyId);
                enemyBarGraceTicks.remove(enemyId);
                continue;
            }

            if (localPlayer.distanceToSqr(enemy) > maxDistanceSqr) {
                enemyFocusTicks.put(enemyId, 0);
                enemyBarGraceTicks.remove(enemyId);
                continue;
            }

            boolean visible = localPlayer.hasLineOfSight(enemy);
            boolean centerAim = isCenterAimedEnemy(minecraft, enemy);
            boolean inCone = isInsideEnemyFocusCone(localPlayer, enemy);

            int focusTicks = enemyFocusTicks.getOrDefault(enemyId, 0);
            if (visible && (centerAim || inCone)) {
                focusTicks++;
            } else {
                focusTicks = 0;
            }
            enemyFocusTicks.put(enemyId, focusTicks);

            int graceTicks = enemyBarGraceTicks.getOrDefault(enemyId, 0);
            boolean triggered = visible && (centerAim || focusTicks >= enemyFocusRequiredTicks);
            if (!visible) {
                graceTicks = 0;
            } else if (triggered) {
                graceTicks = enemyBarVisibleGraceTicks;
            } else if (graceTicks > 0) {
                graceTicks--;
            }

            if (graceTicks > 0) {
                enemyBarGraceTicks.put(enemyId, graceTicks);
            } else {
                enemyBarGraceTicks.remove(enemyId);
            }

            if (visible && (triggered || graceTicks > 0)) {
                visibleEnemyBars.add(enemyId);
            }
        }
    }

    public void updateConfig(boolean enemyMarkerHealthBar,
            float focusHalfAngleDegrees,
            int focusRequiredTicks,
            double barMaxDistance,
            int barVisibleGraceTicks) {
        this.enemyMarkerHealthBar = enemyMarkerHealthBar;
        this.enemyFocusHalfAngleDegrees = clampFloat(
                focusHalfAngleDegrees,
                MIN_ENEMY_FOCUS_HALF_ANGLE_DEGREES,
                MAX_ENEMY_FOCUS_HALF_ANGLE_DEGREES);
        this.enemyFocusRequiredTicks = clampInt(
                focusRequiredTicks,
                MIN_ENEMY_FOCUS_REQUIRED_TICKS,
                MAX_ENEMY_FOCUS_REQUIRED_TICKS);
        this.enemyBarMaxDistance = clampDouble(
                barMaxDistance,
                MIN_ENEMY_BAR_MAX_DISTANCE,
                MAX_ENEMY_BAR_MAX_DISTANCE);
        this.enemyBarVisibleGraceTicks = clampInt(
                barVisibleGraceTicks,
                MIN_ENEMY_BAR_VISIBLE_GRACE_TICKS,
                MAX_ENEMY_BAR_VISIBLE_GRACE_TICKS);
        this.enemyFocusCosThreshold = Math.cos(Math.toRadians(this.enemyFocusHalfAngleDegrees));
    }

    public TeamVisionSnapshot snapshot() {
        return latestSnapshot;
    }

    public boolean shouldRenderEnemyMarker(UUID playerId) {
        return playerId != null && visibleEnemyBars.contains(playerId);
    }

    public boolean shouldRenderEnemyHealthBar(UUID playerId) {
        return shouldRenderEnemyMarker(playerId);
    }

    public boolean isEnemyMarkerHealthBar() {
        return enemyMarkerHealthBar;
    }

    public void clear() {
        latestSnapshot = TeamVisionSnapshot.empty();
        clearEnemyTrackingOnly();
    }

    private void clearEnemyTrackingOnly() {
        enemyFocusTicks.clear();
        enemyBarGraceTicks.clear();
        visibleEnemyBars.clear();
    }

    private TeamVisionSnapshot buildSnapshot(UUID localPlayerId, Map<String, List<PlayerInfo>> teamPlayers) {
        if (localPlayerId == null || teamPlayers == null || teamPlayers.isEmpty()) {
            return TeamVisionSnapshot.empty();
        }

        Map<UUID, String> teamByPlayer = new HashMap<>();
        Set<UUID> livingPlayers = new HashSet<>();

        for (Map.Entry<String, List<PlayerInfo>> entry : teamPlayers.entrySet()) {
            String teamName = entry.getKey() == null ? "" : entry.getKey();
            List<PlayerInfo> players = entry.getValue();
            if (players == null || players.isEmpty()) {
                continue;
            }
            for (PlayerInfo info : players) {
                if (info == null || info.uuid() == null) {
                    continue;
                }
                teamByPlayer.put(info.uuid(), teamName);
                if (info.isAlive()) {
                    livingPlayers.add(info.uuid());
                }
            }
        }

        String localTeam = teamByPlayer.get(localPlayerId);
        if (localTeam == null) {
            return new TeamVisionSnapshot(localPlayerId, null, Map.copyOf(teamByPlayer), Set.copyOf(livingPlayers),
                    Set.of(), Set.of());
        }

        Set<UUID> teammates = new HashSet<>();
        Set<UUID> enemies = new HashSet<>();
        for (Map.Entry<UUID, String> entry : teamByPlayer.entrySet()) {
            UUID playerId = entry.getKey();
            if (localPlayerId.equals(playerId)) {
                continue;
            }
            if (localTeam.equals(entry.getValue())) {
                teammates.add(playerId);
            } else {
                enemies.add(playerId);
            }
        }

        return new TeamVisionSnapshot(localPlayerId, localTeam, Map.copyOf(teamByPlayer), Set.copyOf(livingPlayers),
                Set.copyOf(teammates), Set.copyOf(enemies));
    }

    private boolean isCenterAimedEnemy(Minecraft minecraft, Player enemy) {
        HitResult hitResult = minecraft.hitResult;
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return false;
        }
        return enemy.getUUID().equals(entityHitResult.getEntity().getUUID());
    }

    private boolean isInsideEnemyFocusCone(LocalPlayer localPlayer, Player enemy) {
        Vec3 toEnemy = enemy.getEyePosition().subtract(localPlayer.getEyePosition());
        double lengthSqr = toEnemy.lengthSqr();
        if (lengthSqr <= 1.0E-6) {
            return true;
        }
        Vec3 look = localPlayer.getLookAngle();
        double dot = look.dot(toEnemy.scale(1.0D / Math.sqrt(lengthSqr)));
        return dot >= enemyFocusCosThreshold;
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class TeamVisionSnapshot {
        private static final TeamVisionSnapshot EMPTY = new TeamVisionSnapshot(null, null, Map.of(), Set.of(), Set.of(),
                Set.of());

        private final UUID localPlayerId;
        private final String localTeam;
        private final Map<UUID, String> teamByPlayer;
        private final Set<UUID> livingPlayers;
        private final Set<UUID> teammates;
        private final Set<UUID> enemies;

        private TeamVisionSnapshot(UUID localPlayerId,
                String localTeam,
                Map<UUID, String> teamByPlayer,
                Set<UUID> livingPlayers,
                Set<UUID> teammates,
                Set<UUID> enemies) {
            this.localPlayerId = localPlayerId;
            this.localTeam = localTeam;
            this.teamByPlayer = teamByPlayer;
            this.livingPlayers = livingPlayers;
            this.teammates = teammates;
            this.enemies = enemies;
        }

        public static TeamVisionSnapshot empty() {
            return EMPTY;
        }

        public UUID localPlayerId() {
            return localPlayerId;
        }

        public String localTeam() {
            return localTeam;
        }

        public boolean hasLocalTeam() {
            return localTeam != null && !localTeam.isBlank();
        }

        public Map<UUID, String> teamByPlayer() {
            return teamByPlayer;
        }

        public boolean isLiving(UUID playerId) {
            return playerId != null && livingPlayers.contains(playerId);
        }

        public boolean isTeammate(UUID playerId) {
            return playerId != null && teammates.contains(playerId);
        }

        public boolean isEnemy(UUID playerId) {
            return playerId != null && enemies.contains(playerId);
        }

        public Set<UUID> enemyPlayers() {
            return enemies;
        }
    }
}
