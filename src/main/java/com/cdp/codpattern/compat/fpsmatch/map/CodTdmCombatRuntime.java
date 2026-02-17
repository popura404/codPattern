package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;
import com.cdp.codpattern.app.tdm.service.PlayerDeathService;
import com.cdp.codpattern.app.tdm.service.ScoreService;
import com.cdp.codpattern.config.tdm.CodTdmConfig;
import net.minecraft.server.level.ServerPlayer;

final class CodTdmCombatRuntime {
    private final CodTdmMatchRuntimeState matchState;
    private final CodTdmPlayerRuntimeState playerState;
    private final ScoreService.Hooks scoreHooks;
    private final PlayerDeathService.Hooks playerDeathHooks;

    CodTdmCombatRuntime(
            CodTdmMatchRuntimeState matchState,
            CodTdmPlayerRuntimeState playerState,
            ScoreService.Hooks scoreHooks,
            PlayerDeathService.Hooks playerDeathHooks
    ) {
        this.matchState = matchState;
        this.playerState = playerState;
        this.scoreHooks = scoreHooks;
        this.playerDeathHooks = playerDeathHooks;
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

    void onPlayerDead(ServerPlayer player, ServerPlayer killer) {
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
        PlayerDeathService.onPlayerDead(player, killer, CodTdmConfig.getConfig(), playerDeathHooks);
    }
}
