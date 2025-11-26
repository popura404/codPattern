package com.cdp.codpattern.network;

import com.cdp.codpattern.config.BackPackConfig.BackpackConfig;
import com.cdp.codpattern.core.ConfigPath.ConfigPath;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.network.NetworkEvent;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfigManager;
import com.cdp.codpattern.network.handler.PacketHandler;

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
                BackpackConfig.PlayerBackpackData playerBackpackData = BackpackConfigManager.LoadorCreatePlayer(uuid,path);

                // 同步更新后的配置到客户端
                PacketHandler.sendToPlayer(new SyncBackpackConfigPacket(playerBackpackData), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
