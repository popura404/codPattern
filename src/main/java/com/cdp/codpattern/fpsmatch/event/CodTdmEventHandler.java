package com.cdp.codpattern.fpsmatch.event;

import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.event.PlayerKillOnMapEvent;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
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
        event.registerGameType(CodTdmMap.GAME_TYPE, CodTdmMap::new);
    }

    /**
     * 处理玩家击杀事件
     */
    @SubscribeEvent
    public static void onPlayerKill(PlayerKillOnMapEvent event) {
        BaseMap map = event.getBaseMap();
        if (!(map instanceof CodTdmMap tdmMap)) {
            return;
        }

        ServerPlayer killer = event.getKiller();
        ServerPlayer victim = event.getDead();

        // 热身期间不计分
        if (!tdmMap.canDealDamage()) {
            // 注意：PlayerKillOnMapEvent 不可取消，只是不记分
            return;
        }

        // 无敌期间不计分
        if (tdmMap.isInvincible(victim.getUUID())) {
            return;
        }

        // 处理击杀计分
        tdmMap.onPlayerKill(killer, victim);

        // 死亡视角逻辑已移至 onPlayerDead 处理
        // tdmMap.startDeathCam(victim, killer);
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
        Optional<BaseMap> mapOpt = FPSMCore.getInstance().getMapByPlayer(player);
        if (mapOpt.isEmpty() || !(mapOpt.get() instanceof CodTdmMap tdmMap)) {
            return;
        }

        // 游戏未开始时不受伤害
        if (!tdmMap.isStart) {
            event.setCanceled(true);
            return;
        }

        // 无敌期间取消所有伤害
        if (tdmMap.isInvincible(player.getUUID())) {
            event.setCanceled(true);
            return;
        }

        // 热身期间：伤害归零但保留事件（保留击退效果）
        if (!tdmMap.canDealDamage()) {
            event.setAmount(0);
            // 不取消事件，击退效果由 Minecraft 原生处理
        }
    }

    /**
     * 处理玩家死亡事件
     * 即使没有攻击者也处理（自杀、摔落等）
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Optional<BaseMap> mapOpt = FPSMCore.getInstance().getMapByPlayer(player);
        if (mapOpt.isEmpty() || !(mapOpt.get() instanceof CodTdmMap tdmMap)) {
            return;
        }

        // 只有游戏开始后才处理死亡
        if (!tdmMap.isStart) {
            return;
        }

        // 取消死亡事件，防止玩家真正死亡
        event.setCanceled(true);

        ServerPlayer killer = null;
        Entity sourceEntity = event.getSource().getEntity();
        if (sourceEntity instanceof ServerPlayer serverPlayer) {
            killer = serverPlayer;
        }

        // 调用地图的死亡处理逻辑
        tdmMap.onPlayerDead(player, killer);

        // 恢复玩家到满血状态（因为取消了死亡）
        player.setHealth(player.getMaxHealth());
    }
}
