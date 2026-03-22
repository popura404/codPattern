package com.phasetranscrystal.fpsmatch.common.client.data;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

public record RenderableArea(String key, Component name, int color, AreaData area) {
    public void render(PoseStack poseStack, MultiBufferSource bufferSource) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        AABB aabb = area.getAABB();
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        LevelRenderer.renderLineBox(
                poseStack,
                vertexConsumer,
                aabb.minX, aabb.minY, aabb.minZ,
                aabb.maxX, aabb.maxY, aabb.maxZ,
                red, green, blue, 1.0F,
                Math.max(red * 0.55F, 0.1F),
                Math.max(green * 0.55F, 0.1F),
                Math.max(blue * 0.55F, 0.1F)
        );
    }
}
