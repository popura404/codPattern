package com.cdp.codpattern.network;

import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackNameHelper;
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
                player.sendSystemMessage(Component.translatable("message.codpattern.backpack.not_found", sourceId));
                return;
            }

            int newId = playerData.getNextAvailableId();
            if (newId == 0) {
                player.sendSystemMessage(Component.translatable("message.codpattern.backpack.clone_limit"));
                return;
            }

            String copyName = "";
            if (!BackpackNameHelper.isGeneratedName(source.getName())) {
                String baseName = source.getName().trim();
                String copySuffix = Component.translatable("message.codpattern.backpack.copy_suffix").getString();
                copyName = baseName + copySuffix;
                if (copyName.length() > 32) {
                    copyName = copyName.substring(0, 32);
                }
            }
            BackpackConfig.Backpack clone = new BackpackConfig.Backpack(copyName);

            for (Map.Entry<String, BackpackConfig.Backpack.ItemData> entry : source.getItem_MAP().entrySet()) {
                BackpackConfig.Backpack.ItemData item = entry.getValue();
                if (item != null) {
                    clone.setItem_MAP(entry.getKey(),
                            new BackpackConfig.Backpack.ItemData(
                                    item.getItem(),
                                    item.getCount(),
                                    item.getNbt(),
                                    item.getAttachmentPreset()));
                }
            }

            if (playerData.addBackpack(newId, clone)) {
                BackpackConfigRepository.save();
                ModNetworkChannel.sendToPlayer(new SyncBackpackConfigPacket(playerData), player);
                player.sendSystemMessage(Component.translatable("message.codpattern.backpack.cloned", sourceId, newId));
            } else {
                player.sendSystemMessage(Component.translatable("message.codpattern.backpack.clone_failed"));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
