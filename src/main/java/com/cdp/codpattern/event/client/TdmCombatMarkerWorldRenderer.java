package com.cdp.codpattern.event.client;

import com.cdp.codpattern.CodPatternConstants;
import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.client.TdmCombatMarkerTracker;
import com.cdp.codpattern.client.render.CombatMarkerWorldRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = CodPatternConstants.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TdmCombatMarkerWorldRenderer {
    private static final double MIN_RENDER_DEPTH = 0.05D;

    private TdmCombatMarkerWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        String phase = ClientTdmState.currentPhase();
        boolean warmup = "WARMUP".equals(phase);
        boolean playing = "PLAYING".equals(phase);
        if (!warmup && !playing) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        ClientLevel level = minecraft.level;
        Camera camera = event.getCamera();
        if (localPlayer == null || level == null || camera == null || !camera.isInitialized() || minecraft.gameRenderer == null) {
            return;
        }

        TdmCombatMarkerTracker.TeamVisionSnapshot snapshot = TdmCombatMarkerTracker.INSTANCE.snapshot();
        if (!snapshot.hasLocalTeam()
                || snapshot.localPlayerId() == null
                || !snapshot.localPlayerId().equals(localPlayer.getUUID())) {
            return;
        }

        int screenHeight = Math.max(1, minecraft.getWindow().getHeight());
        double tanHalfFov = event.getProjectionMatrix().m11() == 0.0f
                ? 0.0D
                : Math.abs(1.0D / event.getProjectionMatrix().m11());
        if (tanHalfFov <= 0.0D) {
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        Vec3 cameraForward = CombatMarkerWorldRenderer.toVec3(camera.getLookVector()).normalize();
        PoseStack poseStack = event.getPoseStack();
        Font font = minecraft.font;
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        TdmCombatMarkerTracker markerTracker = TdmCombatMarkerTracker.INSTANCE;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        try {
            for (Map.Entry<UUID, String> entry : snapshot.teamByPlayer().entrySet()) {
                UUID playerId = entry.getKey();
                if (playerId == null
                        || playerId.equals(localPlayer.getUUID())
                        || !snapshot.isLiving(playerId)) {
                    continue;
                }

                Player tracked = level.getPlayerByUUID(playerId);
                if (tracked == null || !tracked.isAlive() || tracked.isRemoved()) {
                    continue;
                }
                if (!event.getFrustum().isVisible(tracked.getBoundingBox().inflate(0.25D))) {
                    continue;
                }

                boolean teammate = snapshot.isTeammate(playerId);
                boolean enemy = snapshot.isEnemy(playerId);
                if (!teammate && !(playing && enemy && markerTracker.shouldRenderEnemyHealthBar(playerId))) {
                    continue;
                }

                Vec3 anchor = interpolatePlayerHeadPos(tracked, event.getPartialTick());
                Vec3 relative = anchor.subtract(cameraPos);
                double depth = relative.dot(cameraForward);
                if (depth <= MIN_RENDER_DEPTH) {
                    continue;
                }

                float pixelScale = (float) ((2.0D * depth * tanHalfFov) / screenHeight);
                if (!Float.isFinite(pixelScale) || pixelScale <= 0.0f) {
                    continue;
                }

                if (teammate) {
                    CombatMarkerWorldRenderer.renderTeammateMarker(
                            poseStack,
                            bufferSource,
                            font,
                            minecraft,
                            relative,
                            pixelScale,
                            tracked.getScoreboardName());
                    continue;
                }

                CombatMarkerWorldRenderer.renderEnemyMarker(
                        poseStack,
                        bufferSource,
                        font,
                        minecraft,
                        relative,
                        pixelScale,
                        tracked.getHealth(),
                        Math.max(1.0f, tracked.getMaxHealth()),
                        tracked.getScoreboardName());
            }
            bufferSource.endBatch();
        } finally {
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    private static Vec3 interpolatePlayerHeadPos(Player player, float partialTick) {
        return CombatMarkerWorldRenderer.interpolateHeadPos(player, partialTick);
    }
}
