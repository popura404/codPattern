package com.cdp.codpattern.client.gui.screen.backpack;

import com.cdp.codpattern.app.backpack.service.BackpackNamespaceFilter;
import com.cdp.codpattern.client.gui.GuiTextHelper;
import com.cdp.codpattern.client.gui.refit.BackPackSelectButton;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterClientCache;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public final class BackpackWeaponPreviewPanel {
    private BackpackWeaponPreviewPanel() {
    }

    public static void render(GuiGraphics graphics,
            int screenWidth,
            int screenHeight,
            int unitLength,
            BackPackSelectButton hoveredButton,
            Map<String, BackPackSelectButton.WeaponInfo> weaponInfo,
            float alphaFactor) {
        if (weaponInfo == null || weaponInfo.isEmpty() || hoveredButton == null || alphaFactor <= 0.0f) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int referenceLineHeight = GuiTextHelper.referenceLineHeight(mc.font);
        int panelPadding = unitLength;
        int weaponDisplayX = unitLength * 6;
        int weaponDisplayY = screenHeight - unitLength * 22 - unitLength * 4 - unitLength * 12;

        if (weaponDisplayY < 10) {
            weaponDisplayY = 10;
        }

        int panelRight = screenWidth - weaponDisplayX;
        int panelBottom = weaponDisplayY + unitLength * 13;
        int panelTop = weaponDisplayY - unitLength - 2;

        graphics.fillGradient(weaponDisplayX, panelTop,
                panelRight, panelBottom,
                scaleAlpha(0x05999988, alphaFactor),
                scaleAlpha(0x20AAAAAA, alphaFactor));

        int grayBarTop = panelTop;
        int grayBarBottom = weaponDisplayY + unitLength;
        graphics.fill(weaponDisplayX, grayBarTop, panelRight, grayBarBottom, scaleAlpha(0x46AAAAAA, alphaFactor));

        graphics.fill(weaponDisplayX, panelBottom - 1, panelRight, panelBottom, scaleAlpha(0x2AAAAAAA, alphaFactor));
        graphics.fill(weaponDisplayX, grayBarTop, weaponDisplayX + 1, panelBottom, scaleAlpha(0x38AAAAAA, alphaFactor));
        graphics.fill(panelRight - 1, grayBarTop, panelRight, panelBottom, scaleAlpha(0x38AAAAAA, alphaFactor));

        int dividerX = weaponDisplayX + unitLength * 25 - 1;
        int secondaryColumnWidth = unitLength * 25;
        int throwableColumnWidth = unitLength * 8;
        int dividerSecTac = dividerX + secondaryColumnWidth;
        int dividerTacLeth = dividerSecTac + throwableColumnWidth;

        int primaryWeaponX = weaponDisplayX + panelPadding + 4;
        int secondaryWeaponX = dividerX + panelPadding;
        int tacticalWeaponX = dividerSecTac + panelPadding;
        int lethalWeaponX = dividerTacLeth + panelPadding;

        String title = hoveredButton.getDisplayNameRaw();
        int titleY = grayBarTop + (grayBarBottom - grayBarTop - referenceLineHeight) / 2;
        GuiTextHelper.drawReferenceEllipsizedString(
                graphics,
                mc.font,
                title,
                primaryWeaponX,
                titleY,
                Math.max(24, panelRight - primaryWeaponX - panelPadding),
                scaleAlpha(0xFFF4DC8A, alphaFactor),
                true
        );

        graphics.fillGradient(dividerX, weaponDisplayY + unitLength,
                dividerX + 1, panelBottom - unitLength,
                scaleAlpha(0x40808080, alphaFactor),
                scaleAlpha(0x20404040, alphaFactor));
        graphics.fillGradient(dividerSecTac, weaponDisplayY + unitLength,
                dividerSecTac + 1, panelBottom - unitLength,
                scaleAlpha(0x40808080, alphaFactor),
                scaleAlpha(0x20404040, alphaFactor));
        graphics.fillGradient(dividerTacLeth, weaponDisplayY + unitLength,
                dividerTacLeth + 1, panelBottom - unitLength,
                scaleAlpha(0x40808080, alphaFactor),
                scaleAlpha(0x20404040, alphaFactor));

        WeaponFilterConfig filterConfig = WeaponFilterClientCache.get();
        boolean throwablesEnabled = filterConfig == null || filterConfig.isThrowablesEnabled();

        for (Map.Entry<String, BackPackSelectButton.WeaponInfo> entry : weaponInfo.entrySet()) {
            String type = entry.getKey();
            BackPackSelectButton.WeaponInfo info = entry.getValue();

            if (filterConfig != null && info.itemStack != null && !info.itemStack.isEmpty()) {
                ResourceLocation fallbackItemId = BuiltInRegistries.ITEM.getKey(info.itemStack.getItem());
                if (BackpackNamespaceFilter.isBlocked(filterConfig, info.itemStack, fallbackItemId)) {
                    continue;
                }
            }

            int weaponX = switch (type) {
                case "primary" -> primaryWeaponX;
                case "secondary" -> secondaryWeaponX;
                case "tactical" -> tacticalWeaponX;
                case "lethal" -> lethalWeaponX;
                default -> primaryWeaponX;
            };
            int weaponY = weaponDisplayY + unitLength + 2;
            int columnRight = switch (type) {
                case "primary" -> dividerX - panelPadding;
                case "secondary" -> dividerSecTac - panelPadding;
                case "tactical" -> dividerTacLeth - panelPadding;
                case "lethal" -> panelRight - panelPadding;
                default -> dividerX - panelPadding;
            };
            int textMaxWidth = Math.max(16, columnRight - weaponX - 2);

            boolean isThrowableSlot = "tactical".equals(type) || "lethal".equals(type);
            boolean drawThrowableDimmed = isThrowableSlot && !throwablesEnabled;

            Component typeLabel = switch (type) {
                case "primary" -> Component.translatable("screen.codpattern.backpack.preview.primary");
                case "secondary" -> Component.translatable("screen.codpattern.backpack.preview.secondary");
                case "tactical" -> Component.translatable("screen.codpattern.backpack.preview.tactical");
                case "lethal" -> Component.translatable("screen.codpattern.backpack.preview.lethal");
                default -> Component.literal(type);
            };
            int typeLabelColor = switch (type) {
                case "primary" -> scaleAlpha(0xFFFF6C6C, alphaFactor);
                case "secondary" -> scaleAlpha(0xFF74A8FF, alphaFactor);
                case "tactical" -> scaleAlpha(0xFF7ED6E7, alphaFactor);
                case "lethal" -> scaleAlpha(0xFFFFC867, alphaFactor);
                default -> scaleAlpha(0xFFFFFFFF, alphaFactor);
            };
            GuiTextHelper.drawReferenceEllipsizedString(
                    graphics,
                    mc.font,
                    typeLabel,
                    weaponX,
                    weaponY,
                    textMaxWidth,
                    typeLabelColor,
                    true
            );

            if (drawThrowableDimmed) {
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1f, 1f, 1f, 0.22f);
            }
            float scale = isThrowableSlot ? 2f : 3f;
            int slotContentHeight = isThrowableSlot ? unitLength * 5 : unitLength * 7;
            int nameY = weaponY + referenceLineHeight + (isThrowableSlot ? unitLength * 7 : slotContentHeight) + 2;

            if (info.texture != null && !isThrowableSlot) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                int textureWidth = unitLength * 18;
                int textureHeight = unitLength * 6;
                int textureX = weaponX;
                int textureY = weaponY + referenceLineHeight + 2;
                graphics.blit(info.texture, textureX, textureY,
                        0, 0, textureWidth, textureHeight,
                        textureWidth, textureHeight);
            } else if (info.itemStack != null && !info.itemStack.isEmpty()) {
                int itemSize = (int) (16 * scale);
                int maxSlotWidth = isThrowableSlot ? (throwableColumnWidth - panelPadding * 2) : (unitLength * 18);
                int itemX = weaponX + Math.max(0, (maxSlotWidth - itemSize) / 2);
                int itemY;
                if (isThrowableSlot) {
                    int contentTop = weaponY + referenceLineHeight + 2;
                    int contentBottom = nameY - 2;
                    int contentHeight = Math.max(1, contentBottom - contentTop);
                    itemY = contentTop + (contentHeight - itemSize) / 2;
                } else {
                    itemY = weaponY + referenceLineHeight + 2;
                }

                graphics.pose().pushPose();
                graphics.pose().translate(itemX, itemY, 0);
                graphics.pose().scale(scale, scale, 1);
                graphics.renderItem(info.itemStack, 0, 0);
                graphics.pose().popPose();
            }

            if (info.weaponName != null) {
                GuiTextHelper.drawReferenceEllipsizedString(
                        graphics,
                        mc.font,
                        info.weaponName,
                    weaponX,
                    nameY,
                    textMaxWidth,
                    scaleAlpha(0xFFFFFFFF, alphaFactor),
                    false
                );
            }
            if (info.packName != null && !isThrowableSlot) {
                int packY = weaponY + referenceLineHeight * 2 + unitLength * 7 + 6;
                GuiTextHelper.drawReferenceEllipsizedString(
                        graphics,
                        mc.font,
                        info.packName,
                        weaponX,
                        packY,
                        textMaxWidth,
                        scaleAlpha(0xAAFFFFFF, alphaFactor),
                        false
                );
            }

            if (drawThrowableDimmed) {
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                RenderSystem.disableBlend();
            }
        }

        if (alphaFactor < 0.999f) {
            graphics.fill(weaponDisplayX, panelTop, panelRight, panelBottom,
                    withAlpha(0xFF000000, Math.max(0, Math.min(255, (int) (170.0f * (1.0f - alphaFactor))))));
        }
    }

    private static int scaleAlpha(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int scaled = Math.max(0, Math.min(255, (int) (alpha * Math.max(0.0f, Math.min(1.0f, factor)))));
        return (scaled << 24) | (color & 0x00FFFFFF);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
