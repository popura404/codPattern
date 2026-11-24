package com.cdp.codpattern.compat.tacz.client;

import com.cdp.codpattern.client.gui.screen.backpack.BackpackUi;
import com.cdp.codpattern.client.gui.screen.backpack.WeaponCatalog;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.GunAttachmentSlot;
import com.tacz.guns.client.gui.components.refit.InventoryAttachmentSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

final class RefitSlotPainter {
    private static final int SIDE_INSET = 7;
    private static final int ICON_GAP = 6;
    private static final int MUTED_INK = 0xFF879793;

    private RefitSlotPainter() {}

    static Component categoryName(AttachmentType type) {
        return Component.translatable("tooltip.tacz.attachment." + type.name().toLowerCase(Locale.ROOT));
    }

    static void category(GuiGraphics g, GunAttachmentSlot slot, ItemStack gun, boolean highlight) {
        AttachmentType type = slot.getType();
        boolean available = slot.active && slot.isAllow();
        boolean selected = available && type == RefitTransform.getCurrentTransformType();
        paint(g, slot, slot.getAttachmentItem(), gun, type, available, selected, highlight);
    }

    static void candidate(GuiGraphics g, InventoryAttachmentSlot slot, ItemStack stack, boolean selected, boolean highlight) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        boolean available = slot.active && attachment != null;
        paint(g, slot, stack, ItemStack.EMPTY, null, available, selected, highlight);
    }

    static void noAttachment(GuiGraphics g, Button row, boolean selected, boolean highlight) {
        paint(g, row, ItemStack.EMPTY, ItemStack.EMPTY, null, true, selected, highlight);
    }

    private static void paint(GuiGraphics g, Button slot, ItemStack icon, ItemStack gun, AttachmentType type,
                              boolean available, boolean selected, boolean highlight) {
        var column = WeaponCatalog.NativeCardColumn.current();
        int x = column.canvas().left();
        int width = column.canvas().width();
        int height = Math.max(1, Math.round(slot.getHeight() / column.scale()));
        var pose = g.pose();
        pose.pushPose();
        try {
            pose.translate(0, slot.getY(), 0);
            pose.last().pose().scale(column.scale(), slot.getHeight() / (float) height, 1);
            paintFace(g, slot, x, width, height, available, selected, highlight);
            int size = Math.min(Math.max(1, height - 2), Mth.clamp(Math.round(height * .6f), 12, 26));
            int iconX = x + SIDE_INSET, iconY = (height - size) / 2;
            if (type != null) g.fill(iconX - 1, iconY - 1, iconX + size + 1, iconY + size + 1, 0xFF223438);
            if (!icon.isEmpty()) drawItem(g, icon, iconX, iconY, size);
            else if (type != null) drawCategoryIcon(g, gun, type, iconX, iconY, size, available);
            else {
                int inset = size / 4;
                g.fill(iconX + inset, height / 2, iconX + size - inset, height / 2 + 1,
                        selected ? BackpackUi.PRIMARY_INK : BackpackUi.MUTED);
            }

            int nameX = iconX + size + ICON_GAP;
            int nameWidth = x + width - SIDE_INSET - nameX;
            Component name = slot.getMessage();
            float ideal = Mth.clamp(height / 27f, 1f, 1.28f);
            float nameScale = Mth.clamp(nameWidth / (float) Math.max(1, BackpackUi.textWidth(name, 1f)), .82f, ideal);
            float scale = Math.min(nameScale, Math.max(1, height - 2) / (float) BackpackUi.lineHeight(1f));
            int color = selected ? BackpackUi.PRIMARY_INK : available ? BackpackUi.INK : MUTED_INK;
            BackpackUi.text(g, name, nameX, (height - BackpackUi.lineHeight(scale)) / 2, nameWidth, scale, color);
        } finally {
            pose.popPose();
        }
    }

    private static void paintFace(GuiGraphics g, Button slot, int x, int width, int height,
                                  boolean available, boolean selected, boolean highlight) {
        int y = 0;
        if (selected) {
            BackpackUi.horizontalGradient(g, x, y, width, height, 0xFFA5D8CF, 0xFF82BFB8);
            int inset = Math.min(4, height / 3);
            g.fill(x, y + inset, x + 2, y + height - inset, 0xFF325A56);
        } else if (available) {
            BackpackUi.horizontalGradient(g, x, y, width, height, 0xFF243236, 0xFF2D4043);
            g.fill(x, y + height - 1, x + width, y + height, 0xFF52686A);
        } else {
            BackpackUi.horizontalGradient(g, x, y, width, height, 0xFF192326, 0xFF232F31);
            g.fill(x, y + height - 1, x + width, y + height, 0xFF3B494A);
        }

        if (slot.isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard()) {
            g.renderOutline(x + 1, y + 1, width - 2, height - 2, selected ? 0xFF355E59 : 0xFFB7E5DE);
        } else if (highlight && available) {
            g.renderOutline(x, y, width, height, selected ? 0xFFE0FAF3 : 0xFF8FB6B3);
        }
    }

    private static void drawCategoryIcon(GuiGraphics g, ItemStack gun, AttachmentType type, int x, int y, int size, boolean available) {
        int sourceX = GunRefitScreen.getSlotTextureXOffset(gun, type);
        if (sourceX < 0) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, available ? 1f : .58f);
        try {
            int sourceSize = GunRefitScreen.ICON_UV_SIZE;
            g.blit(GunRefitScreen.ICONS_TEXTURE, x, y, size, size,
                    (float) sourceX, 0f, sourceSize, sourceSize, GunRefitScreen.getSlotsTextureWidth(), sourceSize);
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }
    }

    private static void drawItem(GuiGraphics g, ItemStack item, int x, int y, int size) {
        var pose = g.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            pose.scale(size / 16f, size / 16f, 1f);
            g.renderItem(item, 0, 0);
        } finally {
            pose.popPose();
        }
    }
}
