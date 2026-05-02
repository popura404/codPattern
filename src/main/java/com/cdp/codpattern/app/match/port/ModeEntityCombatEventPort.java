package com.cdp.codpattern.app.match.port;

import com.cdp.codpattern.app.match.model.DamageDecision;
import com.cdp.codpattern.app.match.model.DeathDecision;
import com.cdp.codpattern.app.match.model.EntityDamageContext;
import com.cdp.codpattern.app.match.model.EntityDeathContext;
import net.minecraft.world.entity.LivingEntity;

public interface ModeEntityCombatEventPort extends ModeRoomIdentityPort {
    DamageDecision onEntityHurt(LivingEntity entity, EntityDamageContext context);

    DeathDecision onEntityDeath(LivingEntity entity, EntityDeathContext context);
}
