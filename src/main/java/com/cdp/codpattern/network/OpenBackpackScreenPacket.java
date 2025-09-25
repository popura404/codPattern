package com.cdp.codpattern.network;

import com.cdp.codpattern.network.handler.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenBackpackScreenPacket {

    public OpenBackpackScreenPacket() {}

    public static void encode(OpenBackpackScreenPacket packet, FriendlyByteBuf buffer) {
        // 无需编码数据
    }

    public static OpenBackpackScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenBackpackScreenPacket();
    }

    public static void handle(OpenBackpackScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 使用 DistExecutor 确保只在客户端执行
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientPacketHandler::handleOpenBackpackScreen);
        });
        ctx.get().setPacketHandled(true);
    }
}
