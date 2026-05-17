package com.cdp.codpattern.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Shared world-space combat marker drawing used by TDM players and zombies mobs.
 */
public final class CombatMarkerWorldRenderer {
    public static final double DEFAULT_HEAD_OFFSET = 0.18D;

    private static final int BAR_WIDTH_PX = 180;
    private static final int BAR_HEIGHT_PX = 8;
    private static final int BAR_OUTLINE_PX = 1;
    private static final int BAR_Y_OFFSET_PX = 34;
    private static final int TEXT_GAP_PX = 4;
    private static final float ENEMY_ID_TEXT_SCALE = 2.25f;
    private static final float TEAMMATE_ID_TEXT_SCALE = ENEMY_ID_TEXT_SCALE * 0.7f;
    private static final int BAR_OUTLINE_COLOR = 0xB4000000;
    private static final int BAR_BACKGROUND_COLOR = 0x9B2B0E0E;
    private static final int BAR_FILL_COLOR = 0xE6FF3A3A;
    private static final int ENEMY_TEXT_COLOR = 0xFFFF5A5A;
    private static final int TEAMMATE_TEXT_COLOR = 0xFFD8E7FF;
    private static final int TEXT_BACKGROUND_COLOR = 0x66000000;

    private CombatMarkerWorldRenderer() {
    }

    public static void renderEnemyMarker(PoseStack poseStack,
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
        drawLabelText(
                poseStack,
                bufferSource,
                font,
                idText,
                enemyTextTop(),
                ENEMY_TEXT_COLOR,
                ENEMY_ID_TEXT_SCALE);

        poseStack.popPose();
    }

    public static void renderTeammateMarker(PoseStack poseStack,
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

        drawLabelText(
                poseStack,
                bufferSource,
                font,
                idText,
                enemyTextTop(),
                TEAMMATE_TEXT_COLOR,
                TEAMMATE_ID_TEXT_SCALE);

        poseStack.popPose();
    }

    public static Vec3 interpolateHeadPos(LivingEntity entity, float partialTick) {
        return interpolateHeadPos(entity, partialTick, DEFAULT_HEAD_OFFSET);
    }

    public static Vec3 interpolateHeadPos(LivingEntity entity, float partialTick, double extraHeadOffset) {
        return entity.getEyePosition(partialTick).add(0.0D, extraHeadOffset, 0.0D);
    }

    public static Vec3 toVec3(Vector3f vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
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
            int textColor,
            float textScale) {
        if (idText == null || idText.isBlank()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0f, textTop, 0.0f);
        poseStack.scale(textScale, textScale, 1.0f);

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
}
