package com.cdp.codpattern.app.tdm.service;

import com.cdp.codpattern.app.tdm.model.TdmGamePhase;
import com.cdp.codpattern.network.tdm.KillFeedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class KillFeedService {
    public interface Hooks {
        void broadcastKillFeed(KillFeedPacket packet);
    }

    private KillFeedService() {
    }

    public static void broadcast(ServerPlayer victim, ServerPlayer killer, TdmGamePhase phase, Hooks hooks) {
        if (victim == null || hooks == null || !shouldBroadcast(phase)) {
            return;
        }

        boolean blunder = killer == null || killer.getUUID().equals(victim.getUUID());
        String killerName = blunder ? victim.getGameProfile().getName() : killer.getGameProfile().getName();
        String victimName = victim.getGameProfile().getName();

        ItemStack weaponStack = ItemStack.EMPTY;
        if (!blunder && killer != null) {
            weaponStack = killer.getMainHandItem().copy();
            if (!weaponStack.isEmpty()) {
                weaponStack.setCount(1);
            }
        }

        hooks.broadcastKillFeed(new KillFeedPacket(killerName, victimName, weaponStack, blunder));
    }

    private static boolean shouldBroadcast(TdmGamePhase phase) {
        return phase == TdmGamePhase.WARMUP || phase == TdmGamePhase.PLAYING;
    }
}
