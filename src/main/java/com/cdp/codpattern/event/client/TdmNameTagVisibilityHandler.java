package com.cdp.codpattern.event.client;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.client.TdmCombatMarkerTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TdmNameTagVisibilityHandler {
    private TdmNameTagVisibilityHandler() {
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player trackedPlayer)) {
            return;
        }

        String phase = ClientTdmState.currentPhase();
        if (!"WARMUP".equals(phase) && !"PLAYING".equals(phase)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        ClientLevel level = minecraft.level;
        if (localPlayer == null || level == null) {
            return;
        }

        TdmCombatMarkerTracker.TeamVisionSnapshot snapshot = TdmCombatMarkerTracker.INSTANCE.snapshot();
        if (!snapshot.hasLocalTeam()
                || snapshot.localPlayerId() == null
                || !snapshot.localPlayerId().equals(localPlayer.getUUID())) {
            return;
        }

        UUID trackedPlayerId = trackedPlayer.getUUID();
        if (trackedPlayerId.equals(localPlayer.getUUID())
                || !snapshot.teamByPlayer().containsKey(trackedPlayerId)) {
            return;
        }

        event.setResult(Event.Result.DENY);
    }
}
