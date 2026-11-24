package com.cdp.codpattern.client.gui.screen.backpack;

import com.cdp.codpattern.compat.lrtactical.LrTacticalClientApi;
import com.cdp.codpattern.compat.lrtactical.LrTacticalGatewayProvider;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.compat.tacz.client.RefitPreviewCamera;
import com.cdp.codpattern.compat.tacz.client.TaczClientApi;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.util.RenderDistance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class BackpackEquipmentRenderer {
    private BackpackEquipmentRenderer() {
    }

    public static Component displayName(ItemStack stack) {
        return stack.isEmpty() ? Component.translatable("screen.codpattern.backpack.tooltip.empty") : stack.getHoverName();
    }

    public static Component category(ItemStack stack) {
        if (TaczClientApi.isGun(stack)) {
            return TaczGatewayProvider.gateway().resolveGunType(stack)
                    .map(type -> Component.translatableWithFallback("tacz.type." + type + ".name", type))
                    .orElseGet(Component::empty);
        }
        if (LrTacticalGatewayProvider.gateway().isMeleeWeapon(stack)) {
            return Component.translatable("lrtactical.type.melee.name");
        }
        if (LrTacticalGatewayProvider.gateway().isThrowableStack(stack)) {
            return Component.translatable("screen.codpattern.weapon.throwable");
        }
        return Component.empty();
    }

    public static Component origin(ItemStack stack) {
        Component name = TaczClientApi.isGun(stack)
                ? TaczClientApi.getGunPackName(stack)
                : LrTacticalClientApi.getLrItemPackName(stack);
        return name == null ? Component.empty() : name.plainCopy();
    }

    public static void renderCardIcon(GuiGraphics graphics, ItemStack stack, int x, int y, int width,
                                      int height, int artPercent, int labelBand) {
        int artWidth = BackpackUi.centeredExtent(Math.round(width * artPercent / 100f), width);
        renderIcon(graphics, stack, x + (width - artWidth) / 2, y + labelBand, artWidth, height - 2 * labelBand);
    }

    public static void renderIcon(GuiGraphics graphics, ItemStack stack, int x, int y, int width, int height) {
        if (stack.isEmpty() || width <= 0 || height <= 0) {
            return;
        }
        ResourceLocation hud = TaczClientApi.isGun(stack) ? TaczClientApi.getGunHudTexture(stack) : null;
        PoseStack pose = graphics.pose();
        if (hud != null && !MissingTextureAtlasSprite.getLocation().equals(hud)) {
            // TaCZ's complete 3:1 HUD canvas stays centered, including transparent pixels.
            int iconHeight = Math.max(1, BackpackUi.centeredExtent(Math.min(width / 3, height), height));
            int iconWidth = Math.min(width, iconHeight * 3);
            int left = x + (width - iconWidth) / 2;
            int top = y + (height - iconHeight) / 2;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(hud, left, top, 0, 0, iconWidth, iconHeight, iconWidth, iconHeight);
            RenderSystem.disableBlend();
            return;
        }

        int size = Math.min(width, height);
        pose.pushPose();
        pose.translate(x + (width - size) / 2f, y + (height - size) / 2f, 0);
        pose.scale(size / 16f, size / 16f, 1);
        graphics.renderItem(stack, 0, 0);
        pose.popPose();
    }

    public static void renderModel(GuiGraphics graphics, ItemStack stack, int x, int y, int width, int height) {
        renderModel(graphics, stack, x, y, width, height, RefitPreviewCamera.Pose.OVERVIEW);
    }

    static void renderModel(GuiGraphics graphics, ItemStack stack, int x, int y, int width, int height,
                            RefitPreviewCamera.Pose camera) {
        if (stack.isEmpty() || width <= 0 || height <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // FIXED uses the pack's item-frame origin and 3D mesh, including equipped attachments.
        float yaw = 18;
        float roll = 4;
        float scale;
        if (TaczClientApi.isGun(stack)) {
            float length = 3.8f;
            var display = TimelessAPI.getGunDisplay(stack).orElse(null);
            if (display != null && display.getGunModel() != null) {
                var bones = display.getGunModel().getIndexBones();
                var origin = bones.get("fixed");
                var muzzle = bones.get("muzzle_flash");
                // Packs can face either direction in an item frame. Keep the camera on the same side.
                if (origin != null && origin.getRotation() != null) {
                    yaw += 270 - origin.getRotation().get(1);
                }
                if (origin != null && muzzle != null) {
                    var fixedScale = display.getTransform().getScale().getFixed();
                    float halfLength = Math.abs(muzzle.getPivot().get(2) - origin.getPivot().get(2));
                    length = Math.max(1f, halfLength / 8f * (fixedScale == null ? 1f : fixedScale.z()));
                }
            }
            scale = Math.min(width * 0.91f / length, height * 1.16f);
            RenderDistance.markGuiRenderTimestamp();
        } else if (LrTacticalGatewayProvider.gateway().isThrowableStack(stack)) {
            scale = Math.min(width, height) * 1.10f;
        } else {
            scale = Math.min(width * 0.62f, height * 0.85f);
            if (LrTacticalGatewayProvider.gateway().isMeleeWeapon(stack)) {
                roll = -84;
                scale = Math.min(width * .67f, height * .94f);
            }
        }

        graphics.flush();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x + width / 2f, y + height / 2f, 160);
            pose.scale(scale, -scale, scale);
            var rollRotation = Axis.ZP.rotationDegrees(roll);
            var pitchRotation = Axis.XP.rotationDegrees(12);
            var yawRotation = Axis.YP.rotationDegrees(yaw);
            if (camera == RefitPreviewCamera.Pose.OVERVIEW) {
                pose.mulPose(rollRotation);
                pose.mulPose(pitchRotation);
                pose.mulPose(yawRotation);
            } else {
                camera.apply(pose, rollRotation.mul(pitchRotation).mul(yawRotation));
            }
            Lighting.setupForFlatItems();
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, pose, graphics.bufferSource(), mc.level, 0);
            graphics.flush();
        } finally {
            Lighting.setupFor3DItems();
            pose.popPose();
        }
    }
}
