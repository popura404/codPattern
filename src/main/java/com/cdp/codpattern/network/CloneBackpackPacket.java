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
import java.util.Map;
import java.util.function.Supplier;

public class CloneBackpackPacket {

    private final int sourceId;

    public CloneBackpackPacket(int sourceId) {
        this.sourceId = sourceId;
    }

    public CloneBackpackPacket(FriendlyByteBuf buf) {
        this.sourceId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(sourceId);
    }

    public static CloneBackpackPacket decode(FriendlyByteBuf buf) {
        return new CloneBackpackPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            Path path = ConfigPath.SERVERBACKPACK.getPath(player.server);
            String uuid = player.getStringUUID();
            BackpackConfig.PlayerBackpackData playerData = BackpackConfigRepository.loadOrCreatePlayer(uuid, path);

            BackpackConfig.Backpack source = playerData.getBackpacks_MAP().get(sourceId);
            if (source == null) {
                player.sendSystemMessage(Component.literal("§c背包不存在: #" + sourceId));
                return;
            }

            int newId = playerData.getNextAvailableId();
            if (newId == 0) {
                player.sendSystemMessage(Component.literal("§c无法复制背包，已达到上限"));
                return;
            }

            String baseName = source.getName() == null ? "背包" : source.getName().trim();
            String copyName = baseName.isEmpty() ? "背包 副本" : baseName + " 副本";
            if (copyName.length() > 32) {
                copyName = copyName.substring(0, 32);
            }
            BackpackConfig.Backpack clone = new BackpackConfig.Backpack(copyName);

            for (Map.Entry<String, BackpackConfig.Backpack.ItemData> entry : source.getItem_MAP().entrySet()) {
                BackpackConfig.Backpack.ItemData item = entry.getValue();
                if (item != null) {
                    clone.setItem_MAP(entry.getKey(),
                            new BackpackConfig.Backpack.ItemData(item.getItem(), item.getCount(), item.getNbt()));
                }
            }

            if (playerData.addBackpack(newId, clone)) {
                BackpackConfigRepository.save();
                ModNetworkChannel.sendToPlayer(new SyncBackpackConfigPacket(playerData), player);
                player.sendSystemMessage(Component.literal("§a已复制背包 #" + sourceId + " -> #" + newId));
            } else {
                player.sendSystemMessage(Component.literal("§c复制失败"));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
