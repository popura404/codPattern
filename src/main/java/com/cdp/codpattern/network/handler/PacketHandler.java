package com.cdp.codpattern.network.handler;

import com.cdp.codpattern.network.AddBackpackPacket;
import com.cdp.codpattern.network.SelectBackpackPacket;
import com.cdp.codpattern.network.UpdateWeaponPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("codpattern", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static int id() {
        return packetId++;
    }

    /**
     * 注册所有网络包
     */
    public static void register() {
        // 注册选择背包的数据包
        INSTANCE.messageBuilder(SelectBackpackPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SelectBackpackPacket::decode)
                .encoder(SelectBackpackPacket::encode)
                .consumerMainThread(SelectBackpackPacket::handle)
                .add();

        // 注册添加背包的数据包
        INSTANCE.messageBuilder(AddBackpackPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(AddBackpackPacket::decode)
                .encoder(AddBackpackPacket::encode)
                .consumerMainThread(AddBackpackPacket::handle)
                .add();

        // 注册更新武器的数据包
        INSTANCE.messageBuilder(UpdateWeaponPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(UpdateWeaponPacket::decode)
                .encoder(UpdateWeaponPacket::encode)
                .consumerMainThread(UpdateWeaponPacket::handle)
                .add();
    }

    /**
     * 发送数据包到服务器
     */
    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    /**
     * 发送数据包到玩家
     */
    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}

