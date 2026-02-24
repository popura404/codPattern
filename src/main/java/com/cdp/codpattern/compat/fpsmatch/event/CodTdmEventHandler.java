package com.cdp.codpattern.compat.fpsmatch.event;

import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.compat.fpsmatch.map.CodTdmMap;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
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
        event.registerGameType(TdmGameTypes.CDP_TDM, CodTdmMap::new);
    }

    /**
     * 处理伤害事件
     * 用于实现热身期间伤害归零（保留击退）和无敌状态
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // 获取玩家所在的地图
        Optional<CodTdmReadPort> readPortOptional = FpsMatchGatewayProvider.gateway().findPlayerTdmReadPort(player);
        if (readPortOptional.isEmpty()) {
            return;
        }
        CodTdmReadPort readPort = readPortOptional.get();

        // 游戏未开始时不受伤害
        if (!readPort.isStarted()) {
            event.setCanceled(true);
            return;
        }

        // 无敌期间取消所有伤害
        if (readPort.isPlayerInvincible(player.getUUID())) {
            event.setCanceled(true);
            return;
        }

        ServerPlayer attacker = resolveAttacker(event);
        if (isTeammate(readPort, attacker, player)) {
            event.setCanceled(true);
            return;
        }

        // 热身期间：伤害归零但保留事件（保留击退效果）
        if (!readPort.canDealDamage()) {
            event.setAmount(0);
            // 不取消事件，击退效果由 Minecraft 原生处理
        }
    }

    /**
     * 处理玩家死亡事件
     * 即使没有攻击者也处理（自杀、摔落等）
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerPlayer killer = resolveKiller(event);
        var gateway = FpsMatchGatewayProvider.gateway();
        Optional<CodTdmReadPort> readPortOptional = gateway.findPlayerTdmReadPort(player);
        Optional<CodTdmActionPort> actionPortOptional = gateway.findPlayerTdmActionPort(player);
        if ((readPortOptional.isEmpty() || actionPortOptional.isEmpty()) && killer != null) {
            if (readPortOptional.isEmpty()) {
                readPortOptional = gateway.findPlayerTdmReadPort(killer);
            }
            if (actionPortOptional.isEmpty()) {
                actionPortOptional = gateway.findPlayerTdmActionPort(killer);
            }
        }
        if (readPortOptional.isEmpty() || actionPortOptional.isEmpty()) {
            return;
        }
        CodTdmReadPort readPort = readPortOptional.get();
        CodTdmActionPort actionPort = actionPortOptional.get();

        // 只有游戏开始后才处理死亡
        if (!readPort.isStarted()) {
            return;
        }

        // 取消死亡事件，防止玩家真正死亡
        event.setCanceled(true);

        // 击杀计分统一在死亡事件处理，兼容子弹/投射物 owner。
        if (killer != null
                && !killer.getUUID().equals(player.getUUID())
                && !isTeammate(readPort, killer, player)) {
            actionPort.onPlayerKill(killer, player);
        }

        // 调用地图的死亡处理逻辑
        actionPort.onPlayerDead(player, killer);

        // 恢复玩家到满血状态（取消死亡）
        player.setHealth(player.getMaxHealth());
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

    private static boolean isTeammate(CodTdmReadPort readPort, ServerPlayer attacker, ServerPlayer victim) {
        if (readPort == null || attacker == null || victim == null) {
            return false;
        }
        if (attacker.getUUID().equals(victim.getUUID())) {
            return false;
        }
        Optional<String> attackerTeam = readPort.findTeamNameByPlayer(attacker);
        Optional<String> victimTeam = readPort.findTeamNameByPlayer(victim);
        return attackerTeam.isPresent() && attackerTeam.equals(victimTeam);
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
