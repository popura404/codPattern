package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.DeathContext;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns MVP zombies player death state transitions. Room failure, HUD sync and
 * equipment snapshots are intentionally delegated to hooks supplied by the map
 * integration layer.
 */
public class ZombiesDeathService {
    public static final String FAILURE_REASON_SINGLE_PLAYER_DEATH = "single_player_death";
    public static final String FAILURE_REASON_ALL_SURVIVORS_DOWN = "all_survivors_down";

    private final ZombiesPlayerStateService playerStateService;
    private final long offlineGraceTicks;
    private final Hooks hooks;

    public ZombiesDeathService(ZombiesPlayerStateService playerStateService, long offlineGraceTicks, Hooks hooks) {
        this.playerStateService = Objects.requireNonNull(playerStateService, "playerStateService");
        this.offlineGraceTicks = Math.max(0L, offlineGraceTicks);
        this.hooks = hooks == null ? Hooks.noop() : hooks;
    }

    public DeathOutcome markPlayerDeadSpectating(ServerPlayer victim, DeathContext context, long currentTick) {
        if (victim == null) {
            return DeathOutcome.ignored();
        }

        UUID victimId = victim.getUUID();
        ZombiesPlayerRuntimeState state = playerStateService.getOrCreate(victimId);
        boolean wasAlreadyDead = !state.lifeState().isAlive();
        playerStateService.updateLastAliveTargetPos(victimId, victim.blockPosition());
        playerStateService.markDeadSpectating(victimId);
        hooks.captureDeathEquipment(victim);
        hooks.onPlayerDeath(victim, context == null ? Optional.empty() : context.killer());
        enterSpectator(victim);

        boolean failed = false;
        String reason = "";
        if (!wasAlreadyDead) {
            int memberCount = Math.max(0, hooks.activeMemberCount());
            if (memberCount <= 1) {
                failed = true;
                reason = FAILURE_REASON_SINGLE_PLAYER_DEATH;
            } else if (!playerStateService.hasAnyOnlineAlive()) {
                failed = true;
                reason = FAILURE_REASON_ALL_SURVIVORS_DOWN;
            }
            if (failed) {
                hooks.onRoundFailed(reason);
            }
        }

        return new DeathOutcome(true, wasAlreadyDead, failed, reason);
    }

    public boolean canPlayerAct(UUID playerId) {
        return playerId != null && playerStateService.canInteract(playerId);
    }

    private static void enterSpectator(ServerPlayer player) {
        if (player != null && !player.gameMode.getGameModeForPlayer().isCreative()) {
            player.setGameMode(GameType.SPECTATOR);
        }
    }

    public interface Hooks {
        default int activeMemberCount() {
            return 0;
        }

        default void captureDeathEquipment(ServerPlayer victim) {
        }

        default void onPlayerDeath(ServerPlayer victim, Optional<ServerPlayer> killer) {
        }

        default void onRoundFailed(String reason) {
        }

        static Hooks noop() {
            return new Hooks() {
            };
        }
    }

    public record DeathOutcome(
            boolean handled,
            boolean alreadyDead,
            boolean roundFailed,
            String failureReason
    ) {
        public DeathOutcome {
            failureReason = failureReason == null ? "" : failureReason;
        }

        public static DeathOutcome ignored() {
            return new DeathOutcome(false, false, false, "");
        }
    }
}
