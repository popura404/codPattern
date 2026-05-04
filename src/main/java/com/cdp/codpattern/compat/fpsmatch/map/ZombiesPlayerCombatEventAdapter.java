package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.match.model.DamageContext;
import com.cdp.codpattern.app.match.model.DamageDecision;
import com.cdp.codpattern.app.match.model.DeathContext;
import com.cdp.codpattern.app.match.model.DeathDecision;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeCombatEventPort;
import com.cdp.codpattern.app.zombies.service.ZombiesBuffCombatService;
import com.cdp.codpattern.app.zombies.service.ZombiesDeathService;
import com.cdp.codpattern.app.match.GameModeRegistry;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;

public class ZombiesPlayerCombatEventAdapter implements ModeCombatEventPort {
    private final RoomId roomId;
    private final String modeDisplayNameKey;
    private final ZombiesDeathService deathService;
    private final RoundState roundState;
    private final ZombiesBuffCombatService buffCombatService;

    public ZombiesPlayerCombatEventAdapter(RoomId roomId, String modeDisplayNameKey, ZombiesDeathService deathService, RoundState roundState) {
        this(roomId, modeDisplayNameKey, deathService, roundState, null);
    }

    public ZombiesPlayerCombatEventAdapter(
            RoomId roomId,
            String modeDisplayNameKey,
            ZombiesDeathService deathService,
            RoundState roundState,
            ZombiesBuffCombatService buffCombatService
    ) {
        this.roomId = Objects.requireNonNull(roomId, "roomId");
        this.modeDisplayNameKey = modeDisplayNameKey == null || modeDisplayNameKey.isBlank()
                ? GameModeRegistry.getOrDefault(roomId.gameType()).displayNameKey()
                : modeDisplayNameKey;
        this.deathService = Objects.requireNonNull(deathService, "deathService");
        this.roundState = roundState == null ? RoundState.started() : roundState;
        this.buffCombatService = buffCombatService;
        if (buffCombatService != null) {
            ZombiesBuffCombatService.register(buffCombatService);
        }
    }

    @Override
    public RoomId roomId() {
        return roomId;
    }

    @Override
    public String gameType() {
        return GameModeRegistry.canonicalize(roomId.gameType());
    }

    @Override
    public String mapName() {
        return roomId.mapName();
    }

    @Override
    public String modeDisplayNameKey() {
        return modeDisplayNameKey;
    }

    @Override
    public DamageDecision onPlayerHurt(ServerPlayer victim, DamageContext context) {
        if (victim == null || context == null) {
            return DamageDecision.passThrough();
        }
        if (!roundState.isStarted()) {
            return DamageDecision.cancel();
        }
        if (!deathService.canPlayerAct(victim.getUUID())) {
            return DamageDecision.cancel();
        }
        if (context.attacker().isPresent() && !deathService.canPlayerAct(context.attacker().get().getUUID())) {
            return DamageDecision.cancel();
        }
        if (context.attacker().isPresent() && isFriendlySurvivorDamage(victim, context.attacker().get())) {
            return DamageDecision.cancel();
        }
        Optional<ZombiesBuffCombatService> service = buffCombatService == null
                ? ZombiesBuffCombatService.serviceFor(roomId)
                : Optional.of(buffCombatService);
        if (service.isEmpty()) {
            return DamageDecision.passThrough();
        }
        ZombiesBuffCombatService.DamageApplicationResult damageResult =
                service.get().applyPlayerDamage(victim, context, roundState.currentTick());
        if (!damageResult.roomMonsterDamage()) {
            return DamageDecision.passThrough();
        }
        if (damageResult.amountChanged()) {
            return DamageDecision.setAmount(damageResult.adjustedAmount());
        }
        return DamageDecision.passThrough();
    }

    @Override
    public DeathDecision onPlayerDeath(ServerPlayer victim, DeathContext context) {
        if (victim == null || context == null || !roundState.isStarted()) {
            return DeathDecision.passThrough();
        }
        deathService.markPlayerDeadSpectating(victim, context, roundState.currentTick());
        return DeathDecision.cancelAndRestoreHealth();
    }

    private boolean isFriendlySurvivorDamage(ServerPlayer victim, ServerPlayer attacker) {
        if (victim == null || attacker == null || victim.getUUID().equals(attacker.getUUID())) {
            return false;
        }
        return deathService.canPlayerAct(victim.getUUID()) && deathService.canPlayerAct(attacker.getUUID());
    }

    public interface RoundState {
        boolean isStarted();

        default long currentTick() {
            return 0L;
        }

        static RoundState started() {
            return () -> true;
        }
    }
}
