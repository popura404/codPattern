package com.cdp.codpattern.app.match.model;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public record DamageContext(
        DamageSource source,
        Entity directEntity,
        Optional<ServerPlayer> attacker,
        float amount
) {
    public DamageContext {
        attacker = attacker == null ? Optional.empty() : attacker;
    }
}
