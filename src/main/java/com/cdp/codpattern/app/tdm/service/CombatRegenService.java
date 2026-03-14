package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.config.tdm.CodTdmConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CombatRegenService {
    private CombatRegenService() {
    }

    public static void onPlayerDamaged(Map<UUID, Integer> combatRegenCooldowns,
            ServerPlayer player,
            CodTdmConfig config) {
        if (combatRegenCooldowns == null || player == null || config == null) {
            return;
        }
        if (config.getCombatRegenHalfHeartsPerSecond() <= 0.0f) {
            combatRegenCooldowns.remove(player.getUUID());
            return;
        }
        combatRegenCooldowns.put(player.getUUID(), Math.max(0, config.getCombatRegenDelayTicks()));
    }

    public static void clearPlayerCooldown(Map<UUID, Integer> combatRegenCooldowns, UUID playerId) {
        if (combatRegenCooldowns == null || playerId == null) {
            return;
        }
        combatRegenCooldowns.remove(playerId);
    }

    public static void tick(Map<UUID, Integer> combatRegenCooldowns,
            Iterable<ServerPlayer> joinedPlayers,
            Set<UUID> respawningPlayers,
            boolean playingPhase,
            CodTdmConfig config) {
        if (combatRegenCooldowns == null || joinedPlayers == null || config == null || !playingPhase) {
            return;
        }

        float healPerTick = Math.max(0.0f, config.getCombatRegenHalfHeartsPerSecond()) / 20.0f;
        if (healPerTick <= 0.0f) {
            combatRegenCooldowns.clear();
            return;
        }

        for (ServerPlayer player : joinedPlayers) {
            if (player == null) {
                continue;
            }

            UUID playerId = player.getUUID();
            Integer cooldown = combatRegenCooldowns.get(playerId);
            if (cooldown == null) {
                continue;
            }

            if (player.isSpectator()
                    || !player.isAlive()
                    || (respawningPlayers != null && respawningPlayers.contains(playerId))) {
                combatRegenCooldowns.remove(playerId);
                continue;
            }

            if (player.getHealth() >= player.getMaxHealth()) {
                combatRegenCooldowns.remove(playerId);
                continue;
            }

            int remaining = Math.max(0, cooldown);
            if (remaining > 0) {
                remaining--;
                combatRegenCooldowns.put(playerId, remaining);
                if (remaining > 0) {
                    continue;
                }
            }

            player.heal(healPerTick);
            if (player.getHealth() >= player.getMaxHealth()) {
                combatRegenCooldowns.remove(playerId);
            } else {
                combatRegenCooldowns.put(playerId, 0);
            }
        }
    }
}
