package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.DamageContext;
import com.cdp.codpattern.app.match.model.DamageDecision;
import com.cdp.codpattern.app.match.model.DeathContext;
import com.cdp.codpattern.app.match.model.DeathDecision;
import net.minecraft.server.level.ServerPlayer;

public interface ModeCombatEventPort extends ModeRoomIdentityPort {
    DamageDecision onPlayerHurt(ServerPlayer victim, DamageContext context);

    DeathDecision onPlayerDeath(ServerPlayer victim, DeathContext context);
}
