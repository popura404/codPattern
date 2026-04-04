package com.cdp.codpattern.event.client;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.client.TdmCombatMarkerTracker;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TdmCombatMarkerWorldRenderer {
    private static final double MIN_RENDER_DEPTH = 0.05D;
    private static final double ENEMY_BAR_HEAD_OFFSET = 0.18D;
    private static final int BAR_WIDTH_PX = 180;
    private static final int BAR_HEIGHT_PX = 8;
    private static final int BAR_OUTLINE_PX = 1;
    private static final int BAR_Y_OFFSET_PX = 34;
    private static final int TEXT_GAP_PX = 4;
    private static final float ID_TEXT_SCALE = 2.25f;
    private static final int BAR_OUTLINE_COLOR = 0xB4000000;
    private static final int BAR_BACKGROUND_COLOR = 0x9B2B0E0E;
    private static final int BAR_FILL_COLOR = 0xE6FF3A3A;
    private static final int ENEMY_TEXT_COLOR = 0xFFF2F2F2;
    private static final int TEAMMATE_TEXT_COLOR = 0xFFD8E7FF;
    private static final int TEXT_BACKGROUND_COLOR = 0x66000000;

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
        Vec3 cameraForward = toVec3(camera.getLookVector()).normalize();
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
                    renderTeammateMarker(
                            poseStack,
                            bufferSource,
                            font,
                            minecraft,
                            relative,
                            pixelScale,
                            tracked.getScoreboardName());
                    continue;
                }

                renderEnemyMarker(
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

    private static void renderEnemyMarker(PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            Font font,
            Minecraft minecraft,
            Vec3 relative,
            float pixelScale,
            float health,
            float maxHealth,
            String idText) {
        float healthRatio = Math.max(0.0f, Math.min(1.0f, health / maxHealth));

        poseStack.pushPose();
        poseStack.translate(relative.x, relative.y, relative.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-pixelScale, -pixelScale, pixelScale);

        drawHealthBar(poseStack, healthRatio);
        drawLabelText(poseStack, bufferSource, font, idText, enemyTextTop(), ENEMY_TEXT_COLOR);

        poseStack.popPose();
    }

    private static void renderTeammateMarker(PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            Font font,
            Minecraft minecraft,
            Vec3 relative,
            float pixelScale,
            String idText) {
        poseStack.pushPose();
        poseStack.translate(relative.x, relative.y, relative.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-pixelScale, -pixelScale, pixelScale);

        drawLabelText(poseStack, bufferSource, font, idText, enemyTextTop(), TEAMMATE_TEXT_COLOR);

        poseStack.popPose();
    }

    private static void drawHealthBar(PoseStack poseStack, float healthRatio) {
        int left = -(BAR_WIDTH_PX / 2);
        int top = -BAR_Y_OFFSET_PX;
        int right = left + BAR_WIDTH_PX;
        int bottom = top + BAR_HEIGHT_PX;

        drawQuad(poseStack,
                left - BAR_OUTLINE_PX,
                top - BAR_OUTLINE_PX,
                right + BAR_OUTLINE_PX,
                bottom + BAR_OUTLINE_PX,
                BAR_OUTLINE_COLOR);
        drawQuad(poseStack, left, top, right, bottom, BAR_BACKGROUND_COLOR);

        int fillWidth = Math.round(BAR_WIDTH_PX * healthRatio);
        if (fillWidth > 0) {
            drawQuad(poseStack, left, top, left + fillWidth, bottom, BAR_FILL_COLOR);
        }
    }

    private static void drawLabelText(PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            Font font,
            String idText,
            float textTop,
            int textColor) {
        if (idText == null || idText.isBlank()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0f, textTop, 0.0f);
        poseStack.scale(ID_TEXT_SCALE, ID_TEXT_SCALE, 1.0f);

        Matrix4f matrix = poseStack.last().pose();
        float textX = -font.width(idText) / 2.0f;
        font.drawInBatch(
                idText,
                textX,
                0.0f,
                textColor,
                false,
                matrix,
                bufferSource,
                Font.DisplayMode.SEE_THROUGH,
                TEXT_BACKGROUND_COLOR,
                LightTexture.FULL_BRIGHT);

        poseStack.popPose();
    }

    private static float enemyTextTop() {
        int barBottom = -BAR_Y_OFFSET_PX + BAR_HEIGHT_PX;
        return barBottom + TEXT_GAP_PX;
    }

    private static void drawQuad(PoseStack poseStack, float left, float top, float right, float bottom, int color) {
        Matrix4f matrix = poseStack.last().pose();
        float alpha = ((color >>> 24) & 0xFF) / 255.0f;
        float red = ((color >>> 16) & 0xFF) / 255.0f;
        float green = ((color >>> 8) & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bufferBuilder.vertex(matrix, left, top, 0.0f).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, left, bottom, 0.0f).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, right, bottom, 0.0f).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, right, top, 0.0f).color(red, green, blue, alpha).endVertex();
        BufferUploader.drawWithShader(bufferBuilder.end());
    }

    private static Vec3 interpolatePlayerHeadPos(Player player, float partialTick) {
        return player.getEyePosition(partialTick).add(0.0D, ENEMY_BAR_HEAD_OFFSET, 0.0D);
    }

    private static Vec3 toVec3(Vector3f vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }
}
