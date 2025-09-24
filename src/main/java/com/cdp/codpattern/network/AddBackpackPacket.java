package com.cdp.codpattern.network;

import com.cdp.codpattern.config.configmanager.BackpackConfigManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AddBackpackPacket {

    public AddBackpackPacket() {}

    public AddBackpackPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public static AddBackpackPacket decode(FriendlyByteBuf buf) {
        return new AddBackpackPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                String uuid = player.getUUID().toString();

                // 添加新背包
                int newId = BackpackConfigManager.addCustomBackpack(uuid);

                if (newId != -1) {
                    player.sendSystemMessage(Component.literal(
                            "§a成功添加新背包 #" + newId
                    ));

                    // 如果需要，可以在这里同步配置到客户端
                    // 对于单人游戏，这不是必需的
                } else {
                    player.sendSystemMessage(Component.literal(
                            "§c无法添加新背包，已达到上限或发生错误"
                    ));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
