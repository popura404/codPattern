package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.data.SpawnSelectionReason;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public abstract class BaseMap {
    public final String mapName;
    public boolean isStart = false;
    public final AreaData mapArea;

    private final ServerLevel serverLevel;
    private final MapTeams mapTeams;

    public BaseMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        this.serverLevel = serverLevel;
        this.mapName = mapName;
        this.mapArea = areaData;
        this.mapTeams = new MapTeams(serverLevel, this);
    }

    public BaseTeam addTeam(String teamName, int playerLimit) {
        return mapTeams.addTeam(teamName, playerLimit);
    }

    public BaseTeam getSpectatorTeam() {
        return mapTeams.getSpectatorTeam();
    }

    public MapTeams getMapTeams() {
        return mapTeams;
    }

    public final void mapTick() {
        if (victoryGoal()) {
            victory();
        }
        tick();
        syncToClient();
    }

    public boolean checkGameHasPlayer(Player player) {
        return checkGameHasPlayer(player.getUUID());
    }

    public boolean checkGameHasPlayer(UUID playerId) {
        return mapTeams.getJoinedUUID().contains(playerId);
    }

    public boolean checkSpecHasPlayer(Player player) {
        return mapTeams.getSpecPlayers().contains(player.getUUID());
    }

    public void join(ServerPlayer player) {
        List<BaseTeam> teams = mapTeams.getTeams();
        teams.stream()
                .min(Comparator.comparingInt(BaseTeam::getPlayerCount))
                .ifPresent(team -> join(team.name, player));
    }

    public void join(String teamName, ServerPlayer player) {
        FPSMCore.checkAndLeaveTeam(player);
        player.setGameMode(GameType.ADVENTURE);
        mapTeams.joinTeam(teamName, player);
    }

    public void joinSpec(ServerPlayer player) {
        FPSMCore.checkAndLeaveTeam(player);
        player.setGameMode(GameType.SPECTATOR);
        mapTeams.leaveTeam(player);
        mapTeams.getSpectatorTeam().join(player);
        mapTeams.getSpectatorTeam().getSpawnPointsData(SpawnPointKind.INITIAL).stream().findFirst()
                .ifPresent(point -> teleportToPoint(player, point));
    }

    public void leave(ServerPlayer player) {
        mapTeams.leaveTeam(player);
        player.setGameMode(GameType.ADVENTURE);
    }

    public boolean teleportPlayerToRoundStartPoint(ServerPlayer player) {
        return teleportPlayerToSpawnPoint(player, SpawnSelectionReason.ROUND_START);
    }

    public boolean teleportPlayerToReSpawnPoint(ServerPlayer player) {
        return teleportPlayerToSpawnPoint(player, SpawnSelectionReason.RESPAWN);
    }

    public boolean teleportPlayerToMidMatchJoinPoint(ServerPlayer player) {
        return teleportPlayerToSpawnPoint(player, SpawnSelectionReason.MID_MATCH_JOIN);
    }

    public boolean teleportPlayerToSpawnPoint(ServerPlayer player, SpawnSelectionReason reason) {
        return teleportPlayerToSpawnPoint(player, resolveSpawnPointKind(reason));
    }

    protected boolean teleportPlayerToSpawnPoint(ServerPlayer player, SpawnPointKind pointKind) {
        if (player == null) {
            return false;
        }
        return mapTeams.getTeamByPlayer(player).flatMap(team ->
                team.getPlayerData(player.getUUID()).map(playerData -> {
                    int maxAttempts = Math.max(1, new LinkedHashSet<>(team.getSpawnPointsData(pointKind)).size() + 1);
                    Set<SpawnPointData> attemptedPoints = new LinkedHashSet<>();
                    while (attemptedPoints.size() < maxAttempts) {
                        SpawnPointData currentPoint = pointKind == SpawnPointKind.INITIAL
                                ? playerData.getSpawnPointsData()
                                : null;
                        if (currentPoint != null && attemptedPoints.contains(currentPoint)) {
                            currentPoint = null;
                        }
                        if (currentPoint == null) {
                            currentPoint = resolveSpawnPointForTeleport(player, team, pointKind, attemptedPoints)
                                    .orElse(null);
                        }
                        if (currentPoint == null) {
                            if (pointKind == SpawnPointKind.INITIAL) {
                                playerData.setSpawnPointsData(null);
                            }
                            return false;
                        }

                        attemptedPoints.add(currentPoint);
                        if (!teleportToPoint(player, currentPoint)) {
                            if (pointKind == SpawnPointKind.INITIAL
                                    && Objects.equals(playerData.getSpawnPointsData(), currentPoint)) {
                                playerData.setSpawnPointsData(null);
                            }
                            continue;
                        }
                        player.setRespawnPosition(currentPoint.getDimension(), currentPoint.getPosition(),
                                currentPoint.getYaw(), true, false);
                        team.markSpawnUsed(player.getUUID(), currentPoint);
                        if (pointKind == SpawnPointKind.INITIAL) {
                            team.assignNextSpawnPoint(player.getUUID(), SpawnPointKind.INITIAL, attemptedPoints);
                        }
                        return true;
                    }
                    if (pointKind == SpawnPointKind.INITIAL) {
                        playerData.setSpawnPointsData(null);
                    }
                    return false;
                })).orElse(false);
    }

    private Optional<SpawnPointData> resolveSpawnPointForTeleport(ServerPlayer player, BaseTeam team,
            SpawnPointKind pointKind, Set<SpawnPointData> attemptedPoints) {
        if (pointKind == SpawnPointKind.DYNAMIC_CANDIDATE) {
            return DynamicRespawnSelector.selectBestSpawnPoint(player, team, mapTeams, attemptedPoints);
        }
        return team.assignNextSpawnPoint(player.getUUID(), pointKind, attemptedPoints);
    }

    public boolean teleportToPoint(ServerPlayer player, SpawnPointData data) {
        if (!Level.isInSpawnableBounds(data.getPosition())) {
            return false;
        }
        ServerLevel targetLevel = serverLevel.getServer().getLevel(data.getDimension());
        if (targetLevel == null) {
            return false;
        }
        if (!SpawnSafetyValidator.isSafe(targetLevel, player, data)) {
            return false;
        }
        player.teleportTo(
                targetLevel,
                data.getX() + 0.5D,
                data.getY(),
                data.getZ() + 0.5D,
                data.getYaw(),
                data.getPitch()
        );
        player.setDeltaMovement(player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
        player.setOnGround(true);
        return true;
    }

    protected SpawnPointKind resolveSpawnPointKind(SpawnSelectionReason reason) {
        return SpawnPointKind.INITIAL;
    }

    public void clearPlayerInventory(UUID uuid, Predicate<ItemStack> inventoryPredicate) {
        Player player = serverLevel.getPlayerByUUID(uuid);
        if (player instanceof ServerPlayer serverPlayer) {
            clearPlayerInventory(serverPlayer, inventoryPredicate);
        }
    }

    public void clearPlayerInventory(ServerPlayer player, Predicate<ItemStack> predicate) {
        player.getInventory().clearOrCountMatchingItems(predicate, -1, (Container) player.inventoryMenu.getCraftSlots());
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
    }

    public void clearPlayerInventory(ServerPlayer player) {
        clearPlayerInventory(player, itemStack -> true);
    }

    public void givePlayerKits(ServerPlayer player) {
    }

    public ServerLevel getServerLevel() {
        return serverLevel;
    }

    public String getMapName() {
        return mapName;
    }

    public AreaData getMapArea() {
        return mapArea;
    }

    public void tick() {
    }

    public abstract void syncToClient();

    public abstract void startGame();

    public abstract void victory();

    public abstract boolean victoryGoal();

    public abstract void resetGame();

    public abstract String getGameType();

    @SubscribeEvent
    public static void onPlayerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && FPSMCore.initialized()) {
            FPSMCore.checkAndLeaveTeam(player);
        }
    }
}
