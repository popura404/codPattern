package com.cdp.codpattern.network;

import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.network.NetworkEvent;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;

import java.nio.file.Path;
import java.util.function.Supplier;

public class RequestBackpackConfigPacket {

    public RequestBackpackConfigPacket() {}

    public RequestBackpackConfigPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public static RequestBackpackConfigPacket decode(FriendlyByteBuf buf) {
        return new RequestBackpackConfigPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Path path = ConfigPath.SERVERBACKPACK.getPath(player.server);
                String uuid = player.getUUID().toString();
                BackpackConfig.PlayerBackpackData playerBackpackData = BackpackConfigRepository.loadOrCreatePlayer(uuid,path);

                // 同步更新后的配置到客户端
                ModNetworkChannel.sendToPlayer(new SyncBackpackConfigPacket(playerBackpackData), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
