package com.cdp.codpattern.network;

import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.network.handler.ClientPacketBridge;
import com.google.gson.Gson;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;
//s2c
public class SyncBackpackConfigPacket {

    private final String configJson;
    private static final Gson GSON = new Gson();

    public SyncBackpackConfigPacket(BackpackConfig.PlayerBackpackData playerData) {
        this.configJson = GSON.toJson(playerData);
    }

    public SyncBackpackConfigPacket(FriendlyByteBuf buf) {
        this.configJson = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(configJson);
    }

    public static SyncBackpackConfigPacket decode(FriendlyByteBuf buf) {
        return new SyncBackpackConfigPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketBridge.syncBackpackConfig(configJson);
        });
        ctx.get().setPacketHandled(true);
    }
}
