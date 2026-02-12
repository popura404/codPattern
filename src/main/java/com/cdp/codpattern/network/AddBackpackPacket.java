package com.cdp.codpattern.network;

import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.nio.file.Path;
import java.util.function.Supplier;

public class AddBackpackPacket {

    public AddBackpackPacket() {}

    public AddBackpackPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public static AddBackpackPacket decode(FriendlyByteBuf buf) {
        return new AddBackpackPacket();
    }

    // AddBackpackPacket.java
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                String uuid = player.getStringUUID();
                Path path = ConfigPath.SERVERBACKPACK.getPath(player.server);
                BackpackConfigRepository.loadOrCreate(path);

                // 添加新背包
                int newId = BackpackConfigRepository.addCustomBackpack(uuid);

                if (newId != -1) {
                    player.sendSystemMessage(Component.literal("§a成功添加新背包 #" + newId));

                    BackpackConfig.PlayerBackpackData playerData = BackpackConfigRepository.loadOrCreatePlayer(uuid, path);
                    ModNetworkChannel.sendToPlayer(new SyncBackpackConfigPacket(playerData) , player);
                } else {
                    player.sendSystemMessage(Component.literal("§c无法添加新背包，已达到上限或发生错误"));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

}
