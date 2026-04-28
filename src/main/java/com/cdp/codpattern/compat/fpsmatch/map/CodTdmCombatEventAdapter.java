package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.match.model.DamageContext;
import com.cdp.codpattern.app.match.model.DamageDecision;
import com.cdp.codpattern.app.match.model.DeathContext;
import com.cdp.codpattern.app.match.model.DeathDecision;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeCombatEventPort;
import com.cdp.codpattern.app.match.port.ModeRoomActionPort;
import com.cdp.codpattern.app.match.port.ModeRoomReadPort;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

final class CodTdmCombatEventAdapter implements ModeCombatEventPort {
    private final ModeRoomReadPort readPort;
    private final ModeRoomActionPort actionPort;

    CodTdmCombatEventAdapter(ModeRoomReadPort readPort, ModeRoomActionPort actionPort) {
        this.readPort = readPort;
        this.actionPort = actionPort;
    }

    @Override
    public RoomId roomId() {
        return readPort.roomId();
    }

    @Override
    public String gameType() {
        return readPort.gameType();
    }

    @Override
    public String mapName() {
        return readPort.mapName();
    }

    @Override
    public String modeDisplayNameKey() {
        return readPort.modeDisplayNameKey();
    }

    @Override
    public DamageDecision onPlayerHurt(ServerPlayer victim, DamageContext context) {
        if (victim == null || context == null) {
            return DamageDecision.passThrough();
        }

        if (!readPort.isStarted()) {
            return DamageDecision.cancel();
        }

        if (readPort.isPlayerInvincible(victim.getUUID())) {
            return DamageDecision.cancel();
        }

        ServerPlayer attacker = context.attacker().orElse(null);
        if (isTeammate(attacker, victim)) {
            return DamageDecision.cancel();
        }

        if (!readPort.canDealDamage()) {
            return DamageDecision.setAmount(0.0F);
        }

        if (context.amount() > 0.0F) {
            actionPort.onPlayerDamaged(victim);
        }
        return DamageDecision.passThrough();
    }

    @Override
    public DeathDecision onPlayerDeath(ServerPlayer victim, DeathContext context) {
        if (victim == null || context == null) {
            return DeathDecision.passThrough();
        }

        if (!readPort.isStarted()) {
            return DeathDecision.passThrough();
        }

        ServerPlayer killer = context.killer().orElse(null);
        if (killer != null
                && !killer.getUUID().equals(victim.getUUID())
                && !isTeammate(killer, victim)) {
            actionPort.onPlayerKill(killer, victim);
        }

        actionPort.onPlayerDead(victim, killer);
        return DeathDecision.cancelAndRestoreHealth();
    }

    private boolean isTeammate(ServerPlayer attacker, ServerPlayer victim) {
        if (attacker == null || victim == null) {
            return false;
        }
        if (attacker.getUUID().equals(victim.getUUID())) {
            return false;
        }
        Optional<String> attackerTeam = readPort.findTeamNameByPlayer(attacker);
        Optional<String> victimTeam = readPort.findTeamNameByPlayer(victim);
        return attackerTeam.isPresent() && attackerTeam.equals(victimTeam);
    }
}
