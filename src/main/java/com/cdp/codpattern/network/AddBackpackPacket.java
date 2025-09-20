package com.cdp.codpattern.network;

import com.cdp.codpattern.config.configmanager.BackpackConfigManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AddBackpackPacket {

    public AddBackpackPacket() {
        // 空构造函数
    }

    public static void encode(AddBackpackPacket packet, FriendlyByteBuf buffer) {
        // 无需编码任何数据
    }

    public static AddBackpackPacket decode(FriendlyByteBuf buffer) {
        return new AddBackpackPacket();
    }

    public static void handle(AddBackpackPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                String uuid = player.getUUID().toString();

                // 调用BackpackConfigManager的addCustomBackpack方法
                int newBackpackId = BackpackConfigManager.addCustomBackpack(uuid);

                if (newBackpackId > 0) {
                    // 成功添加背包
                    player.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.literal("§a✔ 成功添加新背包 #" + newBackpackId)
                                    .withStyle(style -> style
                                            .withColor(0x7FFF00)
                                            .withBold(true))
                    ));
                } else {
                    // 添加失败（达到上限）
                    player.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.literal("§c✘ 无法添加更多背包（已达上限）")
                                    .withStyle(style -> style
                                            .withColor(0xFF5555)
                                            .withBold(true))
                    ));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
