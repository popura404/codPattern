package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: Generic popup notice packet.
 */
public class PopupNoticePacket {
    private final Component title;
    private final Component message;

    public PopupNoticePacket(Component title, Component message) {
        this.title = title == null ? Component.translatable("screen.codpattern.popup.title") : title;
        this.message = message == null ? Component.empty() : message;
    }

    public PopupNoticePacket(FriendlyByteBuf buf) {
        this.title = buf.readComponent();
        this.message = buf.readComponent();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeComponent(title);
        buf.writeComponent(message);
    }

    public static PopupNoticePacket decode(FriendlyByteBuf buf) {
        return new PopupNoticePacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPacketBridge.popupNotice(title, message));
        ctx.get().setPacketHandled(true);
    }
}
