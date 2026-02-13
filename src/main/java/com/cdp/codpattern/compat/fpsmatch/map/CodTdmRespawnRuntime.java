package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.service.DeathCamService;
import com.cdp.codpattern.app.tdm.service.RespawnService;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class CodTdmRespawnRuntime {
    private final CodTdmPlayerRuntimeState playerState;
    private final Supplier<ServerLevel> serverLevelSupplier;
    private final Consumer<ServerPlayer> teleportPlayerToRespawnAction;
    private final Consumer<ServerPlayer> givePlayerKitsAction;

    CodTdmRespawnRuntime(
            CodTdmPlayerRuntimeState playerState,
            Supplier<ServerLevel> serverLevelSupplier,
            Consumer<ServerPlayer> teleportPlayerToRespawnAction,
            Consumer<ServerPlayer> givePlayerKitsAction
    ) {
        this.playerState = playerState;
        this.serverLevelSupplier = serverLevelSupplier;
        this.teleportPlayerToRespawnAction = teleportPlayerToRespawnAction;
        this.givePlayerKitsAction = givePlayerKitsAction;
    }

    void tickDeathCam() {
        DeathCamService.tickDeathCam(playerState.deathCamPlayers(), serverLevelSupplier.get());
    }

    void scheduleRespawn(ServerPlayer player) {
        RespawnService.scheduleRespawn(
                playerState.respawnTimers(),
                player,
                CodTdmConfig.getConfig().getRespawnDelayTicks()
        );
    }

    void tickRespawn() {
        RespawnService.tickRespawn(playerState.respawnTimers(), serverLevelSupplier.get(), this::respawnPlayer);
    }

    void tickInvincibility() {
        RespawnService.tickInvincibility(playerState.invincibilityTimers(), playerState.invinciblePlayers());
    }

    boolean isInvincible(UUID playerId) {
        return playerState.isInvincible(playerId);
    }

    private void respawnPlayer(ServerPlayer player) {
        RespawnService.respawnPlayer(
                player,
                teleportPlayerToRespawnAction,
                givePlayerKitsAction,
                CodTdmConfig.getConfig().getInvincibilityTicks(),
                playerState.invinciblePlayers(),
                playerState.invincibilityTimers()
        );
    }
}
