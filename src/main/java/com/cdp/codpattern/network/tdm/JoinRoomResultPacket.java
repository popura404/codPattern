package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.client.gui.screen.TdmRoomScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: 加入房间结果 ACK
 */
public class JoinRoomResultPacket {
    private final boolean success;
    private final String mapName;
    private final String reasonCode;
    private final String reasonMessage;

    public JoinRoomResultPacket(boolean success, String mapName, String reasonCode, String reasonMessage) {
        this.success = success;
        this.mapName = mapName == null ? "" : mapName;
        this.reasonCode = reasonCode == null ? "" : reasonCode;
        this.reasonMessage = reasonMessage == null ? "" : reasonMessage;
    }

    public JoinRoomResultPacket(FriendlyByteBuf buf) {
        this.success = buf.readBoolean();
        this.mapName = buf.readUtf();
        this.reasonCode = buf.readUtf();
        this.reasonMessage = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeUtf(mapName);
        buf.writeUtf(reasonCode);
        buf.writeUtf(reasonMessage);
    }

    public static JoinRoomResultPacket decode(FriendlyByteBuf buf) {
        return new JoinRoomResultPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> Minecraft.getInstance().execute(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof TdmRoomScreen tdmRoomScreen) {
                tdmRoomScreen.handleJoinResult(success, mapName, reasonCode, reasonMessage);
            }
        }));
        ctx.get().setPacketHandled(true);
    }
}
