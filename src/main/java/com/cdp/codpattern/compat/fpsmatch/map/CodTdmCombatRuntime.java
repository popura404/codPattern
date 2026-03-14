package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.service.CombatRegenService;
import com.cdp.codpattern.app.tdm.model.TdmGamePhase;
import com.cdp.codpattern.app.tdm.service.KillFeedService;
import com.cdp.codpattern.app.tdm.service.PlayerDeathService;
import com.cdp.codpattern.app.tdm.service.ScoreService;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.Supplier;

final class CodTdmCombatRuntime {
    private final CodTdmMatchRuntimeState matchState;
    private final CodTdmPlayerRuntimeState playerState;
    private final ScoreService.Hooks scoreHooks;
    private final KillFeedService.Hooks killFeedHooks;
    private final PlayerDeathService.Hooks playerDeathHooks;
    private final Supplier<List<ServerPlayer>> joinedPlayersSupplier;

    CodTdmCombatRuntime(
            CodTdmMatchRuntimeState matchState,
            CodTdmPlayerRuntimeState playerState,
            ScoreService.Hooks scoreHooks,
            KillFeedService.Hooks killFeedHooks,
            PlayerDeathService.Hooks playerDeathHooks,
            Supplier<List<ServerPlayer>> joinedPlayersSupplier
    ) {
        this.matchState = matchState;
        this.playerState = playerState;
        this.scoreHooks = scoreHooks;
        this.killFeedHooks = killFeedHooks;
        this.playerDeathHooks = playerDeathHooks;
        this.joinedPlayersSupplier = joinedPlayersSupplier;
    }

    boolean canDealDamage() {
        return matchState.phase() == TdmGamePhase.PLAYING;
    }

    void onPlayerKill(ServerPlayer killer, ServerPlayer victim) {
        ScoreService.onPlayerKill(
                killer,
                victim,
                matchState.phase(),
                playerState.playerKills(),
                playerState.playerDeaths(),
                playerState.currentKillStreaks(),
                playerState.maxKillStreaks(),
                matchState.teamScores(),
                matchState.gameTimeTicks(),
                scoreHooks
        );
    }

    void onPlayerDamaged(ServerPlayer player) {
        CombatRegenService.onPlayerDamaged(
                playerState.combatRegenCooldowns(),
                player,
                CodTdmConfig.getConfig()
        );
    }

    void onPlayerDead(ServerPlayer player, ServerPlayer killer) {
        CombatRegenService.clearPlayerCooldown(playerState.combatRegenCooldowns(), player.getUUID());
        ScoreService.onNonPlayerDeath(
                player,
                killer,
                matchState.phase(),
                playerState.playerDeaths(),
                playerState.currentKillStreaks(),
                matchState.teamScores(),
                matchState.gameTimeTicks(),
                scoreHooks
        );
        KillFeedService.broadcast(player, killer, matchState.phase(), killFeedHooks);
        PlayerDeathService.onPlayerDead(player, killer, CodTdmConfig.getConfig(), playerDeathHooks);
    }

    void tickCombatRegen() {
        CombatRegenService.tick(
                playerState.combatRegenCooldowns(),
                joinedPlayersSupplier.get(),
                playerState.respawnTimers().keySet(),
                matchState.phase() == TdmGamePhase.PLAYING,
                CodTdmConfig.getConfig()
        );
    }
}
