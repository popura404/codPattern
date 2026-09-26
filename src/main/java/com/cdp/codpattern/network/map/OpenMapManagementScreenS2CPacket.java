package com.cdp.codpattern.network.map;

import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sent only after server-side administrator permission checks. */
public record OpenMapManagementScreenS2CPacket() {
    public void encode(FriendlyByteBuf buffer) { }

    public static OpenMapManagementScreenS2CPacket decode(FriendlyByteBuf buffer) {
        return new OpenMapManagementScreenS2CPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(ClientPacketBridge::openMapManagementScreen);
        context.get().setPacketHandled(true);
    }
}
