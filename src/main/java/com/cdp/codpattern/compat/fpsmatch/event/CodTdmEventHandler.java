package com.cdp.codpattern.compat.fpsmatch.event;

import com.cdp.codpattern.app.match.GameModeBootstrap;
import com.cdp.codpattern.app.match.model.DamageContext;
import com.cdp.codpattern.app.match.model.DamageDecision;
import com.cdp.codpattern.app.match.model.DeathContext;
import com.cdp.codpattern.app.match.model.DeathDecision;
import com.cdp.codpattern.app.match.model.EntityDamageContext;
import com.cdp.codpattern.app.match.model.EntityDeathContext;
import com.cdp.codpattern.app.match.model.EntityLifecycleContext;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeEntityCombatEventPort;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.match.port.ModeCombatEventPort;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGateway;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * COD TDM 事件处理器
 * 处理游戏类型注册、击杀事件、伤害事件等
 */
@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CodTdmEventHandler {

    /**
     * 注册 TDM 游戏类型
     */
    @SubscribeEvent
    public static void onRegisterFPSMap(RegisterFPSMapEvent event) {
        GameModeBootstrap.registerCommonProviders(event);
    }

    /**
     * 处理伤害事件
     * 用于实现热身期间伤害归零（保留击退）和无敌状态
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            handlePlayerHurt(event, player);
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        FpsMatchGateway gateway = FpsMatchGatewayProvider.gateway();
        Optional<RoomId> roomId = gateway.entityOwnershipRegistry().roomIdOf(entity);
        if (roomId.isEmpty()) {
            return;
        }
        Optional<ModeEntityCombatEventPort> combatPortOptional = gateway.findRoomEntityCombatEventPort(roomId.get());
        if (combatPortOptional.isEmpty()) {
            return;
        }

        DamageDecision decision = combatPortOptional.get().onEntityHurt(entity, new EntityDamageContext(
                roomId.get(),
                event.getSource(),
                event.getSource().getDirectEntity(),
                Optional.ofNullable(resolveAttacker(event)),
                event.getAmount()));
        applyDamageDecision(event, decision);
    }

    private static void handlePlayerHurt(LivingHurtEvent event, ServerPlayer player) {
        Optional<ModeCombatEventPort> combatPortOptional = FpsMatchGatewayProvider.gateway()
                .findPlayerCombatEventPort(player);
        if (combatPortOptional.isEmpty()) {
            return;
        }

        DamageDecision decision = combatPortOptional.get().onPlayerHurt(player, new DamageContext(
                event.getSource(),
                event.getSource().getDirectEntity(),
                Optional.ofNullable(resolveAttacker(event)),
                event.getAmount()));
        applyDamageDecision(event, decision);
    }

    private static void applyDamageDecision(LivingHurtEvent event, DamageDecision decision) {
        if (decision == null) {
            return;
        }
        if (decision.cancelEvent()) {
            event.setCanceled(true);
        }
        decision.replacementAmount().ifPresent(event::setAmount);
    }

    /**
     * 处理玩家死亡事件
     * 即使没有攻击者也处理（自杀、摔落等）
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            handlePlayerDeath(event, player);
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        FpsMatchGateway gateway = FpsMatchGatewayProvider.gateway();
        Optional<RoomId> roomId = gateway.entityOwnershipRegistry().roomIdOf(entity);
        if (roomId.isEmpty()) {
            return;
        }
        Optional<ModeEntityCombatEventPort> combatPortOptional = gateway.findRoomEntityCombatEventPort(roomId.get());
        if (combatPortOptional.isEmpty()) {
            return;
        }

        DeathDecision decision = combatPortOptional.get().onEntityDeath(entity, new EntityDeathContext(
                roomId.get(),
                event.getSource(),
                event.getSource().getDirectEntity(),
                Optional.ofNullable(resolveKiller(event))));
        applyDeathDecision(event, entity, decision);
    }

    private static void handlePlayerDeath(LivingDeathEvent event, ServerPlayer player) {
        ServerPlayer killer = resolveKiller(event);
        Optional<ModeCombatEventPort> combatPortOptional = FpsMatchGatewayProvider.gateway()
                .findPlayerCombatEventPort(player);
        if (combatPortOptional.isEmpty()) {
            return;
        }

        DeathDecision decision = combatPortOptional.get().onPlayerDeath(player, new DeathContext(
                event.getSource(),
                event.getSource().getDirectEntity(),
                Optional.ofNullable(killer)));
        applyDeathDecision(event, player, decision);
    }

    private static void applyDeathDecision(LivingDeathEvent event, LivingEntity entity, DeathDecision decision) {
        if (decision == null) {
            return;
        }
        if (decision.cancelEvent()) {
            event.setCanceled(true);
        }
        if (decision.restoreFullHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide || entity instanceof ServerPlayer) {
            return;
        }

        FpsMatchGateway gateway = FpsMatchGatewayProvider.gateway();
        ModeEntityOwnershipRegistry registry = gateway.entityOwnershipRegistry();
        Optional<RoomId> roomId = registry.roomIdOf(entity);
        if (roomId.isEmpty()) {
            return;
        }
        gateway.findRoomEntityLifecyclePort(roomId.get())
                .ifPresent(port -> port.onRoomEntityRemoved(entity, new EntityLifecycleContext(roomId.get())));
        registry.unregister(entity);
    }

    private static ServerPlayer resolveKiller(LivingDeathEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        ServerPlayer killer = asServerPlayer(sourceEntity);
        if (killer != null) {
            return killer;
        }

        Entity directEntity = event.getSource().getDirectEntity();
        killer = asServerPlayer(directEntity);
        if (killer != null) {
            return killer;
        }

        killer = resolveProjectileOwner(sourceEntity);
        if (killer != null) {
            return killer;
        }
        killer = resolveProjectileOwner(directEntity);
        if (killer != null) {
            return killer;
        }

        if (event.getEntity() instanceof ServerPlayer victim) {
            Entity killCredit = victim.getKillCredit();
            killer = asServerPlayer(killCredit);
            if (killer != null) {
                return killer;
            }
            killer = resolveProjectileOwner(killCredit);
            if (killer != null) {
                return killer;
            }

            Entity lastHurtBy = victim.getLastHurtByMob();
            killer = asServerPlayer(lastHurtBy);
            if (killer != null) {
                return killer;
            }
            return resolveProjectileOwner(lastHurtBy);
        }

        return null;
    }

    private static ServerPlayer resolveAttacker(LivingHurtEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        ServerPlayer attacker = asServerPlayer(sourceEntity);
        if (attacker != null) {
            return attacker;
        }

        Entity directEntity = event.getSource().getDirectEntity();
        attacker = asServerPlayer(directEntity);
        if (attacker != null) {
            return attacker;
        }

        attacker = resolveProjectileOwner(sourceEntity);
        if (attacker != null) {
            return attacker;
        }
        return resolveProjectileOwner(directEntity);
    }

    private static ServerPlayer asServerPlayer(Entity entity) {
        return entity instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    private static ServerPlayer resolveProjectileOwner(Entity entity) {
        if (entity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        return null;
    }
}
