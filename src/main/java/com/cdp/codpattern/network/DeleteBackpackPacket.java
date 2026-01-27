package com.cdp.codpattern.network;

import com.cdp.codpattern.config.BackPackConfig.BackpackConfig;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfigManager;
import com.cdp.codpattern.core.ConfigPath.ConfigPath;
import com.cdp.codpattern.network.handler.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Supplier;

public class DeleteBackpackPacket {

    private final int backpackId;

    public DeleteBackpackPacket(int backpackId) {
        this.backpackId = backpackId;
    }

    public DeleteBackpackPacket(FriendlyByteBuf buf) {
        this.backpackId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(backpackId);
    }

    public static DeleteBackpackPacket decode(FriendlyByteBuf buf) {
        return new DeleteBackpackPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            Path path = ConfigPath.SERVERBACKPACK.getPath(player.server);
            String uuid = player.getStringUUID();
            BackpackConfig.PlayerBackpackData playerData = BackpackConfigManager.LoadorCreatePlayer(uuid, path);

            if (!playerData.getBackpacks_MAP().containsKey(backpackId)) {
                player.sendSystemMessage(Component.literal("§c背包不存在: #" + backpackId));
                return;
            }
            if (playerData.getBackpacks_MAP().size() <= 1) {
                player.sendSystemMessage(Component.literal("§c至少保留一个背包"));
                return;
            }

            playerData.getBackpacks_MAP().remove(backpackId);

            if (playerData.getSelectedBackpack() == backpackId) {
                int nextId = playerData.getBackpacks_MAP().keySet()
                        .stream()
                        .min(Comparator.naturalOrder())
                        .orElse(1);
                playerData.setSelectedBackpack(nextId);
            }

            BackpackConfigManager.save();
            PacketHandler.sendToPlayer(new SyncBackpackConfigPacket(playerData), player);
            player.sendSystemMessage(Component.literal("§a已删除背包 #" + backpackId));
        });
        ctx.get().setPacketHandled(true);
    }
}
