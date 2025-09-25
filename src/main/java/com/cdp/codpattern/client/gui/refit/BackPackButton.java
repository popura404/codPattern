package com.cdp.codpattern.client.gui.refit;

import com.cdp.codpattern.config.server.BackpackSelectionConfig;
import com.cdp.codpattern.network.handler.PacketHandler;
import com.cdp.codpattern.network.SelectBackpackPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BackPackButton extends Button {

    private final Integer BAGSERIAL;
    int focusedtimes = 0;
    private final BackpackSelectionConfig.Backpack backpack;
    private final boolean isCurrentlySelected;

    /**
     * 构造函数兼容性
     */
    public BackPackButton(int x, int y, int width, int height, int bagserial) {
        this(x, y, width, height, bagserial, null, false);
    }

    /**
     * 增强构造函数 - 支持背包数据和选中状态
     */
    public BackPackButton(int x, int y, int width, int height, int bagserial, BackpackSelectionConfig.Backpack backpack, boolean isSelected) {
        super(x, y, width, height, Component.literal("choose your bag"), button -> {
            // 发送选择背包的数据包到服务端
            PacketHandler.sendToServer(new SelectBackpackPacket(bagserial));
            // 关闭界面
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(null);
            });
        }, DEFAULT_NARRATION);

        this.BAGSERIAL = bagserial;
        this.backpack = backpack;
        this.isCurrentlySelected = isSelected;

        // 设置悬停提示
        if (backpack != null) {
            this.setTooltip(createBackpackTooltip());
        }
    }

    /**
     * 创建背包物品提示
     */
    private Tooltip createBackpackTooltip() {
        List<Component> tooltipLines = new ArrayList<>();

        // 背包名称和编号
        tooltipLines.add(Component.literal("§e§l" + backpack.getName() + " §7(#" + BAGSERIAL + ")"));
        tooltipLines.add(Component.literal("§7----------------"));

        // 显示背包内的物品
        for (Map.Entry<String, BackpackSelectionConfig.Backpack.ItemData> entry :
                backpack.getItem_MAP().entrySet()) {
            String type = entry.getKey();
            BackpackSelectionConfig.Backpack.ItemData item = entry.getValue();

            String typeLabel = type.equals("primary") ? "§c主武器" : "§9副武器";
            String itemName = item.getItem().replace("minecraft:", "");

            tooltipLines.add(Component.literal(typeLabel + ": §f" + itemName + " §7x" + item.getCount()));
        }

        // 如果是当前选中的背包，添加提示
        if (isCurrentlySelected) {
            tooltipLines.add(Component.literal("§a✔ 当前选中"));
        }

        // 创建多行Tooltip
        Component combined = tooltipLines.get(0);
        for (int i = 1; i < tooltipLines.size(); i++) {
            combined = Component.empty().append(combined).append("\n").append(tooltipLines.get(i));
        }

        return Tooltip.create(combined);
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float pPartialTick) {
        // 悬停音效处理
        if (this.isHoveredOrFocused() && focusedtimes == 0) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.playNotifySound(
                            SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON,
                            SoundSource.PLAYERS, 1f, 1f);
                }
            });
            focusedtimes = 1;
        } else if (!this.isHoveredOrFocused()) {
            focusedtimes = 0;
        }

        // 渲染基础按钮背景
        graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xDA5C565C, 0xED292729);

        // 悬停效果
        if (isHoveredOrFocused()) {
            renderOnHoveredOrFocused(graphics);
        }

        // 如果是选中的背包，渲染高亮边框
        if (isCurrentlySelected) {
            renderSelectedHighlight(graphics);
        }

        // 渲染背包名称编号
        if (backpack != null) {
            renderBackpackInfo(graphics);
        }
    }

    /**
     * 渲染选中状态的高亮边框
     */
    protected void renderSelectedHighlight(GuiGraphics graphics) {
        int borderWidth = 1;
        int color = 0xEEEED700; // 金色
        // 上
        graphics.fill(this.getX() - borderWidth, this.getY() - borderWidth, this.getX() + this.width + borderWidth, this.getY(), color);
        // 下
        graphics.fill(this.getX() - borderWidth, this.getY() + this.height, this.getX() + this.width + borderWidth, this.getY() + this.height + borderWidth, color);
        // 左
        graphics.fill(this.getX() - borderWidth, this.getY(), this.getX(), this.getY() + this.height, color);
        // 右
        graphics.fill(this.getX() + this.width, this.getY(), this.getX() + this.width + borderWidth, this.getY() + this.height, color);
    }

    /**
     * 渲染背包信息
     */
    protected void renderBackpackInfo(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        // 背包编号（左上角）
        String idText = "#" + BAGSERIAL;
        graphics.drawString(minecraft.font, idText, this.getX() + 4, this.getY() + 4, isCurrentlySelected ? 0xFFD700 : 0xBBBBBB, true);
        // 背包名称（居中）
        String name = backpack.getName();
        int textWidth = minecraft.font.width(name);
        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = this.getY() + (this.height - minecraft.font.lineHeight) / 2;

        graphics.drawString(minecraft.font, name, textX, textY, this.isHoveredOrFocused() ? 0x00FF00 : 0xFFFFFF, true);
    }

    protected void renderOnHoveredOrFocused(GuiGraphics graphics) {
        graphics.fillGradient(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xD0141A14, 0xD02A2F2A);
        graphics.fillGradient(this.getX(), this.getY() - 2, this.getX() + this.width, this.getY(), 0xD0145200, 0xD0145200);
        graphics.fillGradient(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height + this.height/3 + 6, 0xD0145200, 0xD0145200);
    }

    @Override
    public void playDownSound(@NotNull SoundManager pHandler) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playNotifySound(
                        SoundEvents.AMETHYST_BLOCK_PLACE,
                        SoundSource.PLAYERS, 0.5f, 2.2f);
            }
        });
        super.playDownSound(pHandler);
    }

    public Integer getBAGSERIAL() {
        return BAGSERIAL;
    }

    public BackpackSelectionConfig.Backpack getBackpack() {
        return backpack;
    }
}
