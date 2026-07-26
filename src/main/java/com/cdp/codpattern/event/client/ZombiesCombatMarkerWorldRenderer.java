package com.cdp.codpattern.event.client;

import com.cdp.codpattern.CodPatternConstants;
import com.cdp.codpattern.client.render.CombatMarkerWorldRenderer;
import com.cdp.codpattern.client.zombies.ClientZombiesState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = CodPatternConstants.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ZombiesCombatMarkerWorldRenderer {
    private static final double MIN_RENDER_DEPTH = 0.05D;

    private ZombiesCombatMarkerWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (!"WAVE_ACTIVE".equals(ClientZombiesState.phaseKey())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        ClientLevel level = minecraft.level;
        Camera camera = event.getCamera();
        if (localPlayer == null || level == null || camera == null || !camera.isInitialized() || minecraft.gameRenderer == null) {
            return;
        }

        Set<UUID> activeZombieIds = ClientZombiesState.activeZombieEntityIds();
        if (activeZombieIds.isEmpty()) {
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

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        try {
            for (UUID entityId : activeZombieIds) {
                Entity entity = findClientEntity(level, entityId);
                if (!(entity instanceof LivingEntity livingEntity)
                        || !livingEntity.isAlive()
                        || livingEntity.isRemoved()) {
                    continue;
                }
                if (!event.getFrustum().isVisible(livingEntity.getBoundingBox().inflate(0.25D))
                        || !localPlayer.hasLineOfSight(livingEntity)) {
                    continue;
                }

                Vec3 anchor = CombatMarkerWorldRenderer.interpolateHeadPos(livingEntity, event.getPartialTick());
                Vec3 relative = anchor.subtract(cameraPos);
                double depth = relative.dot(cameraForward);
                if (depth <= MIN_RENDER_DEPTH) {
                    continue;
                }

                float pixelScale = (float) ((2.0D * depth * tanHalfFov) / screenHeight);
                if (!Float.isFinite(pixelScale) || pixelScale <= 0.0f) {
                    continue;
                }

                CombatMarkerWorldRenderer.renderEnemyMarker(
                        poseStack,
                        bufferSource,
                        font,
                        minecraft,
                        relative,
                        pixelScale,
                        livingEntity.getHealth(),
                        Math.max(1.0f, livingEntity.getMaxHealth()),
                        livingEntity.getDisplayName().getString());
            }
            bufferSource.endBatch();
        } finally {
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    private static Entity findClientEntity(ClientLevel level, UUID entityId) {
        if (level == null || entityId == null) {
            return null;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity != null && entityId.equals(entity.getUUID())) {
                return entity;
            }
        }
        return null;
    }
}
