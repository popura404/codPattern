package com.cdp.codpattern.client.gui.screen;

import com.cdp.codpattern.network.RenameBackpackPacket;
import com.cdp.codpattern.network.handler.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class RenameBackpackScreen extends Screen {

    private final BackpackMenuScreen parent;
    private final int backpackId;
    private final String currentName;
    private EditBox nameBox;
    private Button confirmButton;

    public RenameBackpackScreen(BackpackMenuScreen parent, int backpackId, String currentName) {
        super(Component.literal("Rename Backpack"));
        this.parent = parent;
        this.backpackId = backpackId;
        this.currentName = currentName == null ? "" : currentName;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int boxWidth = 200;
        int boxHeight = 20;

        nameBox = new EditBox(this.font, centerX - boxWidth / 2, centerY - 20, boxWidth, boxHeight, Component.literal("背包名称"));
        nameBox.setMaxLength(32);
        nameBox.setValue(currentName);
        nameBox.setFocused(true);
        nameBox.setResponder(value -> confirmButton.active = !value.trim().isEmpty());
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        confirmButton = Button.builder(Component.literal("确定"), btn -> submit())
                .bounds(centerX - boxWidth / 2, centerY + 10, 98, 20)
                .build();
        confirmButton.active = !currentName.trim().isEmpty();

        Button cancelButton = Button.builder(Component.literal("取消"), btn -> onClose())
                .bounds(centerX - boxWidth / 2 + 102, centerY + 10, 98, 20)
                .build();

        addRenderableWidget(confirmButton);
        addRenderableWidget(cancelButton);
    }

    @Override
    public void tick() {
        if (nameBox != null) {
            nameBox.tick();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, Component.literal("重命名背包 #" + backpackId), this.width / 2, this.height / 2 - 50, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("请输入新的背包名称"), this.width / 2, this.height / 2 - 35, 0xAAAAAA);
    }

    private void submit() {
        String newName = nameBox.getValue().trim();
        if (newName.isEmpty()) {
            return;
        }
        PacketHandler.sendToServer(new RenameBackpackPacket(backpackId, newName));
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
