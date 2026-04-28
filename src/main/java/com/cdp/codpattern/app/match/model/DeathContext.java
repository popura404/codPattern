package com.cdp.codpattern.app.match.model;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public record DeathContext(
        DamageSource source,
        Entity directEntity,
        Optional<ServerPlayer> killer
) {
    public DeathContext {
        killer = killer == null ? Optional.empty() : killer;
    }
}
