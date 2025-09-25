package com.cdp.codpattern.network;

import com.cdp.codpattern.config.server.WeaponFilterConfig;
import com.google.gson.Gson;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncWeaponFilterPacket {
    private static final Gson GSON = new Gson();
    private final String configJson;

    public SyncWeaponFilterPacket(WeaponFilterConfig config) {
        this.configJson = GSON.toJson(config);
    }

    public SyncWeaponFilterPacket(FriendlyByteBuf buf) {
        this.configJson = buf.readUtf(32767);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(configJson);
    }

    public static SyncWeaponFilterPacket decode(FriendlyByteBuf buf) {
        return new SyncWeaponFilterPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 客户端接收并缓存配置
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                WeaponFilterConfig config = GSON.fromJson(configJson, WeaponFilterConfig.class);
                WeaponFilterConfig.setClientInstance(config);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
