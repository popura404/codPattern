package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.client.gui.screen.TdmRoomScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: 离开房间结果 ACK
 */
public class LeaveRoomResultPacket {
    private final boolean success;
    private final String roomName;
    private final String reasonCode;
    private final String reasonMessage;

    public LeaveRoomResultPacket(boolean success, String roomName, String reasonCode, String reasonMessage) {
        this.success = success;
        this.roomName = roomName == null ? "" : roomName;
        this.reasonCode = reasonCode == null ? "" : reasonCode;
        this.reasonMessage = reasonMessage == null ? "" : reasonMessage;
    }

    public LeaveRoomResultPacket(FriendlyByteBuf buf) {
        this.success = buf.readBoolean();
        this.roomName = buf.readUtf();
        this.reasonCode = buf.readUtf();
        this.reasonMessage = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeUtf(roomName);
        buf.writeUtf(reasonCode);
        buf.writeUtf(reasonMessage);
    }

    public static LeaveRoomResultPacket decode(FriendlyByteBuf buf) {
        return new LeaveRoomResultPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> Minecraft.getInstance().execute(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof TdmRoomScreen tdmRoomScreen) {
                tdmRoomScreen.handleLeaveResult(success, roomName, reasonCode, reasonMessage);
            }
            if (!success && Minecraft.getInstance().player != null) {
                String message = reasonMessage.isBlank() ? "离开房间失败: " + reasonCode : reasonMessage;
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c" + message));
            }
        }));
        ctx.get().setPacketHandled(true);
    }
}
