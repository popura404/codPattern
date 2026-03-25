package com.cdp.codpattern.compat.fpsmatch.map;

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

    void clearAllPlayersInventory() {
        port.getJoinedPlayers().forEach(port::clearPlayerInventory);
    }
}
