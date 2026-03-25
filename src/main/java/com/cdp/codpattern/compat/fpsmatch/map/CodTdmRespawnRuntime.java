package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.tdm.service.CombatRegenService;
import com.cdp.codpattern.app.tdm.service.DeathCamService;
import com.cdp.codpattern.app.tdm.service.RespawnService;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import com.cdp.codpattern.network.tdm.DeathCamPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class CodTdmRespawnRuntime {
    private final CodTdmPlayerRuntimeState playerState;
    private final Supplier<ServerLevel> serverLevelSupplier;
    private final Predicate<ServerPlayer> teleportPlayerToRespawnAction;
    private final java.util.function.Consumer<ServerPlayer> givePlayerKitsAction;
    private final Supplier<Integer> respawnRetryTicksSupplier;
    private final Supplier<Integer> invincibilityTicksSupplier;

    CodTdmRespawnRuntime(
            CodTdmPlayerRuntimeState playerState,
            Supplier<ServerLevel> serverLevelSupplier,
            Predicate<ServerPlayer> teleportPlayerToRespawnAction,
            java.util.function.Consumer<ServerPlayer> givePlayerKitsAction,
            Supplier<Integer> respawnRetryTicksSupplier,
            Supplier<Integer> invincibilityTicksSupplier
    ) {
        this.playerState = playerState;
        this.serverLevelSupplier = serverLevelSupplier;
        this.teleportPlayerToRespawnAction = teleportPlayerToRespawnAction;
        this.givePlayerKitsAction = givePlayerKitsAction;
        this.respawnRetryTicksSupplier = respawnRetryTicksSupplier;
        this.invincibilityTicksSupplier = invincibilityTicksSupplier;
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
        RespawnService.tickRespawn(
                playerState.respawnTimers(),
                serverLevelSupplier.get(),
                this::respawnPlayer,
                respawnRetryTicksSupplier.get(),
                this::notifyRespawnRetry
        );
    }

    void tickInvincibility() {
        RespawnService.tickInvincibility(playerState.invincibilityTimers(), playerState.invinciblePlayers());
    }

    boolean isInvincible(UUID playerId) {
        return playerState.isInvincible(playerId);
    }

    boolean respawnPlayerNow(ServerPlayer player) {
        return respawnPlayer(player);
    }

    private boolean respawnPlayer(ServerPlayer player) {
        CombatRegenService.clearPlayerCooldown(playerState.combatRegenCooldowns(), player.getUUID());
        return RespawnService.respawnPlayer(
                player,
                teleportPlayerToRespawnAction,
                givePlayerKitsAction,
                invincibilityTicksSupplier.get(),
                playerState.invinciblePlayers(),
                playerState.invincibilityTimers()
        );
    }

    private void notifyRespawnRetry(ServerPlayer player, int retryTicks) {
        if (player == null || retryTicks <= 0) {
            return;
        }
        ModNetworkChannel.sendToPlayer(new DeathCamPacket(null, "", 0, retryTicks), player);
    }
}
