package com.cdp.codpattern.compat.fpsmatch.map;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

final class CodTdmRoundLifecycleCoordinator {
    private final CodTdmRoundLifecyclePort port;

    CodTdmRoundLifecycleCoordinator(CodTdmRoundLifecyclePort port) {
        this.port = port;
    }

    void restoreAllRoomPlayersToAdventure() {
        port.getJoinedPlayers().forEach(player -> player.setGameMode(GameType.ADVENTURE));
        port.getSpectatorPlayers().forEach(player -> player.setGameMode(GameType.ADVENTURE));
    }

    void teleportAllPlayersToSpawn() {
        for (String teamName : port.randomizeAllTeamSpawnsAndCollectMissingTeams()) {
            port.getJoinedPlayers().forEach(player -> player.sendSystemMessage(
                    Component.translatable("message.codpattern.game.warning_no_spawn", teamName)));
        }
        port.getJoinedPlayers().forEach(port::teleportPlayerToReSpawnPoint);
    }

    void giveAllPlayersKits() {
        port.getJoinedPlayers().forEach(port::givePlayerKits);
    }

    void clearAllPlayersInventory() {
        port.getJoinedPlayers().forEach(port::clearPlayerInventory);
    }
}
