package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.service.WarmupMovementLockService;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class CodTdmRoundLifecycleCoordinator {
    private final CodTdmRoundLifecyclePort port;
    private final Set<UUID> phaseStartRespawningPlayers = new HashSet<>();

    CodTdmRoundLifecycleCoordinator(CodTdmRoundLifecyclePort port) {
        this.port = port;
    }

    void restoreAllRoomPlayersToAdventure() {
        port.getJoinedPlayers().forEach(player -> player.setGameMode(GameType.ADVENTURE));
        port.getSpectatorPlayers().forEach(player -> player.setGameMode(GameType.ADVENTURE));
    }

    void showCountdownActionBar(int secondsLeft) {
        port.getJoinedPlayers().forEach(player -> player.displayClientMessage(
                Component.translatable("hud.codpattern.tdm.actionbar.countdown", secondsLeft),
                true));
    }

    void clearCountdownActionBar() {
        port.getJoinedPlayers().forEach(player -> player.displayClientMessage(Component.empty(), true));
    }

    void lockWarmupMovement() {
        port.getJoinedPlayers().forEach(WarmupMovementLockService::lock);
    }

    void unlockAllRoomPlayersMovement() {
        port.getJoinedPlayers().forEach(WarmupMovementLockService::unlock);
        port.getSpectatorPlayers().forEach(WarmupMovementLockService::unlock);
    }

    void teleportAllPlayersToSpawn() {
        phaseStartRespawningPlayers.clear();
        for (String teamName : port.randomizeAllTeamSpawnsAndCollectMissingTeams()) {
            port.getJoinedPlayers().forEach(player -> player.sendSystemMessage(
                    Component.translatable("message.codpattern.game.warning_no_spawn", teamName)));
        }
        port.getJoinedPlayers().forEach(player -> {
            player.setGameMode(GameType.ADVENTURE);
            if (port.teleportPlayerToRoundStartPoint(player)) {
                return;
            }
            phaseStartRespawningPlayers.add(player.getUUID());
            port.clearPlayerInventory(player);
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.translatable("message.codpattern.game.warning_spawn_unusable"));
            port.scheduleRespawn(player);
        });
    }

    void giveAllPlayersKits() {
        port.getJoinedPlayers().forEach(player -> {
            if (phaseStartRespawningPlayers.contains(player.getUUID())) {
                return;
            }
            port.givePlayerKits(player);
        });
    }

    void giveAllPlayersKitsSilently() {
        port.getJoinedPlayers().forEach(player -> {
            if (phaseStartRespawningPlayers.contains(player.getUUID())) {
                return;
            }
            port.givePlayerKitsSilently(player);
        });
    }

    void clearAllPlayersInventory() {
        port.getJoinedPlayers().forEach(port::clearPlayerInventory);
    }
}
