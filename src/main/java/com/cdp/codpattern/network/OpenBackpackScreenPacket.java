package com.cdp.codpattern.network;

import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenBackpackScreenPacket {

    public OpenBackpackScreenPacket() {}

    public static void encode(OpenBackpackScreenPacket packet, FriendlyByteBuf buffer) {}

    public static OpenBackpackScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenBackpackScreenPacket();
    }

    public static void handle(OpenBackpackScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketBridge.openBackpackScreen();
        });
        ctx.get().setPacketHandled(true);
    }
}
