package com.cdp.codpattern.network;

import com.cdp.codpattern.config.ConfigFailureFeedback;

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

    public AddBackpackPacket() {
    }

    public AddBackpackPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static AddBackpackPacket decode(FriendlyByteBuf buf) {
        return new AddBackpackPacket();
    }

    // AddBackpackPacket.java
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ConfigFailureFeedback.run(player, () -> {
                    String uuid = player.getStringUUID();
                    Path path = ConfigPath.SERVERBACKPACK.getPath(player.server);
                    BackpackConfigRepository.loadOrCreate(path);

                    // 添加新背包
                    int newId = BackpackConfigRepository.addCustomBackpack(uuid);

                    if (newId != -1) {
                        BackpackConfig.PlayerBackpackData playerData = BackpackConfigRepository.getOrCreatePlayerData(uuid);
                        ModNetworkChannel.sendToPlayer(new SyncBackpackConfigPacket(playerData), player);
                        player.sendSystemMessage(Component.translatable("message.codpattern.backpack.added", newId));
                    } else {
                        player.sendSystemMessage(Component.translatable("message.codpattern.backpack.add_failed"));
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }

}
