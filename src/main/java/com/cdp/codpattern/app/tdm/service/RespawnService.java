package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.tdm.DeathCamPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class RespawnService {
    private RespawnService() {
    }

    public static void scheduleRespawn(Map<UUID, Integer> respawnTimers, ServerPlayer player, int respawnDelayTicks) {
        if (player == null) {
            return;
        }
        respawnTimers.put(player.getUUID(), Math.max(1, respawnDelayTicks));
    }

    public static void tickRespawn(
            Map<UUID, Integer> respawnTimers,
            ServerLevel serverLevel,
            Predicate<ServerPlayer> respawnAction,
            int respawnRetryTicks
    ) {
        Iterator<Map.Entry<UUID, Integer>> iterator = respawnTimers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int newTime = entry.getValue() - 1;

            if (newTime <= 0) {
                UUID playerId = entry.getKey();
                Player player = serverLevel.getPlayerByUUID(playerId);
                if (player instanceof ServerPlayer serverPlayer) {
                    if (respawnAction.test(serverPlayer)) {
                        iterator.remove();
                    } else {
                        entry.setValue(Math.max(1, respawnRetryTicks));
                    }
                    continue;
                }
                iterator.remove();
            } else {
                entry.setValue(newTime);
            }
        }
    }

    public static boolean respawnPlayer(ServerPlayer player, Predicate<ServerPlayer> teleportToSpawn,
            java.util.function.Consumer<ServerPlayer> givePlayerKits, int invincibilityTicks,
            Set<UUID> invinciblePlayers, Map<UUID, Integer> invincibilityTimers) {
        if (player == null) {
            return false;
        }
        if (!teleportToSpawn.test(player)) {
            player.setGameMode(GameType.SPECTATOR);
            return false;
        }
        player.setGameMode(GameType.ADVENTURE);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.removeAllEffects();
        givePlayerKits.accept(player);

        invinciblePlayers.add(player.getUUID());
        invincibilityTimers.put(player.getUUID(), Math.max(0, invincibilityTicks));
        ModNetworkChannel.sendToPlayer(DeathCamPacket.clear(), player);
        return true;
    }

    public static void tickInvincibility(Map<UUID, Integer> invincibilityTimers, Set<UUID> invinciblePlayers) {
        Iterator<Map.Entry<UUID, Integer>> iterator = invincibilityTimers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int newTime = entry.getValue() - 1;

            if (newTime <= 0) {
                invinciblePlayers.remove(entry.getKey());
                iterator.remove();
            } else {
                entry.setValue(newTime);
            }
        }
    }
}
