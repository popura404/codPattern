package com.cdp.codpattern.client.gui.screen.backpack;

import com.cdp.codpattern.client.gui.refit.BackPackSelectButton;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterClientCache;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Map;

public final class BackpackWeaponPreviewPanel {
    private BackpackWeaponPreviewPanel() {
    }

    public static void render(GuiGraphics graphics,
            int screenWidth,
            int screenHeight,
            int unitLength,
            BackPackSelectButton hoveredButton,
            Map<String, BackPackSelectButton.WeaponInfo> weaponInfo) {
        if (weaponInfo == null || weaponInfo.isEmpty() || hoveredButton == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int panelPadding = unitLength;
        int weaponDisplayX = unitLength * 6;
        int weaponDisplayY = screenHeight - unitLength * 22 - unitLength * 4 - unitLength * 12;

        if (weaponDisplayY < 10) {
            weaponDisplayY = 10;
        }

        int panelRight = screenWidth - weaponDisplayX;
        int panelBottom = weaponDisplayY + unitLength * 13;

        graphics.fillGradient(weaponDisplayX, weaponDisplayY - unitLength - 2,
                panelRight, panelBottom,
                0x05999988, 0x20AAAAAA);

        int grayBarTop = weaponDisplayY - unitLength - 2;
        int grayBarBottom = weaponDisplayY + unitLength;
        graphics.fill(weaponDisplayX, grayBarTop, panelRight, grayBarBottom, 0x46AAAAAA);

        graphics.fill(weaponDisplayX, panelBottom - 1, panelRight, panelBottom, 0x2AAAAAAA);
        graphics.fill(weaponDisplayX, grayBarTop, weaponDisplayX + 1, panelBottom, 0x38AAAAAA);
        graphics.fill(panelRight - 1, grayBarTop, panelRight, panelBottom, 0x38AAAAAA);

        int dividerX = weaponDisplayX + unitLength * 25 - 1;
        int secondaryColumnWidth = unitLength * 25;
        int throwableColumnWidth = unitLength * 8;
        int dividerSecTac = dividerX + secondaryColumnWidth;
        int dividerTacLeth = dividerSecTac + throwableColumnWidth;

        int primaryWeaponX = weaponDisplayX + panelPadding + 4;
        int secondaryWeaponX = dividerX + panelPadding;
        int tacticalWeaponX = dividerSecTac + panelPadding;
        int lethalWeaponX = dividerTacLeth + panelPadding;

        String displayName = hoveredButton.getDisplayNameRaw();
        String title = "§e§l" + displayName + " §7#" + hoveredButton.getBAGSERIAL();
        int titleY = grayBarTop + (grayBarBottom - grayBarTop - mc.font.lineHeight) / 2;
        graphics.drawString(mc.font, title, primaryWeaponX, titleY, 0xFFFFFF, true);

        graphics.fillGradient(dividerX, weaponDisplayY + unitLength,
                dividerX + 1, panelBottom - unitLength,
                0x40808080, 0x20404040);
        graphics.fillGradient(dividerSecTac, weaponDisplayY + unitLength,
                dividerSecTac + 1, panelBottom - unitLength,
                0x40808080, 0x20404040);
        graphics.fillGradient(dividerTacLeth, weaponDisplayY + unitLength,
                dividerTacLeth + 1, panelBottom - unitLength,
                0x40808080, 0x20404040);

        WeaponFilterConfig filterConfig = WeaponFilterClientCache.get();
        boolean throwablesEnabled = filterConfig == null || filterConfig.isThrowablesEnabled();

        for (Map.Entry<String, BackPackSelectButton.WeaponInfo> entry : weaponInfo.entrySet()) {
            String type = entry.getKey();
            BackPackSelectButton.WeaponInfo info = entry.getValue();

            int weaponX = switch (type) {
                case "primary" -> primaryWeaponX;
                case "secondary" -> secondaryWeaponX;
                case "tactical" -> tacticalWeaponX;
                case "lethal" -> lethalWeaponX;
                default -> primaryWeaponX;
            };
            int weaponY = weaponDisplayY + unitLength + 2;

            boolean isThrowableSlot = "tactical".equals(type) || "lethal".equals(type);
            boolean drawThrowableDimmed = isThrowableSlot && !throwablesEnabled;

            String typeLabel = switch (type) {
                case "primary" -> "§c主武器";
                case "secondary" -> "§9副武器";
                case "tactical" -> "§b投掷物 1";
                case "lethal" -> "§6投掷物 2";
                default -> type;
            };
            graphics.drawString(mc.font, typeLabel, weaponX, weaponY, 0xFFFFFF, true);

            if (drawThrowableDimmed) {
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1f, 1f, 1f, 0.22f);
            }
            float scale = isThrowableSlot ? 2f : 3f;
            int slotContentHeight = isThrowableSlot ? unitLength * 5 : unitLength * 7;
            int nameY = weaponY + mc.font.lineHeight + (isThrowableSlot ? unitLength * 7 : slotContentHeight) + 2;

            if (info.texture != null && !isThrowableSlot) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                int textureWidth = unitLength * 18;
                int textureHeight = unitLength * 6;
                int textureX = weaponX;
                int textureY = weaponY + mc.font.lineHeight + 2;
                graphics.blit(info.texture, textureX, textureY,
                        0, 0, textureWidth, textureHeight,
                        textureWidth, textureHeight);
            } else if (info.itemStack != null && !info.itemStack.isEmpty()) {
                int itemSize = (int) (16 * scale);
                int maxSlotWidth = isThrowableSlot ? (throwableColumnWidth - panelPadding * 2) : (unitLength * 18);
                int itemX = weaponX + Math.max(0, (maxSlotWidth - itemSize) / 2);
                int itemY;
                if (isThrowableSlot) {
                    int contentTop = weaponY + mc.font.lineHeight + 2;
                    int contentBottom = nameY - 2;
                    int contentHeight = Math.max(1, contentBottom - contentTop);
                    itemY = contentTop + (contentHeight - itemSize) / 2;
                } else {
                    itemY = weaponY + mc.font.lineHeight + 2;
                }

                graphics.pose().pushPose();
                graphics.pose().translate(itemX, itemY, 0);
                graphics.pose().scale(scale, scale, 1);
                graphics.renderItem(info.itemStack, 0, 0);
                graphics.pose().popPose();
            }

            if (info.weaponName != null) {
                graphics.drawString(mc.font, info.weaponName, weaponX, nameY, 0xFFFFFFFF, false);
            }
            if (info.packName != null && !isThrowableSlot) {
                int packY = weaponY + mc.font.lineHeight * 2 + unitLength * 7 + 6;
                graphics.drawString(mc.font, info.packName, weaponX, packY, 0xAAFFFFFF, false);
            }

            if (drawThrowableDimmed) {
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                RenderSystem.disableBlend();
            }
        }
    }
}
