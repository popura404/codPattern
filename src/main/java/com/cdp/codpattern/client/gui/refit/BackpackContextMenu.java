package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.client.gui.CodTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * COD MWII 风格的右键上下文菜单
 * 用于背包卡片的 重命名/复制/删除 操作
 */
public class BackpackContextMenu implements Renderable, GuiEventListener, NarratableEntry {

    private int x;
    private int y;
    private int width;
    private int height;
    private boolean visible = false;
    private final List<MenuItem> items = new ArrayList<>();
    private int hoveredIndex = -1;
    private int lastHoveredIndex = -1;
    private Consumer<BackpackContextMenu> onClose;

    public static class MenuItem {
        public final String label;
        public final Runnable action;
        public final int textColor;
        public final int hoverTextColor;

        public MenuItem(String label, Runnable action) {
            this(label, action, CodTheme.TEXT_PRIMARY, CodTheme.TEXT_HOVER);
        }

        public MenuItem(String label, Runnable action, int textColor, int hoverTextColor) {
            this.label = label;
            this.action = action;
            this.textColor = textColor;
            this.hoverTextColor = hoverTextColor;
        }
    }

    public BackpackContextMenu() {
        this.width = CodTheme.MENU_WIDTH;
    }

    /**
     * 添加菜单项
     */
    public void addItem(String label, Runnable action) {
        items.add(new MenuItem(label, action));
        recalculateHeight();
    }

    /**
     * 添加带自定义颜色的菜单项
     */
    public void addItem(String label, Runnable action, int textColor, int hoverTextColor) {
        items.add(new MenuItem(label, action, textColor, hoverTextColor));
        recalculateHeight();
    }

    /**
     * 清空所有菜单项
     */
    public void clearItems() {
        items.clear();
        recalculateHeight();
    }

    private void recalculateHeight() {
        this.height = items.size() * CodTheme.MENU_ITEM_HEIGHT + 4;
    }

    /**
     * 显示菜单在指定位置
     */
    public void show(int x, int y, int screenWidth, int screenHeight) {
        // 确保菜单不超出屏幕边界
        if (x + width > screenWidth) {
            x = screenWidth - width - 5;
        }
        if (y + height > screenHeight) {
            y = screenHeight - height - 5;
        }
        if (x < 5) x = 5;
        if (y < 5) y = 5;

        this.x = x;
        this.y = y;
        this.visible = true;
        this.hoveredIndex = -1;
        this.lastHoveredIndex = -1;
    }

    /**
     * 隐藏菜单
     */
    public void hide() {
        this.visible = false;
        this.hoveredIndex = -1;
        this.lastHoveredIndex = -1;
        if (onClose != null) {
            onClose.accept(this);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void setOnClose(Consumer<BackpackContextMenu> onClose) {
        this.onClose = onClose;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible || items.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();

        // 更新悬停状态
        updateHoveredIndex(mouseX, mouseY);

        // 绘制菜单背景
        graphics.fill(x, y, x + width, y + height, CodTheme.MENU_BG);

        // 绘制边框
        graphics.fill(x, y, x + width, y + 1, CodTheme.MENU_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, CodTheme.MENU_BORDER);
        graphics.fill(x, y, x + 1, y + height, CodTheme.MENU_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, CodTheme.MENU_BORDER);

        // 绘制菜单项
        int itemY = y + 2;
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            boolean isHovered = (i == hoveredIndex);

            // 悬停背景
            if (isHovered) {
                graphics.fill(x + 2, itemY, x + width - 2, itemY + CodTheme.MENU_ITEM_HEIGHT, CodTheme.MENU_ITEM_HOVER);
                // 左侧高亮条
                graphics.fill(x + 2, itemY, x + 4, itemY + CodTheme.MENU_ITEM_HEIGHT, CodTheme.MENU_ITEM_HOVER_BAR);
            }

            // 绘制分隔线（除了最后一项）
            if (i < items.size() - 1) {
                graphics.fill(x + 8, itemY + CodTheme.MENU_ITEM_HEIGHT - 1,
                        x + width - 8, itemY + CodTheme.MENU_ITEM_HEIGHT,
                        CodTheme.DIVIDER);
            }

            // 绘制文本
            int textColor = isHovered ? item.hoverTextColor : item.textColor;
            int textX = x + 10;
            int textY = itemY + (CodTheme.MENU_ITEM_HEIGHT - minecraft.font.lineHeight) / 2;
            graphics.drawString(minecraft.font, item.label, textX, textY, textColor, false);

            itemY += CodTheme.MENU_ITEM_HEIGHT;
        }
    }

    private void updateHoveredIndex(int mouseX, int mouseY) {
        if (!visible) {
            hoveredIndex = -1;
            return;
        }

        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            hoveredIndex = -1;
            lastHoveredIndex = -1;
            return;
        }

        int relativeY = mouseY - y - 2;
        int index = relativeY / CodTheme.MENU_ITEM_HEIGHT;
        if (index >= 0 && index < items.size()) {
            if (hoveredIndex != index) {
                // 切换到新的菜单项时播放音效
                if (hoveredIndex != -1) {
                    playHoverSound();
                }
                hoveredIndex = index;
            }
            lastHoveredIndex = hoveredIndex;
        } else {
            hoveredIndex = -1;
        }
    }

    private void playHoverSound() {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playNotifySound(
                        SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                        SoundSource.PLAYERS, 0.3f, 1.5f);
            }
        });
    }

    private void playClickSound() {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playNotifySound(
                        SoundEvents.AMETHYST_BLOCK_PLACE,
                        SoundSource.PLAYERS, 0.5f, 1.2f);
            }
        });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        // 检查是否点击在菜单内
        boolean isInsideMenu = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;

        if (!isInsideMenu) {
            // 点击在菜单外部，关闭菜单
            hide();
            return true; // 消费事件，防止穿透
        }

        // 左键点击菜单项
        if (button == 0 && hoveredIndex >= 0 && hoveredIndex < items.size()) {
            playClickSound();
            MenuItem item = items.get(hoveredIndex);
            hide();
            if (item.action != null) {
                item.action.run();
            }
            return true;
        }

        // 右键点击菜单内也消费事件
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public void setFocused(boolean focused) {
        // 不需要实现
    }

    @Override
    public boolean isFocused() {
        return visible;
    }

    @Override
    public @NotNull NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(@NotNull NarrationElementOutput output) {
        // 无障碍功能暂不实现
    }

    // Getters
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
